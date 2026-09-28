package com.ratemyuj.auth;

import com.ratemyuj.domain.OtpChallenge;
import com.ratemyuj.exception.ApiException;
import com.ratemyuj.repository.OtpChallengeRepository;
import com.ratemyuj.service.HashService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

@Service
public class OtpService {

    public static final String INVALID_CODE = "Invalid or expired code";
    public static final String RATE_LIMIT = "Too many codes requested. Try again later.";
    public static final String SEND_FAILED = "Could not send the code. Try again.";

    private static final Logger log = LoggerFactory.getLogger(OtpService.class);
    private static final Duration CODE_TTL = Duration.ofMinutes(10);
    private static final Duration CHALLENGE_TTL = Duration.ofMinutes(15);
    private static final int MAX_PER_EMAIL = 3;
    private static final int MAX_PER_IP = 10;
    private static final int MAX_ATTEMPTS = 5;

    private final OtpChallengeRepository challenges;
    private final OtpMailer mailer;
    private final HashService hashes;
    private final EmailPolicy emails;
    private final SecureRandom random = new SecureRandom();
    private final byte[] dummyCodeHash;

    public OtpService(OtpChallengeRepository challenges, OtpMailer mailer,
                      HashService hashes, EmailPolicy emails) {
        this.challenges = challenges;
        this.mailer = mailer;
        this.hashes = hashes;
        this.emails = emails;
        this.dummyCodeHash = HexFormat.of().parseHex(hashes.hmacRaw("dummy-otp-compare"));
    }

    /**
     * Replaces the Mongo TTL index: expired challenges are purged on a timer.
     */
    @Scheduled(fixedDelayString = "PT15M")
    @Transactional
    public void purgeExpiredChallenges() {
        long deleted = challenges.deleteByExpiresAtBefore(Instant.now());
        if (deleted > 0) {
            log.info("Purged {} expired OTP challenges", deleted);
        }
    }

    public void request(String email, String remoteAddr) {
        String normalized = emails.normalize(email);
        String ip = remoteAddr == null || remoteAddr.isBlank() ? "unknown" : remoteAddr;
        Instant since = Instant.now().minus(CHALLENGE_TTL);
        if (challenges.countByEmailAndCreatedAtAfter(normalized, since) >= MAX_PER_EMAIL
                || challenges.countByRequesterIpAndCreatedAtAfter(ip, since) >= MAX_PER_IP) {
            throw ApiException.tooManyRequests(RATE_LIMIT);
        }

        String code = "%06d".formatted(random.nextInt(1_000_000));
        try {
            mailer.sendCode(normalized, code);
        } catch (RuntimeException e) {
            log.warn("OTP email failed for {}", normalized);
            throw ApiException.badGateway(SEND_FAILED);
        }

        Instant now = Instant.now();
        OtpChallenge challenge = new OtpChallenge();
        challenge.setEmail(normalized);
        challenge.setCodeHash(hashes.hmacRaw(code));
        challenge.setAttempts(0);
        challenge.setConsumed(false);
        challenge.setRequesterIp(ip);
        challenge.setCreatedAt(now);
        challenge.setCodeExpiresAt(now.plus(CODE_TTL));
        challenge.setExpiresAt(now.plus(CHALLENGE_TTL));
        challenges.save(challenge);
    }

    public String verify(String email, String code) {
        String normalized = emails.normalize(email);
        Instant now = Instant.now();
        OtpChallenge live = challenges
                .findFirstByEmailAndConsumedFalseAndCodeExpiresAtAfterOrderByCreatedAtDesc(normalized, now)
                .orElse(null);

        String presented = code == null || code.isBlank() ? "\0" : code.trim();
        byte[] actual = HexFormat.of().parseHex(hashes.hmacRaw(presented));
        byte[] expected = live == null ? dummyCodeHash : HexFormat.of().parseHex(live.getCodeHash());
        boolean matches = MessageDigest.isEqual(actual, expected) && live != null;
        if (!matches) {
            if (live != null) {
                live.setAttempts(live.getAttempts() + 1);
                if (live.getAttempts() >= MAX_ATTEMPTS) {
                    live.setConsumed(true);
                }
                challenges.save(live);
            }
            throw ApiException.unauthorized(INVALID_CODE);
        }

        List<OtpChallenge> open = new ArrayList<>(challenges.findByEmailAndConsumedFalse(normalized));
        if (open.stream().noneMatch(c -> sameChallenge(c, live))) {
            open.add(live);
        }
        for (OtpChallenge challenge : open) {
            challenge.setConsumed(true);
            challenges.save(challenge);
        }
        return hashes.hmac(normalized);
    }

    private static boolean sameChallenge(OtpChallenge left, OtpChallenge right) {
        return left == right || (left.getId() != null && Objects.equals(left.getId(), right.getId()));
    }
}

package com.ratemyuj.repository;

import com.ratemyuj.domain.OtpChallenge;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface OtpChallengeRepository extends JpaRepository<OtpChallenge, String> {

    long countByEmailAndCreatedAtAfter(String email, Instant createdAt);

    long countByRequesterIpAndCreatedAtAfter(String requesterIp, Instant createdAt);

    Optional<OtpChallenge> findFirstByEmailAndConsumedFalseAndCodeExpiresAtAfterOrderByCreatedAtDesc(
            String email, Instant codeExpiresAt);

    List<OtpChallenge> findByEmailAndConsumedFalse(String email);

    long deleteByExpiresAtBefore(Instant cutoff);
}

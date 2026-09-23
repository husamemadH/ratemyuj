package com.ratemyuj.auth;

import com.ratemyuj.config.AppProperties;
import com.ratemyuj.domain.OtpChallenge;
import com.ratemyuj.exception.ApiException;
import com.ratemyuj.repository.OtpChallengeRepository;
import com.ratemyuj.service.HashService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OtpServiceTest {

    private static final String EMAIL = "a@ju.edu.jo";

    @Mock private OtpChallengeRepository challenges;
    @Mock private OtpMailer mailer;

    private final HashService hashes = new HashService(new AppProperties("test-pepper-secret"));
    private OtpService service;

    @BeforeEach
    void setUp() {
        service = new OtpService(challenges, mailer, hashes, new EmailPolicy());
    }

    @Test
    @DisplayName("the third code in the window is sent, and only its hash is stored")
    void thirdRequestIsAllowed() {
        when(challenges.countByEmailAndCreatedAtAfter(eq(EMAIL), any())).thenReturn(2L);
        when(challenges.countByRequesterIpAndCreatedAtAfter(eq("10.0.0.1"), any())).thenReturn(0L);

        service.request("  A@JU.EDU.JO  ", "10.0.0.1");

        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(mailer).sendCode(eq(EMAIL), code.capture());
        assertThat(code.getValue()).matches("\\d{6}");

        ArgumentCaptor<OtpChallenge> saved = ArgumentCaptor.forClass(OtpChallenge.class);
        verify(challenges).save(saved.capture());
        OtpChallenge challenge = saved.getValue();
        assertThat(challenge.getEmail()).isEqualTo(EMAIL);
        assertThat(challenge.getCodeHash()).isEqualTo(hashes.hmacRaw(code.getValue())).doesNotContain(code.getValue());
        assertThat(challenge.isConsumed()).isFalse();
        assertThat(challenge.getAttempts()).isZero();
        assertThat(Duration.between(challenge.getCreatedAt(), challenge.getCodeExpiresAt()))
                .isEqualTo(Duration.ofMinutes(10));
        assertThat(Duration.between(challenge.getCreatedAt(), challenge.getExpiresAt()))
                .isEqualTo(Duration.ofMinutes(15));
    }

    @Test
    @DisplayName("the fourth code for one address is rejected before any mail is sent")
    void fourthRequestIsLimited() {
        when(challenges.countByEmailAndCreatedAtAfter(eq(EMAIL), any())).thenReturn(3L);

        assertThatThrownBy(() -> service.request(EMAIL, "10.0.0.1"))
                .isInstanceOfSatisfying(ApiException.class, error -> {
                    assertThat(error.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                    assertThat(error.getMessage()).isEqualTo(OtpService.RATE_LIMIT);
                });
        verify(mailer, never()).sendCode(anyString(), anyString());
        verify(challenges, never()).save(any());
    }

    @Test
    @DisplayName("the eleventh code from one address is rejected")
    void ipIsLimited() {
        when(challenges.countByEmailAndCreatedAtAfter(anyString(), any())).thenReturn(0L);
        when(challenges.countByRequesterIpAndCreatedAtAfter(eq("10.0.0.1"), any())).thenReturn(10L);

        assertThatThrownBy(() -> service.request(EMAIL, "10.0.0.1"))
                .isInstanceOfSatisfying(ApiException.class, error ->
                        assertThat(error.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
        verify(mailer, never()).sendCode(anyString(), anyString());
    }

    @Test
    @DisplayName("a mail failure stores nothing")
    void mailFailureDoesNotStore() {
        when(challenges.countByEmailAndCreatedAtAfter(anyString(), any())).thenReturn(0L);
        when(challenges.countByRequesterIpAndCreatedAtAfter(anyString(), any())).thenReturn(0L);
        doThrow(new IllegalStateException("smtp down")).when(mailer).sendCode(anyString(), anyString());

        assertThatThrownBy(() -> service.request(EMAIL, "10.0.0.1"))
                .isInstanceOfSatisfying(ApiException.class, error -> {
                    assertThat(error.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    assertThat(error.getMessage()).isEqualTo(OtpService.SEND_FAILED);
                });
        verify(challenges, never()).save(any());
    }

    @Test
    @DisplayName("a non-JU address never reaches the mailer")
    void foreignDomainIsRejected() {
        assertThatThrownBy(() -> service.request("someone@gmail.com", "10.0.0.1"))
                .isInstanceOfSatisfying(ApiException.class, error ->
                        assertThat(error.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        verify(mailer, never()).sendCode(anyString(), anyString());
    }

    @Test
    @DisplayName("a wrong code increments attempts and stays usable")
    void wrongCodeIncrements() {
        OtpChallenge live = challenge(hashes.hmacRaw("111111"), 0);
        when(challenges.findFirstByEmailAndConsumedFalseAndCodeExpiresAtAfterOrderByCreatedAtDesc(eq(EMAIL), any()))
                .thenReturn(Optional.of(live));

        assertThatThrownBy(() -> service.verify(EMAIL, "000000"))
                .isInstanceOfSatisfying(ApiException.class, error -> {
                    assertThat(error.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(error.getMessage()).isEqualTo(OtpService.INVALID_CODE);
                });
        assertThat(live.getAttempts()).isEqualTo(1);
        assertThat(live.isConsumed()).isFalse();
        verify(challenges).save(live);
    }

    @Test
    @DisplayName("the fifth miss consumes the challenge")
    void fifthMissConsumes() {
        OtpChallenge live = challenge(hashes.hmacRaw("111111"), 4);
        when(challenges.findFirstByEmailAndConsumedFalseAndCodeExpiresAtAfterOrderByCreatedAtDesc(eq(EMAIL), any()))
                .thenReturn(Optional.of(live));

        assertThatThrownBy(() -> service.verify(EMAIL, "000000"))
                .hasMessage(OtpService.INVALID_CODE);
        assertThat(live.getAttempts()).isEqualTo(5);
        assertThat(live.isConsumed()).isTrue();
    }

    @Test
    @DisplayName("the right code returns the student hash and consumes the challenge")
    void success() {
        OtpChallenge live = challenge(hashes.hmacRaw("111111"), 2);
        live.setId("ch-1");
        when(challenges.findFirstByEmailAndConsumedFalseAndCodeExpiresAtAfterOrderByCreatedAtDesc(eq(EMAIL), any()))
                .thenReturn(Optional.of(live));
        when(challenges.findByEmailAndConsumedFalse(EMAIL)).thenReturn(List.of(live));

        String hash = service.verify("  A@JU.edu.jo ", "111111");

        assertThat(hash).isEqualTo(hashes.hmac(EMAIL));
        assertThat(live.isConsumed()).isTrue();
    }

    @Test
    @DisplayName("an unknown address and an expired code share one error")
    void unknownAndExpiredLookTheSame() {
        when(challenges.findFirstByEmailAndConsumedFalseAndCodeExpiresAtAfterOrderByCreatedAtDesc(eq(EMAIL), any()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.verify(EMAIL, "111111"))
                .isInstanceOfSatisfying(ApiException.class, error -> {
                    assertThat(error.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(error.getMessage()).isEqualTo(OtpService.INVALID_CODE);
                });
        verify(challenges, never()).save(any());
    }

    private static OtpChallenge challenge(String codeHash, int attempts) {
        OtpChallenge challenge = new OtpChallenge();
        challenge.setEmail(EMAIL);
        challenge.setCodeHash(codeHash);
        challenge.setAttempts(attempts);
        challenge.setConsumed(false);
        return challenge;
    }
}

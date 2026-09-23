package com.ratemyuj.service;

import com.ratemyuj.config.AppProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HashServiceTest {

    private final HashService service = new HashService(new AppProperties("test-pepper-secret"));

    @Test
    @DisplayName("same email always produces the same hash (stable identity)")
    void deterministic() {
        assertThat(service.hmac("student@ju.edu.jo"))
                .isEqualTo(service.hmac("student@ju.edu.jo"));
    }

    @Test
    @DisplayName("case and whitespace are normalized before hashing")
    void normalization() {
        String canonical = service.hmac("student@ju.edu.jo");
        assertThat(service.hmac("  STUDENT@JU.EDU.JO  ")).isEqualTo(canonical);
    }

    @Test
    @DisplayName("output is 64 lowercase hex chars and never contains the email")
    void outputShape() {
        String hash = service.hmac("student@ju.edu.jo");
        assertThat(hash).hasSize(64).matches("[0-9a-f]+").doesNotContain("student");
    }

    @Test
    @DisplayName("different emails produce different hashes")
    void differentInputsDiffer() {
        assertThat(service.hmac("a@ju.edu.jo")).isNotEqualTo(service.hmac("b@ju.edu.jo"));
    }

    @Test
    @DisplayName("a different pepper produces a different hash (secret actually matters)")
    void pepperMatters() {
        HashService other = new HashService(new AppProperties("another-pepper"));
        assertThat(other.hmac("a@ju.edu.jo")).isNotEqualTo(service.hmac("a@ju.edu.jo"));
    }

    @Test
    @DisplayName("service refuses to start without a pepper — fail fast, not silently insecure")
    void missingPepperFailsFast() {
        assertThatThrownBy(() -> new HashService(new AppProperties(" ")))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new HashService(new AppProperties(null)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("raw HMAC does not trim or lowercase, so codes are not rewritten")
    void hmacRawDoesNotNormalize() {
        assertThat(service.hmacRaw("AbC")).isNotEqualTo(service.hmacRaw("abc"));
        assertThat(service.hmacRaw("  abc")).isNotEqualTo(service.hmacRaw("abc"));
    }

    @Test
    @DisplayName("blank email input is rejected")
    void blankEmailRejected() {
        assertThatThrownBy(() -> service.hmac("  "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

package com.ratemyuj.auth;

import com.ratemyuj.exception.ApiException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmailPolicyTest {

    private final EmailPolicy policy = new EmailPolicy();

    @Test
    @DisplayName("a dotted local part is a valid JU address, after case and space are folded")
    void acceptsJuAddress() {
        assertThat(policy.normalize("  User.Name@JU.edu.jo  ")).isEqualTo("user.name@ju.edu.jo");
    }

    @Test
    @DisplayName("other domains, subdomains, plus-tags, and a missing local part are rejected")
    void rejectsAnythingElse() {
        assertRejected("student@gmail.com");
        assertRejected("student@cs.ju.edu.jo");
        assertRejected("student+tag@ju.edu.jo");
        assertRejected("@ju.edu.jo");
        assertRejected("student@ju.edu.jo.attacker.com");
        assertRejected("   ");
        assertRejected(null);
    }

    private void assertRejected(String email) {
        assertThatThrownBy(() -> policy.normalize(email))
                .isInstanceOfSatisfying(ApiException.class, error -> {
                    assertThat(error.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(error.getMessage()).isEqualTo(EmailPolicy.MESSAGE);
                });
    }
}

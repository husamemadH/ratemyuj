package com.ratemyuj.auth;

import com.ratemyuj.config.AuthProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef";

    private final JwtService service = new JwtService(props(SECRET));

    @Test
    @DisplayName("the subject round-trips and the email never appears in the token")
    void roundTrip() {
        String token = service.sign("abc123");
        assertThat(service.parse(token)).contains("abc123");
        assertThat(token).doesNotContain("student@ju.edu.jo");
    }

    @Test
    @DisplayName("an expired token parses empty")
    void expired() {
        Instant past = Instant.now().minus(Duration.ofMinutes(5));
        String token = Jwts.builder()
                .issuer(JwtService.ISSUER)
                .audience().add(JwtService.AUDIENCE).and()
                .subject("abc123")
                .issuedAt(Date.from(past.minus(Duration.ofMinutes(1))))
                .expiration(Date.from(past))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
        assertThat(service.parse(token)).isEmpty();
    }

    @Test
    @DisplayName("a token signed with a different secret parses empty")
    void wrongSecret() {
        String token = service.sign("abc123");
        JwtService other = new JwtService(props("fedcba9876543210fedcba9876543210"));
        assertThat(other.parse(token)).isEmpty();
    }

    @Test
    @DisplayName("a token for a different audience parses empty")
    void wrongAudience() {
        String token = Jwts.builder()
                .issuer(JwtService.ISSUER)
                .audience().add("somewhere-else").and()
                .subject("abc123")
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plus(Duration.ofMinutes(5))))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
        assertThat(service.parse(token)).isEmpty();
    }

    @Test
    @DisplayName("a missing or short secret refuses to start")
    void shortSecretFailsFast() {
        assertThatThrownBy(() -> new JwtService(props("short")))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new JwtService(props(null)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new JwtService(props("0123456789abcdef0123456789abcde")))
                .isInstanceOf(IllegalStateException.class);
    }

    private static AuthProperties props(String secret) {
        return new AuthProperties(secret, Duration.ofDays(7), "session", true, "log");
    }
}

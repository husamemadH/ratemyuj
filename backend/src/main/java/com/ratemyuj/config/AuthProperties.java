package com.ratemyuj.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.auth")
public record AuthProperties(
        String jwtSecret,
        Duration jwtTtl,
        String cookieName,
        Boolean cookieSecure,
        String mailMode
) {
    public AuthProperties {
        if (jwtTtl == null) {
            jwtTtl = Duration.ofDays(7);
        }
        if (cookieName == null || cookieName.isBlank()) {
            cookieName = "session";
        }
        if (cookieSecure == null) {
            cookieSecure = true;
        }
        if (mailMode == null || mailMode.isBlank()) {
            mailMode = "log";
        }
    }
}

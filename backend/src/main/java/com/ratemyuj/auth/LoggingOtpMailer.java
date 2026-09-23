package com.ratemyuj.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.auth", name = "mail-mode", havingValue = "log", matchIfMissing = true)
public class LoggingOtpMailer implements OtpMailer {

    private static final Logger log = LoggerFactory.getLogger(LoggingOtpMailer.class);

    @Override
    public void sendCode(String email, String code) {
        log.info("OTP for {}: {}", email, code);
    }
}

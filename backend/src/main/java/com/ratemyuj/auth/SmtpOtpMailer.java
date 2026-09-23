package com.ratemyuj.auth;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.auth", name = "mail-mode", havingValue = "smtp")
public class SmtpOtpMailer implements OtpMailer {

    private final JavaMailSender mail;
    private final String from;

    public SmtpOtpMailer(ObjectProvider<JavaMailSender> mail,
                         @Value("${spring.mail.host:}") String host,
                         @Value("${spring.mail.username:}") String from) {
        if (host == null || host.isBlank()) {
            throw new IllegalStateException("SMTP_HOST is required when MAIL_MODE=smtp");
        }
        JavaMailSender sender = mail.getIfAvailable();
        if (sender == null) {
            throw new IllegalStateException("SMTP mail sender is not configured");
        }
        this.mail = sender;
        this.from = from;
    }

    @Override
    public void sendCode(String email, String code) {
        SimpleMailMessage message = new SimpleMailMessage();
        if (from != null && !from.isBlank()) {
            message.setFrom(from);
        }
        message.setTo(email);
        message.setSubject("رمز الدخول إلى قيّم دكتورك");
        message.setText("""
                رمز الدخول الخاص بك: %s
                Your sign-in code: %s

                ينتهي خلال 10 دقائق.
                It expires in 10 minutes.
                """.formatted(code, code));
        mail.send(message);
    }
}

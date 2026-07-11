package com.ratemyuj.service;

import com.ratemyuj.config.AppProperties;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

/**
 * The single funnel for student identity. Everything that needs to reference
 * a student stores hmac(email) — plaintext emails never reach the database.
 *
 * Emails are normalized (trim + lowercase) before hashing so the same student
 * always maps to the same hash regardless of how they typed their address.
 */
@Service
public class HashService {

    private static final String ALGORITHM = "HmacSHA256";

    private final SecretKeySpec key;

    public HashService(AppProperties props) {
        if (props.hashPepper() == null || props.hashPepper().isBlank()) {
            throw new IllegalStateException(
                    "app.hash-pepper (HASH_PEPPER env var) must be set — refusing to start without it");
        }
        this.key = new SecretKeySpec(props.hashPepper().getBytes(StandardCharsets.UTF_8), ALGORITHM);
    }

    public String hmac(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("email must not be blank");
        }
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            return HexFormat.of().formatHex(mac.doFinal(normalized.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HMAC computation failed", e);
        }
    }
}

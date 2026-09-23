package com.ratemyuj.auth;

import com.ratemyuj.exception.ApiException;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * JU mailbox only. Plus-tags are rejected so one inbox cannot mint many student hashes.
 */
@Component
public class EmailPolicy {

    public static final String MESSAGE = "Use your @ju.edu.jo email address";

    private static final Pattern ALLOWED = Pattern.compile("^[a-z0-9._-]+@ju\\.edu\\.jo$");

    public String normalize(String email) {
        if (email == null) {
            throw ApiException.badRequest(MESSAGE);
        }
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        if (!ALLOWED.matcher(normalized).matches()) {
            throw ApiException.badRequest(MESSAGE);
        }
        return normalized;
    }
}

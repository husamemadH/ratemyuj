package com.ratemyuj.auth;

import com.ratemyuj.config.AdminProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Guards /api/admin/** with a static API key from app.admin.api-key.
 * Disabled (rejects everything) when the key is not configured.
 */
public class AdminApiKeyFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Admin-Key";
    public static final String MESSAGE = "Admin API key required";

    private final String expected;

    public AdminApiKeyFilter(AdminProperties props) {
        this.expected = props.apiKey();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/admin/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String provided = request.getHeader(HEADER);
        if (expected == null || expected.isBlank() || provided == null
                || !MessageDigest.isEqual(
                        provided.getBytes(StandardCharsets.UTF_8),
                        expected.getBytes(StandardCharsets.UTF_8))) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"error\":\"" + MESSAGE + "\"}");
            return;
        }
        chain.doFilter(request, response);
    }
}

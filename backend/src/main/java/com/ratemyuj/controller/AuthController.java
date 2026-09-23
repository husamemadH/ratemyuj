package com.ratemyuj.controller;

import com.ratemyuj.auth.AuthCookie;
import com.ratemyuj.auth.EmailPolicy;
import com.ratemyuj.auth.JwtService;
import com.ratemyuj.auth.OtpService;
import com.ratemyuj.auth.StudentPrincipal;
import com.ratemyuj.dto.AuthEmailRequest;
import com.ratemyuj.dto.AuthVerifyRequest;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final EmailPolicy emails;
    private final OtpService otp;
    private final JwtService jwt;
    private final AuthCookie cookies;

    public AuthController(EmailPolicy emails, OtpService otp, JwtService jwt, AuthCookie cookies) {
        this.emails = emails;
        this.otp = otp;
        this.jwt = jwt;
        this.cookies = cookies;
    }

    @PostMapping("/request")
    public Map<String, String> request(@RequestBody AuthEmailRequest body, HttpServletRequest request) {
        String email = emails.normalize(body.email());
        otp.request(email, request.getRemoteAddr());
        return Map.of("status", "sent");
    }

    @PostMapping("/verify")
    public ResponseEntity<Map<String, Boolean>> verify(@RequestBody AuthVerifyRequest body) {
        String email = emails.normalize(body.email());
        String studentHash = otp.verify(email, body.code());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookies.session(jwt.sign(studentHash)).toString())
                .body(Map.of("authenticated", true));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookies.clear().toString())
                .build();
    }

    @GetMapping("/me")
    public Map<String, Boolean> me(@AuthenticationPrincipal StudentPrincipal student) {
        return Map.of("authenticated", student != null);
    }
}

package com.ratemyuj.auth;

import com.ratemyuj.config.AuthProperties;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public class AuthCookie {

    private final AuthProperties props;

    public AuthCookie(AuthProperties props) {
        this.props = props;
    }

    public ResponseCookie session(String token) {
        return base(token).maxAge(props.jwtTtl()).build();
    }

    public ResponseCookie clear() {
        return base("").maxAge(0).build();
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(props.cookieName(), value)
                .httpOnly(true)
                .secure(props.cookieSecure())
                .sameSite("Lax")
                .path("/");
    }
}

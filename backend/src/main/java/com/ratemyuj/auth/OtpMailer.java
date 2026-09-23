package com.ratemyuj.auth;

public interface OtpMailer {
    void sendCode(String email, String code);
}

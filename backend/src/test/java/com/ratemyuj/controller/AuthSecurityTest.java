package com.ratemyuj.controller;

import com.ratemyuj.auth.AuthCookie;
import com.ratemyuj.auth.EmailPolicy;
import com.ratemyuj.auth.JwtService;
import com.ratemyuj.auth.OtpService;
import com.ratemyuj.config.SecurityConfig;
import com.ratemyuj.domain.ReviewStatus;
import com.ratemyuj.dto.ReviewSubmissionResponse;
import com.ratemyuj.service.ReviewService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import com.ratemyuj.config.AuthProperties;

import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {AuthController.class, ReviewController.class})
@Import({SecurityConfig.class, EmailPolicy.class, AuthCookie.class})
@EnableConfigurationProperties(AuthProperties.class)
@TestPropertySource(properties = {
        "JWT_SECRET=0123456789abcdef0123456789abcdef",
        "app.auth.jwt-secret=0123456789abcdef0123456789abcdef",
        "app.auth.jwt-ttl=7d",
        "app.auth.cookie-name=session",
        "app.auth.cookie-secure=true",
        "app.auth.mail-mode=log"
})
class AuthSecurityTest {

    private static final String VALID_BODY = """
            {"professorId":"p1","courseId":"c1","rating":5,
             "comment":"Lectures are clear and the exams match the sheets.",
             "grade":"A","difficulty":3,"wouldTakeAgain":true}""";

    @Autowired private MockMvc mvc;
    @MockBean private JwtService jwtService;
    @MockBean private OtpService otpService;
    @MockBean private ReviewService reviewService;

    @BeforeEach
    void stubTokens() {
        lenient().when(jwtService.parse(any())).thenReturn(Optional.empty());
        lenient().when(jwtService.sign(any())).thenReturn("signed-token");
    }

    @Test
    @DisplayName("an anonymous review submit is 401")
    void anonymousSubmitIsRejected() throws Exception {
        mvc.perform(post("/api/reviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value(SecurityConfig.SIGN_IN_MESSAGE));
        verify(reviewService, never()).submit(anyString(), any());
    }

    @Test
    @DisplayName("a valid session cookie is the student hash passed to submit")
    void cookieBecomesThePrincipal() throws Exception {
        when(jwtService.parse("good")).thenReturn(Optional.of("hash-abc"));
        when(reviewService.submit(eq("hash-abc"), any()))
                .thenReturn(new ReviewSubmissionResponse("rev-1", ReviewStatus.PUBLISHED, null, List.of()));

        mvc.perform(post("/api/reviews")
                        .cookie(new Cookie("session", "good"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewId").value("rev-1"));

        verify(reviewService).submit(eq("hash-abc"), any());
    }

    @Test
    @DisplayName("anonymous review listing stays public")
    void anonymousListing() throws Exception {
        when(reviewService.publishedReviews(anyString(), anyString(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        mvc.perform(get("/api/professors/p1/reviews"))
                .andExpect(status().isOk());
        verify(reviewService).publishedReviews(eq("p1"), eq(""), any(Pageable.class));
    }

    @Test
    @DisplayName("verifying a code sets an HttpOnly Secure SameSite=Lax session cookie")
    void verifySetsCookie() throws Exception {
        when(otpService.verify("student@ju.edu.jo", "123456")).thenReturn("hash-abc");
        when(jwtService.sign("hash-abc")).thenReturn("signed-token");

        mvc.perform(post("/api/auth/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\" Student@JU.edu.jo \",\"code\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, allOf(
                        containsString("session=signed-token"),
                        containsString("HttpOnly"),
                        containsString("Secure"),
                        containsString("SameSite=Lax"))));
    }

    @Test
    @DisplayName("logout clears the session cookie")
    void logoutClearsCookie() throws Exception {
        mvc.perform(post("/api/auth/logout"))
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, allOf(
                        containsString("session="),
                        containsString("Max-Age=0"),
                        containsString("HttpOnly"))));
    }

    @Test
    @DisplayName("me reports anonymous without a cookie and signed-in with one")
    void me() throws Exception {
        mvc.perform(get("/api/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(false));

        when(jwtService.parse("good")).thenReturn(Optional.of("hash-abc"));
        mvc.perform(get("/api/auth/me").cookie(new Cookie("session", "good")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true));
    }

    @Test
    @DisplayName("a non-JU address is rejected before a code is issued")
    void foreignDomainDoesNotSend() throws Exception {
        mvc.perform(post("/api/auth/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"someone@gmail.com\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(EmailPolicy.MESSAGE));
        verifyNoInteractions(otpService);
    }
}

package com.ratemyuj.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ratemyuj.auth.AdminApiKeyFilter;
import com.ratemyuj.auth.JwtAuthFilter;
import com.ratemyuj.auth.JwtService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.util.Map;

@Configuration
public class SecurityConfig {

    public static final String SIGN_IN_MESSAGE = "Sign in with your JU email";

    @Bean
    JwtAuthFilter jwtAuthFilter(JwtService jwtService, AuthProperties authProperties) {
        return new JwtAuthFilter(jwtService, authProperties.cookieName());
    }

    @Bean
    AdminApiKeyFilter adminApiKeyFilter(AdminProperties adminProperties) {
        return new AdminApiKeyFilter(adminProperties);
    }

    @Bean
    AuthenticationEntryPoint authenticationEntryPoint(ObjectMapper mapper) {
        return (request, response, ex) -> {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            mapper.writeValue(response.getOutputStream(), Map.of("error", SIGN_IN_MESSAGE));
        };
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthFilter jwtAuthFilter,
                                            AdminApiKeyFilter adminApiKeyFilter,
                                            AuthenticationEntryPoint authenticationEntryPoint) throws Exception {
        // SameSite=Lax on the session cookie is the CSRF control. No state-changing route is a GET.
        http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(cache -> cache.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(errors -> errors.authenticationEntryPoint(authenticationEntryPoint))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/auth/request", "/api/auth/verify", "/api/auth/logout").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/auth/me").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/professors/**").permitAll()
                        .requestMatchers("/api/admin/**").permitAll()   // AdminApiKeyFilter guards these
                        .requestMatchers("/error").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(adminApiKeyFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}

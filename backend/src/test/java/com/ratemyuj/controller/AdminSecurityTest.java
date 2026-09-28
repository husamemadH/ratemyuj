package com.ratemyuj.controller;

import com.ratemyuj.auth.AdminApiKeyFilter;
import com.ratemyuj.auth.JwtService;
import com.ratemyuj.config.AdminProperties;
import com.ratemyuj.config.AuthProperties;
import com.ratemyuj.config.SecurityConfig;
import com.ratemyuj.service.AdminService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AdminController.class)
@Import(SecurityConfig.class)
@EnableConfigurationProperties({AuthProperties.class, AdminProperties.class})
@TestPropertySource(properties = {
        "app.auth.jwt-secret=0123456789abcdef0123456789abcdef",
        "app.auth.cookie-name=session",
        "app.admin.api-key=secret-admin"
})
class AdminSecurityTest {

    @Autowired private MockMvc mvc;
    @MockBean private AdminService adminService;
    @MockBean private JwtService jwtService;

    @Test
    @DisplayName("admin endpoints reject a request without the key")
    void missingKeyIsForbidden() throws Exception {
        mvc.perform(get("/api/admin/reviews"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value(AdminApiKeyFilter.MESSAGE));
    }

    @Test
    @DisplayName("admin endpoints reject a wrong key")
    void wrongKeyIsForbidden() throws Exception {
        mvc.perform(get("/api/admin/reviews").header(AdminApiKeyFilter.HEADER, "nope"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("the configured key unlocks the queue")
    void correctKeyAllows() throws Exception {
        when(adminService.list(any(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        mvc.perform(get("/api/admin/reviews")
                        .param("status", "MANUAL_REVIEW")
                        .header(AdminApiKeyFilter.HEADER, "secret-admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    @DisplayName("an invalid status filter is a 400, not a 500")
    void invalidStatusIsBadRequest() throws Exception {
        mvc.perform(get("/api/admin/reviews")
                        .param("status", "NOT_A_STATUS")
                        .header(AdminApiKeyFilter.HEADER, "secret-admin"))
                .andExpect(status().isBadRequest());
    }
}

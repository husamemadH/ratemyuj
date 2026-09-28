package com.ratemyuj.controller;

import com.ratemyuj.domain.ReviewStatus;
import com.ratemyuj.dto.AdminReviewResponse;
import com.ratemyuj.dto.AdminStatusRequest;
import com.ratemyuj.service.AdminService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Guarded by AdminApiKeyFilter: requires the X-Admin-Key header when
 * app.admin.api-key is configured.
 */
@RestController
@RequestMapping("/api/admin/reviews")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping
    public Page<AdminReviewResponse> list(
            @RequestParam(required = false) ReviewStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), 100);
        return adminService.list(status,
                PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "updatedAt")));
    }

    @PatchMapping("/{id}")
    public AdminReviewResponse update(@PathVariable String id, @Valid @RequestBody AdminStatusRequest request) {
        return adminService.changeStatus(id, request);
    }
}

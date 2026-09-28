package com.ratemyuj.controller;

import com.ratemyuj.dto.ProfessorDetailResponse;
import com.ratemyuj.dto.ProfessorSummaryResponse;
import com.ratemyuj.service.ProfessorService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/professors")
public class ProfessorController {

    private final ProfessorService professorService;

    public ProfessorController(ProfessorService professorService) {
        this.professorService = professorService;
    }

    @GetMapping
    public Page<ProfessorSummaryResponse> search(
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), 50);
        return professorService.search(q, PageRequest.of(safePage, safeSize,
                Sort.by(Sort.Order.desc("reviewCount"), Sort.Order.asc("fullName"))));
    }

    @GetMapping("/{id}")
    public ProfessorDetailResponse detail(@PathVariable String id) {
        return professorService.detail(id);
    }
}

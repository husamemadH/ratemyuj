package com.ratemyuj.dto;

import java.util.List;

public record ProfessorSummaryResponse(
        String id,
        String title,
        String fullName,
        String department,
        String college,
        String photoUrl,
        double avgRating,
        int reviewCount,
        List<String> courseCodes
) {}

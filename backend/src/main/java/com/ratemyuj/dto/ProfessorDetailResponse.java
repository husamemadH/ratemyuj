package com.ratemyuj.dto;

import java.util.List;
import java.util.Map;

public record ProfessorDetailResponse(
        String id,
        String title,
        String fullName,
        String department,
        String college,
        String photoUrl,
        double avgRating,
        int reviewCount,
        Map<String, Integer> breakdown,   // "1".."5" -> count
        List<CourseResponse> courses
) {}

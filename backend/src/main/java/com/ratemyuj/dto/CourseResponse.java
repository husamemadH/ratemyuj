package com.ratemyuj.dto;

public record CourseResponse(
        String id,
        String code,
        String name,
        String department,
        int creditHours
) {}

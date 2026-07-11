package com.ratemyuj.dto;

import com.ratemyuj.domain.Grade;

import java.time.Instant;

public record ReviewResponse(
        String id,
        int rating,
        String comment,
        Grade grade,
        String courseCode,
        String courseName,
        Integer difficulty,
        Boolean wouldTakeAgain,
        Instant createdAt,
        boolean isMine
) {}

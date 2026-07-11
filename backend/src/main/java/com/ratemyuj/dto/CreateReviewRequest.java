package com.ratemyuj.dto;

import com.ratemyuj.domain.Grade;
import jakarta.validation.constraints.*;

public record CreateReviewRequest(
        @NotBlank String professorId,
        @NotBlank String courseId,
        @Min(1) @Max(5) int rating,
        @NotBlank @Size(min = 20, max = 1000,
                message = "Comment must be between 20 and 1000 characters")
        String comment,
        @NotNull Grade grade,
        @Min(1) @Max(5) Integer difficulty,   // nullable
        Boolean wouldTakeAgain                 // nullable
) {}

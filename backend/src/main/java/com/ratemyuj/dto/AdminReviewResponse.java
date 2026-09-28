package com.ratemyuj.dto;

import com.ratemyuj.domain.ModerationVerdict;
import com.ratemyuj.domain.ReviewStatus;

import java.time.Instant;
import java.util.List;

public record AdminReviewResponse(
        String id,
        String professorId,
        String professorName,
        String courseCode,
        String courseName,
        String studentHash,
        int rating,
        String comment,
        String grade,
        Integer difficulty,
        Boolean wouldTakeAgain,
        ReviewStatus status,
        AdminModerationResponse moderation,
        Instant createdAt,
        Instant updatedAt
) {
    public record AdminModerationResponse(
            ModerationVerdict verdict,
            List<String> flaggedCategories,
            String studentFeedback,
            String internalReason,
            double confidence,
            String model,
            long latencyMs,
            Instant checkedAt
    ) {}
}

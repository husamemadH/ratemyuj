package com.ratemyuj.dto;

import com.ratemyuj.domain.ReviewStatus;

import java.util.List;

/**
 * What the student sees right after submitting.
 * outcome drives the UI:
 *   PUBLISHED      -> success state, review appears immediately
 *   REJECTED       -> show feedback + flagged categories, let them edit and resubmit
 *   MANUAL_REVIEW  -> "held for review" state
 */
public record ReviewSubmissionResponse(
        String reviewId,
        ReviewStatus outcome,
        String feedback,               // null when PUBLISHED
        List<String> flaggedCategories // empty when PUBLISHED
) {}

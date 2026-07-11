package com.ratemyuj.moderation;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * The exact JSON schema the model is instructed to return.
 * Anything that fails to parse into this shape is treated as ESCALATE.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ModerationDecision(
        String verdict,                 // "APPROVE" | "REJECT" | "ESCALATE"
        List<String> categories,        // subset of policy category codes
        String student_feedback,        // 1-2 sentences, shown to student on REJECT
        String internal_reason,         // fuller reasoning, admin-only
        double confidence               // 0.0 - 1.0
) {}

package com.ratemyuj.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;

/**
 * Embedded in Review. Full audit trail of what Jev decided and why,
 * so admins can override and the question set can be tuned over time.
 */
@Embeddable
public class ModerationResult {

    @Enumerated(EnumType.STRING)
    @Column(name = "moderation_verdict")
    private ModerationVerdict verdict;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "moderation_flagged_categories", columnDefinition = "jsonb")
    private List<String> flaggedCategories; // e.g. ["PROFANITY", "PERSONAL_ATTACK"]

    @Column(name = "moderation_student_feedback", columnDefinition = "text")
    private String studentFeedback;         // shown to the student on rejection

    @Column(name = "moderation_internal_reason", columnDefinition = "text")
    private String internalReason;          // choice probabilities, admin-only

    @Column(name = "moderation_confidence")
    private double confidence;              // 0.0 - 1.0 reported by Jev

    @Column(name = "moderation_model")
    private String model;                   // which Jev release judged it

    @Column(name = "moderation_latency_ms")
    private long latencyMs;

    @Column(name = "moderation_checked_at")
    private Instant checkedAt;

    public ModerationResult() {}

    public ModerationResult(ModerationVerdict verdict, List<String> flaggedCategories,
                            String studentFeedback, String internalReason,
                            double confidence, String model, long latencyMs) {
        this.verdict = verdict;
        this.flaggedCategories = flaggedCategories;
        this.studentFeedback = studentFeedback;
        this.internalReason = internalReason;
        this.confidence = confidence;
        this.model = model;
        this.latencyMs = latencyMs;
        this.checkedAt = Instant.now();
    }

    public ModerationVerdict getVerdict() { return verdict; }
    public void setVerdict(ModerationVerdict verdict) { this.verdict = verdict; }
    public List<String> getFlaggedCategories() { return flaggedCategories; }
    public void setFlaggedCategories(List<String> flaggedCategories) { this.flaggedCategories = flaggedCategories; }
    public String getStudentFeedback() { return studentFeedback; }
    public void setStudentFeedback(String studentFeedback) { this.studentFeedback = studentFeedback; }
    public String getInternalReason() { return internalReason; }
    public void setInternalReason(String internalReason) { this.internalReason = internalReason; }
    public double getConfidence() { return confidence; }
    public void setConfidence(double confidence) { this.confidence = confidence; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public long getLatencyMs() { return latencyMs; }
    public void setLatencyMs(long latencyMs) { this.latencyMs = latencyMs; }
    public Instant getCheckedAt() { return checkedAt; }
    public void setCheckedAt(Instant checkedAt) { this.checkedAt = checkedAt; }
}

package com.ratemyuj.domain;

import java.time.Instant;
import java.util.List;

/**
 * Embedded in Review. Full audit trail of what the AI decided and why,
 * so admins can override and the prompt can be tuned over time.
 */
public class ModerationResult {

    private ModerationVerdict verdict;
    private List<String> flaggedCategories; // e.g. ["PROFANITY", "PERSONAL_ATTACK"]
    private String studentFeedback;         // shown to the student on rejection
    private String internalReason;          // model's full reasoning, admin-only
    private double confidence;              // 0.0 - 1.0 self-reported by model
    private String model;                   // which OpenRouter model judged it
    private long latencyMs;
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

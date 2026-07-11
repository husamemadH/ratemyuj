package com.ratemyuj.domain;

public enum ModerationVerdict {
    APPROVE,   // constructive, publish immediately
    REJECT,    // violates policy, return reason to student
    ESCALATE   // model unsure — route to MANUAL_REVIEW
}

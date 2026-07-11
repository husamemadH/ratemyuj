package com.ratemyuj.domain;

public enum ReviewStatus {
    PENDING_MODERATION,  // just submitted, AI check in flight
    PUBLISHED,           // AI approved
    REJECTED,            // AI rejected — student sees reason, may revise & resubmit
    MANUAL_REVIEW,       // AI unavailable/unsure — held for an admin
    HIDDEN,              // admin hid after reports
    REMOVED              // admin removed permanently
}

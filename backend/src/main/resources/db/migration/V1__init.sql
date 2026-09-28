CREATE TABLE courses (
    id VARCHAR(36) PRIMARY KEY,
    code VARCHAR(255) NOT NULL UNIQUE,
    name VARCHAR(255),
    department VARCHAR(255),
    credit_hours INTEGER NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE professors (
    id VARCHAR(36) PRIMARY KEY,
    full_name VARCHAR(255),
    department VARCHAR(255),
    college VARCHAR(255),
    title VARCHAR(255),
    photo_url VARCHAR(255),
    avg_rating DOUBLE PRECISION NOT NULL DEFAULT 0,
    review_count INTEGER NOT NULL DEFAULT 0,
    rating_sum INTEGER NOT NULL DEFAULT 0,
    breakdown_one INTEGER NOT NULL DEFAULT 0,
    breakdown_two INTEGER NOT NULL DEFAULT 0,
    breakdown_three INTEGER NOT NULL DEFAULT 0,
    breakdown_four INTEGER NOT NULL DEFAULT 0,
    breakdown_five INTEGER NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ
);

CREATE TABLE professor_courses (
    professor_id VARCHAR(36) NOT NULL REFERENCES professors (id) ON DELETE CASCADE,
    course_id VARCHAR(36) NOT NULL REFERENCES courses (id) ON DELETE CASCADE,
    PRIMARY KEY (professor_id, course_id)
);

CREATE TABLE reviews (
    id VARCHAR(36) PRIMARY KEY,
    professor_id VARCHAR(36) NOT NULL REFERENCES professors (id),
    course_id VARCHAR(36) NOT NULL REFERENCES courses (id),
    course_code VARCHAR(255),
    course_name VARCHAR(255),
    student_hash VARCHAR(64) NOT NULL,
    rating INTEGER NOT NULL,
    comment VARCHAR(1000) NOT NULL,
    grade VARCHAR(32),
    difficulty INTEGER,
    would_take_again BOOLEAN,
    status VARCHAR(32) NOT NULL,
    moderation_verdict VARCHAR(32),
    moderation_flagged_categories JSONB,
    moderation_student_feedback TEXT,
    moderation_internal_reason TEXT,
    moderation_confidence DOUBLE PRECISION,
    moderation_model VARCHAR(255),
    moderation_latency_ms BIGINT,
    moderation_checked_at TIMESTAMPTZ,
    report_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ,
    CONSTRAINT uniq_review UNIQUE (student_hash, professor_id, course_id)
);

CREATE INDEX idx_reviews_professor_status ON reviews (professor_id, status);
CREATE INDEX idx_reviews_status ON reviews (status);

CREATE TABLE otp_challenges (
    id VARCHAR(36) PRIMARY KEY,
    email VARCHAR(255) NOT NULL,
    code_hash VARCHAR(64) NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    consumed BOOLEAN NOT NULL DEFAULT FALSE,
    requester_ip VARCHAR(64),
    created_at TIMESTAMPTZ,
    code_expires_at TIMESTAMPTZ,
    expires_at TIMESTAMPTZ
);

CREATE INDEX idx_otp_email_created ON otp_challenges (email, created_at DESC);
CREATE INDEX idx_otp_expires ON otp_challenges (expires_at);

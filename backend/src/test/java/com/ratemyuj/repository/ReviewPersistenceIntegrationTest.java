package com.ratemyuj.repository;

import com.ratemyuj.PostgresIntegrationTest;
import com.ratemyuj.auth.OtpService;
import com.ratemyuj.domain.Course;
import com.ratemyuj.domain.Grade;
import com.ratemyuj.domain.ModerationResult;
import com.ratemyuj.domain.ModerationVerdict;
import com.ratemyuj.domain.OtpChallenge;
import com.ratemyuj.domain.Professor;
import com.ratemyuj.domain.Review;
import com.ratemyuj.domain.ReviewStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReviewPersistenceIntegrationTest extends PostgresIntegrationTest {

    @Autowired private ReviewRepository reviews;
    @Autowired private ProfessorRepository professors;
    @Autowired private CourseRepository courses;
    @Autowired private OtpChallengeRepository challenges;
    @Autowired private OtpService otpService;

    private Professor professor;
    private Course course;

    @BeforeEach
    void setUp() {
        reviews.deleteAll();
        professors.deleteAll();
        courses.deleteAll();
        challenges.deleteAll();

        Course newCourse = new Course();
        newCourse.setCode("CS101");
        newCourse.setName("Intro to Programming");
        course = courses.save(newCourse);

        Professor newProfessor = new Professor();
        newProfessor.setFullName("Khaled Mansour");
        newProfessor.setCourseIds(List.of(course.getId()));
        professor = professors.save(newProfessor);
    }

    private Review review(String studentHash, ReviewStatus status, int rating, Instant createdAt) {
        Review review = new Review();
        review.setStudentHash(studentHash);
        review.setProfessorId(professor.getId());
        review.setCourseId(course.getId());
        review.setCourseCode(course.getCode());
        review.setCourseName(course.getName());
        review.setRating(rating);
        review.setComment("Lectures are clear and the exams match the sheets.");
        review.setGrade(Grade.A);
        review.setStatus(status);
        review.setCreatedAt(createdAt == null ? Instant.now() : createdAt);
        review.setUpdatedAt(review.getCreatedAt());
        return review;
    }

    @Test
    @DisplayName("the unique constraint blocks a second review for the same student/professor/course")
    void uniqueReviewConstraint() {
        reviews.saveAndFlush(review("hash-abc", ReviewStatus.PUBLISHED, 5, null));

        assertThatThrownBy(() ->
                reviews.saveAndFlush(review("hash-abc", ReviewStatus.PUBLISHED, 4, null)))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(reviews.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("the moderation audit trail round-trips through jsonb")
    void moderationJsonbRoundTrip() {
        Review review = review("hash-abc", ReviewStatus.REJECTED, 1, null);
        review.setModeration(new ModerationResult(
                ModerationVerdict.REJECT, List.of("PROFANITY", "PERSONAL_ATTACK"),
                "Keep it about the teaching.", "Jev choice=REJECT confidence=0.93",
                0.93, "typesafe/jev-1.13-20260917", 41));
        String id = reviews.saveAndFlush(review).getId();

        ModerationResult moderation = reviews.findById(id).orElseThrow().getModeration();

        assertThat(moderation.getVerdict()).isEqualTo(ModerationVerdict.REJECT);
        assertThat(moderation.getFlaggedCategories())
                .containsExactly("PROFANITY", "PERSONAL_ATTACK");
        assertThat(moderation.getStudentFeedback()).isEqualTo("Keep it about the teaching.");
        assertThat(moderation.getConfidence()).isEqualTo(0.93);
        assertThat(moderation.getModel()).isEqualTo("typesafe/jev-1.13-20260917");
        assertThat(moderation.getCheckedAt()).isNotNull();
    }

    @Test
    @DisplayName("published reviews page per professor, newest first, status-filtered")
    void publishedReviewsPage() {
        reviews.saveAll(List.of(
                review("hash-1", ReviewStatus.PUBLISHED, 5, Instant.parse("2026-01-01T10:00:00Z")),
                review("hash-2", ReviewStatus.PUBLISHED, 4, Instant.parse("2026-02-01T10:00:00Z")),
                review("hash-3", ReviewStatus.PUBLISHED, 3, Instant.parse("2026-03-01T10:00:00Z")),
                review("hash-4", ReviewStatus.MANUAL_REVIEW, 1, Instant.parse("2026-04-01T10:00:00Z"))));

        Page<Review> page = reviews.findByProfessorIdAndStatus(
                professor.getId(), ReviewStatus.PUBLISHED,
                PageRequest.of(0, 2, Sort.by(Sort.Direction.DESC, "createdAt")));

        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getContent()).hasSize(2);
        assertThat(page.getContent().get(0).getStudentHash()).isEqualTo("hash-3");
        assertThat(page.getContent().get(1).getStudentHash()).isEqualTo("hash-2");
    }

    @Test
    @DisplayName("expired OTP challenges are purged while live ones stay")
    void expiredChallengesArePurged() {
        Instant now = Instant.now();
        OtpChallenge expired = challenge("a@ju.edu.jo", now.minusSeconds(120));
        OtpChallenge live = challenge("b@ju.edu.jo", now.plusSeconds(600));
        challenges.saveAll(List.of(expired, live));

        otpService.purgeExpiredChallenges();

        assertThat(challenges.findAll()).extracting(OtpChallenge::getId)
                .containsExactly(live.getId());
    }

    private OtpChallenge challenge(String email, Instant expiresAt) {
        OtpChallenge challenge = new OtpChallenge();
        challenge.setEmail(email);
        challenge.setCodeHash("hash");
        challenge.setCreatedAt(Instant.now());
        challenge.setCodeExpiresAt(Instant.now().plusSeconds(600));
        challenge.setExpiresAt(expiresAt);
        return challenge;
    }
}

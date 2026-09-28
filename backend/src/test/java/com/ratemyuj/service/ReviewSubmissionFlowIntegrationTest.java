package com.ratemyuj.service;

import com.ratemyuj.PostgresIntegrationTest;
import com.ratemyuj.domain.Course;
import com.ratemyuj.domain.Grade;
import com.ratemyuj.domain.ModerationResult;
import com.ratemyuj.domain.ModerationVerdict;
import com.ratemyuj.domain.Professor;
import com.ratemyuj.domain.Review;
import com.ratemyuj.domain.ReviewStatus;
import com.ratemyuj.dto.CreateReviewRequest;
import com.ratemyuj.dto.ReviewSubmissionResponse;
import com.ratemyuj.dto.AdminStatusRequest;
import com.ratemyuj.exception.ApiException;
import com.ratemyuj.moderation.ModerationService;
import com.ratemyuj.repository.CourseRepository;
import com.ratemyuj.repository.ProfessorRepository;
import com.ratemyuj.repository.ReviewRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class ReviewSubmissionFlowIntegrationTest extends PostgresIntegrationTest {

    private static final String MODEL = "typesafe/jev-1.13-20260917";

    @Autowired private ReviewService service;
    @Autowired private AdminService adminService;
    @Autowired private ReviewRepository reviews;
    @Autowired private ProfessorRepository professors;
    @Autowired private CourseRepository courses;
    @MockBean private ModerationService moderation;

    private Professor professor;
    private Course course;

    @BeforeEach
    void setUp() {
        reviews.deleteAll();
        professors.deleteAll();
        courses.deleteAll();

        Course newCourse = new Course();
        newCourse.setCode("CS101");
        newCourse.setName("Intro to Programming");
        course = courses.save(newCourse);

        Professor newProfessor = new Professor();
        newProfessor.setFullName("Khaled Mansour");
        newProfessor.setCourseIds(List.of(course.getId()));
        professor = professors.save(newProfessor);
    }

    private CreateReviewRequest request(int rating) {
        return new CreateReviewRequest(professor.getId(), course.getId(), rating,
                "Lectures are clear and the exams match the sheets.", Grade.A, 3, true);
    }

    private ModerationResult verdict(ModerationVerdict verdict, List<String> categories, String feedback) {
        return new ModerationResult(verdict, categories, feedback, "reason", 0.95, MODEL, 30);
    }

    @Test
    @DisplayName("an approved review is persisted as PUBLISHED and updates professor stats")
    void approvedReviewIsPersisted() {
        when(moderation.moderate(anyString(), anyString(), anyString()))
                .thenReturn(verdict(ModerationVerdict.APPROVE, List.of(), null));

        ReviewSubmissionResponse response = service.submit("hash-abc", request(5));

        assertThat(response.outcome()).isEqualTo(ReviewStatus.PUBLISHED);
        Review stored = reviews.findById(response.reviewId()).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(ReviewStatus.PUBLISHED);
        assertThat(stored.getModeration().getVerdict()).isEqualTo(ModerationVerdict.APPROVE);

        Professor updated = professors.findById(professor.getId()).orElseThrow();
        assertThat(updated.getReviewCount()).isEqualTo(1);
        assertThat(updated.getRatingSum()).isEqualTo(5);
        assertThat(updated.getAvgRating()).isEqualTo(5.0);
    }

    @Test
    @DisplayName("a rejected review is revised in place — same row, no duplicate")
    void rejectedReviewIsReusedOnResubmit() {
        when(moderation.moderate(anyString(), anyString(), anyString()))
                .thenReturn(verdict(ModerationVerdict.REJECT, List.of("PERSONAL_ATTACK"),
                        "Focus on the teaching."));

        ReviewSubmissionResponse rejected = service.submit("hash-abc", request(1));
        assertThat(rejected.outcome()).isEqualTo(ReviewStatus.REJECTED);

        when(moderation.moderate(anyString(), anyString(), anyString()))
                .thenReturn(verdict(ModerationVerdict.APPROVE, List.of(), null));

        ReviewSubmissionResponse published = service.submit("hash-abc", request(5));

        assertThat(published.outcome()).isEqualTo(ReviewStatus.PUBLISHED);
        assertThat(published.reviewId()).isEqualTo(rejected.reviewId());
        assertThat(reviews.count()).isEqualTo(1);

        Review stored = reviews.findById(published.reviewId()).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(ReviewStatus.PUBLISHED);
        assertThat(stored.getModeration().getFlaggedCategories()).isEmpty();
        assertThat(stored.getRating()).isEqualTo(5);
    }

    @Test
    @DisplayName("an already-published review blocks a duplicate submit with 409")
    void duplicateSubmitConflicts() {
        when(moderation.moderate(anyString(), anyString(), anyString()))
                .thenReturn(verdict(ModerationVerdict.APPROVE, List.of(), null));
        service.submit("hash-abc", request(5));

        assertThatThrownBy(() -> service.submit("hash-abc", request(4)))
                .isInstanceOfSatisfying(ApiException.class, error ->
                        assertThat(error.getStatus()).isEqualTo(HttpStatus.CONFLICT));
        assertThat(reviews.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("an escalated review lands in MANUAL_REVIEW without touching stats")
    void escalatedReviewIsHeld() {
        when(moderation.moderate(anyString(), anyString(), anyString()))
                .thenReturn(verdict(ModerationVerdict.ESCALATE, List.of(), "Being checked."));

        ReviewSubmissionResponse response = service.submit("hash-abc", request(3));

        assertThat(response.outcome()).isEqualTo(ReviewStatus.MANUAL_REVIEW);
        Professor untouched = professors.findById(professor.getId()).orElseThrow();
        assertThat(untouched.getReviewCount()).isZero();
    }

    @Test
    @DisplayName("an admin publishes a held review; its rating lands on stats exactly once")
    void adminPublishesHeldReview() {
        when(moderation.moderate(anyString(), anyString(), anyString()))
                .thenReturn(verdict(ModerationVerdict.ESCALATE, List.of(), "Being checked."));
        ReviewSubmissionResponse held = service.submit("hash-abc", request(4));

        var published = adminService.changeStatus(held.reviewId(),
                new AdminStatusRequest(ReviewStatus.PUBLISHED));
        assertThat(published.status()).isEqualTo(ReviewStatus.PUBLISHED);

        adminService.changeStatus(held.reviewId(), new AdminStatusRequest(ReviewStatus.PUBLISHED));

        Professor updated = professors.findById(professor.getId()).orElseThrow();
        assertThat(updated.getReviewCount()).isEqualTo(1);
        assertThat(updated.getAvgRating()).isEqualTo(4.0);
    }
}

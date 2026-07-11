package com.ratemyuj.service;

import com.ratemyuj.domain.*;
import com.ratemyuj.dto.CreateReviewRequest;
import com.ratemyuj.dto.ReviewSubmissionResponse;
import com.ratemyuj.exception.ApiException;
import com.ratemyuj.moderation.ModerationService;
import com.ratemyuj.repository.CourseRepository;
import com.ratemyuj.repository.ProfessorRepository;
import com.ratemyuj.repository.ReviewRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    private static final String STUDENT = "hash-abc";
    private static final String OTHER_STUDENT = "hash-xyz";

    @Mock private ReviewRepository reviews;
    @Mock private ProfessorRepository professors;
    @Mock private CourseRepository courses;
    @Mock private ModerationService moderation;
    @Mock private ProfessorStatsService stats;

    @InjectMocks private ReviewService service;

    private Professor professor;
    private Course course;

    @BeforeEach
    void setUp() {
        professor = new Professor();
        professor.setId("p1");
        professor.setFullName("Khaled Mansour");
        professor.setCourseIds(List.of("c1"));

        course = new Course();
        course.setId("c1");
        course.setCode("CS101");
        course.setName("Intro to Programming");
    }

    private CreateReviewRequest validRequest() {
        return new CreateReviewRequest("p1", "c1", 5,
                "Lectures are clear and the exams match the sheets.", Grade.A, 3, true);
    }

    private void stubHappyLookups() {
        when(professors.findById("p1")).thenReturn(Optional.of(professor));
        when(courses.findById("c1")).thenReturn(Optional.of(course));
        when(reviews.findByStudentHashAndProfessorIdAndCourseId(STUDENT, "p1", "c1"))
                .thenReturn(Optional.empty());
        when(reviews.save(any(Review.class))).thenAnswer(inv -> {
            Review r = inv.getArgument(0);
            if (r.getId() == null) r.setId("rev-1");
            return r;
        });
    }

    private ModerationResult verdict(ModerationVerdict v, String feedback, List<String> cats) {
        return new ModerationResult(v, cats, feedback, "internal", 0.95, "test/model", 42);
    }

    @Nested
    @DisplayName("submission outcomes")
    class SubmissionOutcomes {

        @Test
        @DisplayName("APPROVE publishes the review and updates professor stats")
        void approvePublishes() {
            stubHappyLookups();
            when(moderation.moderate(anyString(), eq("Khaled Mansour"), eq("CS101")))
                    .thenReturn(verdict(ModerationVerdict.APPROVE, null, List.of()));

            ReviewSubmissionResponse response = service.submit(STUDENT, validRequest());

            assertThat(response.outcome()).isEqualTo(ReviewStatus.PUBLISHED);
            assertThat(response.feedback()).isNull();
            verify(stats).applyNewRating("p1", 5);

            ArgumentCaptor<Review> saved = ArgumentCaptor.forClass(Review.class);
            verify(reviews, atLeast(2)).save(saved.capture());
            Review last = saved.getValue();
            assertThat(last.getStatus()).isEqualTo(ReviewStatus.PUBLISHED);
            assertThat(last.getCourseCode()).isEqualTo("CS101");
            assertThat(last.getModeration().getVerdict()).isEqualTo(ModerationVerdict.APPROVE);
        }

        @Test
        @DisplayName("REJECT returns feedback + categories and never touches stats")
        void rejectReturnsFeedback() {
            stubHappyLookups();
            when(moderation.moderate(anyString(), anyString(), anyString()))
                    .thenReturn(verdict(ModerationVerdict.REJECT,
                            "Keep it about the teaching.", List.of("PERSONAL_ATTACK")));

            ReviewSubmissionResponse response = service.submit(STUDENT, validRequest());

            assertThat(response.outcome()).isEqualTo(ReviewStatus.REJECTED);
            assertThat(response.feedback()).isEqualTo("Keep it about the teaching.");
            assertThat(response.flaggedCategories()).containsExactly("PERSONAL_ATTACK");
            verifyNoInteractions(stats);
        }

        @Test
        @DisplayName("ESCALATE parks the review in MANUAL_REVIEW without publishing")
        void escalateGoesToManualReview() {
            stubHappyLookups();
            when(moderation.moderate(anyString(), anyString(), anyString()))
                    .thenReturn(verdict(ModerationVerdict.ESCALATE, "Being checked.", List.of()));

            ReviewSubmissionResponse response = service.submit(STUDENT, validRequest());

            assertThat(response.outcome()).isEqualTo(ReviewStatus.MANUAL_REVIEW);
            verifyNoInteractions(stats);

            ArgumentCaptor<Review> saved = ArgumentCaptor.forClass(Review.class);
            verify(reviews, atLeast(2)).save(saved.capture());
            assertThat(saved.getValue().getStatus()).isEqualTo(ReviewStatus.MANUAL_REVIEW);
        }
    }

    @Nested
    @DisplayName("one review per student per professor per course")
    class UniquenessRules {

        @Test
        @DisplayName("an existing PUBLISHED review blocks with 409")
        void publishedBlocks() {
            when(professors.findById("p1")).thenReturn(Optional.of(professor));
            when(courses.findById("c1")).thenReturn(Optional.of(course));
            Review existing = new Review();
            existing.setStatus(ReviewStatus.PUBLISHED);
            when(reviews.findByStudentHashAndProfessorIdAndCourseId(STUDENT, "p1", "c1"))
                    .thenReturn(Optional.of(existing));

            assertThatThrownBy(() -> service.submit(STUDENT, validRequest()))
                    .isInstanceOf(ApiException.class)
                    .satisfies(e -> assertThat(((ApiException) e).getStatus())
                            .isEqualTo(HttpStatus.CONFLICT));
            verifyNoInteractions(moderation);
        }

        @Test
        @DisplayName("a MANUAL_REVIEW attempt also blocks — no double submissions while held")
        void manualReviewBlocks() {
            when(professors.findById("p1")).thenReturn(Optional.of(professor));
            when(courses.findById("c1")).thenReturn(Optional.of(course));
            Review existing = new Review();
            existing.setStatus(ReviewStatus.MANUAL_REVIEW);
            when(reviews.findByStudentHashAndProfessorIdAndCourseId(STUDENT, "p1", "c1"))
                    .thenReturn(Optional.of(existing));

            assertThatThrownBy(() -> service.submit(STUDENT, validRequest()))
                    .isInstanceOf(ApiException.class);
        }

        @Test
        @DisplayName("a REJECTED review is revised in place — same id, original createdAt kept")
        void rejectedSlotIsReused() {
            when(professors.findById("p1")).thenReturn(Optional.of(professor));
            when(courses.findById("c1")).thenReturn(Optional.of(course));

            Instant originalCreation = Instant.parse("2026-06-01T10:00:00Z");
            Review rejected = new Review();
            rejected.setId("rev-old");
            rejected.setStudentHash(STUDENT);
            rejected.setProfessorId("p1");
            rejected.setCourseId("c1");
            rejected.setStatus(ReviewStatus.REJECTED);
            rejected.setCreatedAt(originalCreation);
            when(reviews.findByStudentHashAndProfessorIdAndCourseId(STUDENT, "p1", "c1"))
                    .thenReturn(Optional.of(rejected));
            when(reviews.save(any(Review.class))).thenAnswer(inv -> inv.getArgument(0));
            when(moderation.moderate(anyString(), anyString(), anyString()))
                    .thenReturn(verdict(ModerationVerdict.APPROVE, null, List.of()));

            ReviewSubmissionResponse response = service.submit(STUDENT, validRequest());

            assertThat(response.reviewId()).isEqualTo("rev-old");
            assertThat(response.outcome()).isEqualTo(ReviewStatus.PUBLISHED);
            ArgumentCaptor<Review> saved = ArgumentCaptor.forClass(Review.class);
            verify(reviews, atLeast(1)).save(saved.capture());
            assertThat(saved.getValue().getCreatedAt()).isEqualTo(originalCreation);
        }

        @Test
        @DisplayName("a lost insert race (DuplicateKeyException) surfaces as 409, not 500")
        void duplicateKeyRaceBecomesConflict() {
            when(professors.findById("p1")).thenReturn(Optional.of(professor));
            when(courses.findById("c1")).thenReturn(Optional.of(course));
            when(reviews.findByStudentHashAndProfessorIdAndCourseId(STUDENT, "p1", "c1"))
                    .thenReturn(Optional.empty());
            when(reviews.save(any(Review.class)))
                    .thenThrow(new DuplicateKeyException("E11000 duplicate key"));

            assertThatThrownBy(() -> service.submit(STUDENT, validRequest()))
                    .isInstanceOf(ApiException.class)
                    .satisfies(e -> assertThat(((ApiException) e).getStatus())
                            .isEqualTo(HttpStatus.CONFLICT));
            verifyNoInteractions(moderation);
        }
    }

    @Nested
    @DisplayName("input validation against the catalog")
    class CatalogValidation {

        @Test
        @DisplayName("unknown professor -> 404")
        void professorNotFound() {
            when(professors.findById("p1")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.submit(STUDENT, validRequest()))
                    .isInstanceOf(ApiException.class)
                    .satisfies(e -> assertThat(((ApiException) e).getStatus())
                            .isEqualTo(HttpStatus.NOT_FOUND));
        }

        @Test
        @DisplayName("unknown course -> 404")
        void courseNotFound() {
            when(professors.findById("p1")).thenReturn(Optional.of(professor));
            when(courses.findById("c1")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.submit(STUDENT, validRequest()))
                    .isInstanceOf(ApiException.class)
                    .satisfies(e -> assertThat(((ApiException) e).getStatus())
                            .isEqualTo(HttpStatus.NOT_FOUND));
        }

        @Test
        @DisplayName("course not taught by this professor -> 400")
        void courseNotTaughtByProfessor() {
            professor.setCourseIds(List.of("other-course"));
            when(professors.findById("p1")).thenReturn(Optional.of(professor));
            when(courses.findById("c1")).thenReturn(Optional.of(course));

            assertThatThrownBy(() -> service.submit(STUDENT, validRequest()))
                    .isInstanceOf(ApiException.class)
                    .satisfies(e -> assertThat(((ApiException) e).getStatus())
                            .isEqualTo(HttpStatus.BAD_REQUEST));
            verifyNoInteractions(moderation);
        }
    }

    @Nested
    @DisplayName("deleting a review")
    class Deletion {

        private Review published() {
            Review r = new Review();
            r.setId("rev-1");
            r.setStudentHash(STUDENT);
            r.setProfessorId("p1");
            r.setRating(4);
            r.setStatus(ReviewStatus.PUBLISHED);
            return r;
        }

        @Test
        @DisplayName("owner deleting a PUBLISHED review rolls back professor stats")
        void ownerDeletePublished() {
            when(reviews.findById("rev-1")).thenReturn(Optional.of(published()));

            service.delete("rev-1", STUDENT);

            verify(reviews).delete(any(Review.class));
            verify(stats).removeRating("p1", 4);
        }

        @Test
        @DisplayName("deleting a non-published review does not touch stats")
        void deleteRejectedSkipsStats() {
            Review r = published();
            r.setStatus(ReviewStatus.REJECTED);
            when(reviews.findById("rev-1")).thenReturn(Optional.of(r));

            service.delete("rev-1", STUDENT);

            verify(reviews).delete(any(Review.class));
            verifyNoInteractions(stats);
        }

        @Test
        @DisplayName("someone else's review cannot be deleted")
        void nonOwnerCannotDelete() {
            when(reviews.findById("rev-1")).thenReturn(Optional.of(published()));

            assertThatThrownBy(() -> service.delete("rev-1", OTHER_STUDENT))
                    .isInstanceOf(ApiException.class);
            verify(reviews, never()).delete(any(Review.class));
            verifyNoInteractions(stats);
        }
    }
}

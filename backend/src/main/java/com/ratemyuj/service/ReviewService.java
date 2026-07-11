package com.ratemyuj.service;

import com.ratemyuj.domain.*;
import com.ratemyuj.dto.CreateReviewRequest;
import com.ratemyuj.dto.ReviewResponse;
import com.ratemyuj.dto.ReviewSubmissionResponse;
import com.ratemyuj.exception.ApiException;
import com.ratemyuj.moderation.ModerationService;
import com.ratemyuj.repository.CourseRepository;
import com.ratemyuj.repository.ProfessorRepository;
import com.ratemyuj.repository.ReviewRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Submission flow:
 *   1. validate professor + course, and that the course belongs to the professor
 *   2. enforce one review per student per professor per course
 *      (a REJECTED review does not block — the student revises the same slot)
 *   3. save as PENDING_MODERATION
 *   4. run AI moderation synchronously (student gets an instant verdict)
 *   5. APPROVE -> PUBLISHED + professor stats updated
 *      REJECT  -> REJECTED, feedback returned so the student can fix and resubmit
 *      ESCALATE-> MANUAL_REVIEW, admin queue
 */
@Service
public class ReviewService {

    private final ReviewRepository reviews;
    private final ProfessorRepository professors;
    private final CourseRepository courses;
    private final ModerationService moderation;
    private final ProfessorStatsService stats;

    public ReviewService(ReviewRepository reviews, ProfessorRepository professors,
                         CourseRepository courses, ModerationService moderation,
                         ProfessorStatsService stats) {
        this.reviews = reviews;
        this.professors = professors;
        this.courses = courses;
        this.moderation = moderation;
        this.stats = stats;
    }

    public ReviewSubmissionResponse submit(String studentHash, CreateReviewRequest request) {
        Professor professor = professors.findById(request.professorId())
                .orElseThrow(() -> ApiException.notFound("Professor not found"));
        Course course = courses.findById(request.courseId())
                .orElseThrow(() -> ApiException.notFound("Course not found"));

        if (professor.getCourseIds() == null || !professor.getCourseIds().contains(course.getId())) {
            throw ApiException.badRequest("This professor does not teach the selected course");
        }

        Optional<Review> existing = reviews.findByStudentHashAndProfessorIdAndCourseId(
                studentHash, request.professorId(), request.courseId());

        Review review;
        if (existing.isPresent()) {
            Review found = existing.get();
            if (found.getStatus() != ReviewStatus.REJECTED) {
                throw ApiException.conflict("You have already reviewed this professor for this course");
            }
            review = found; // rejected review — reuse the slot for the revised attempt
        } else {
            review = new Review();
            review.setStudentHash(studentHash);
            review.setProfessorId(professor.getId());
            review.setCourseId(course.getId());
            review.setCreatedAt(Instant.now());
        }

        review.setCourseCode(course.getCode());
        review.setCourseName(course.getName());
        review.setRating(request.rating());
        review.setComment(request.comment().trim());
        review.setGrade(request.grade());
        review.setDifficulty(request.difficulty());
        review.setWouldTakeAgain(request.wouldTakeAgain());
        review.setStatus(ReviewStatus.PENDING_MODERATION);
        review.setUpdatedAt(Instant.now());
        try {
            review = reviews.save(review);
        } catch (DuplicateKeyException e) {
            // lost a race with a concurrent submit from the same student
            throw ApiException.conflict("You have already reviewed this professor for this course");
        }

        ModerationResult result = moderation.moderate(
                review.getComment(), professor.getFullName(), course.getCode());
        review.setModeration(result);

        switch (result.getVerdict()) {
            case APPROVE -> {
                review.setStatus(ReviewStatus.PUBLISHED);
                reviews.save(review);
                stats.applyNewRating(professor.getId(), review.getRating());
                return new ReviewSubmissionResponse(
                        review.getId(), ReviewStatus.PUBLISHED, null, List.of());
            }
            case REJECT -> {
                review.setStatus(ReviewStatus.REJECTED);
                reviews.save(review);
                return new ReviewSubmissionResponse(
                        review.getId(), ReviewStatus.REJECTED,
                        result.getStudentFeedback(), result.getFlaggedCategories());
            }
            default -> {
                review.setStatus(ReviewStatus.MANUAL_REVIEW);
                reviews.save(review);
                return new ReviewSubmissionResponse(
                        review.getId(), ReviewStatus.MANUAL_REVIEW,
                        "Your review is being checked and will appear once approved.",
                        List.of());
            }
        }
    }

    public Page<ReviewResponse> publishedReviews(String professorId, String requesterHash, Pageable pageable) {
        return reviews.findByProfessorIdAndStatus(professorId, ReviewStatus.PUBLISHED, pageable)
                .map(r -> new ReviewResponse(
                        r.getId(), r.getRating(), r.getComment(), r.getGrade(),
                        r.getCourseCode(), r.getCourseName(), r.getDifficulty(),
                        r.getWouldTakeAgain(), r.getCreatedAt(),
                        r.getStudentHash().equals(requesterHash)));
    }

    public void delete(String reviewId, String studentHash) {
        Review review = reviews.findById(reviewId)
                .orElseThrow(() -> ApiException.notFound("Review not found"));
        if (!review.getStudentHash().equals(studentHash)) {
            throw ApiException.badRequest("You can only delete your own review");
        }
        boolean wasPublished = review.getStatus() == ReviewStatus.PUBLISHED;
        reviews.delete(review);
        if (wasPublished) {
            stats.removeRating(review.getProfessorId(), review.getRating());
        }
    }
}

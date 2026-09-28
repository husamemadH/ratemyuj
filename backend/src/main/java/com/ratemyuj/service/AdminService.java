package com.ratemyuj.service;

import com.ratemyuj.domain.ModerationResult;
import com.ratemyuj.domain.Professor;
import com.ratemyuj.domain.Review;
import com.ratemyuj.domain.ReviewStatus;
import com.ratemyuj.dto.AdminReviewResponse;
import com.ratemyuj.dto.AdminStatusRequest;
import com.ratemyuj.exception.ApiException;
import com.ratemyuj.repository.ProfessorRepository;
import com.ratemyuj.repository.ReviewRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Admin queue: review the MANUAL_REVIEW backlog and move reviews between
 * statuses. Publishing a held review applies its rating to professor stats
 * exactly once; hiding/removing a published review rolls it back.
 */
@Service
public class AdminService {

    private static final Set<ReviewStatus> TARGETS = Set.of(
            ReviewStatus.PUBLISHED, ReviewStatus.REJECTED,
            ReviewStatus.HIDDEN, ReviewStatus.REMOVED);

    private final ReviewRepository reviews;
    private final ProfessorRepository professors;
    private final ProfessorStatsService stats;

    public AdminService(ReviewRepository reviews, ProfessorRepository professors, ProfessorStatsService stats) {
        this.reviews = reviews;
        this.professors = professors;
        this.stats = stats;
    }

    public Page<AdminReviewResponse> list(ReviewStatus status, Pageable pageable) {
        Page<Review> page = status == null
                ? reviews.findAll(pageable)
                : reviews.findByStatus(status, pageable);

        Map<String, String> namesById = professors.findAllById(
                        page.getContent().stream().map(Review::getProfessorId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(Professor::getId, Professor::getFullName, (left, right) -> left));

        return page.map(review -> toResponse(review, namesById.get(review.getProfessorId())));
    }

    @Transactional
    public AdminReviewResponse changeStatus(String reviewId, AdminStatusRequest request) {
        ReviewStatus target = request.status();
        if (!TARGETS.contains(target)) {
            throw ApiException.badRequest("Unsupported target status: " + target);
        }

        Review review = reviews.findById(reviewId)
                .orElseThrow(() -> ApiException.notFound("Review not found"));
        ReviewStatus previous = review.getStatus();
        if (previous == target) {
            return toResponse(review, professorName(review.getProfessorId()));
        }

        review.setStatus(target);
        review.setUpdatedAt(Instant.now());
        reviews.save(review);

        boolean wasPublished = previous == ReviewStatus.PUBLISHED;
        boolean nowPublished = target == ReviewStatus.PUBLISHED;
        if (nowPublished && !wasPublished) {
            stats.applyNewRating(review.getProfessorId(), review.getRating());
        } else if (!nowPublished && wasPublished) {
            stats.removeRating(review.getProfessorId(), review.getRating());
        }

        return toResponse(review, professorName(review.getProfessorId()));
    }

    private String professorName(String professorId) {
        return professors.findById(professorId).map(Professor::getFullName).orElse(null);
    }

    private AdminReviewResponse toResponse(Review review, String professorName) {
        ModerationResult moderation = review.getModeration();
        AdminReviewResponse.AdminModerationResponse moderationResponse = moderation == null ? null
                : new AdminReviewResponse.AdminModerationResponse(
                        moderation.getVerdict(),
                        moderation.getFlaggedCategories() == null ? List.of() : moderation.getFlaggedCategories(),
                        moderation.getStudentFeedback(),
                        moderation.getInternalReason(),
                        moderation.getConfidence(),
                        moderation.getModel(),
                        moderation.getLatencyMs(),
                        moderation.getCheckedAt());

        return new AdminReviewResponse(
                review.getId(),
                review.getProfessorId(),
                professorName,
                review.getCourseCode(),
                review.getCourseName(),
                review.getStudentHash(),
                review.getRating(),
                review.getComment(),
                review.getGrade() == null ? null : review.getGrade().name(),
                review.getDifficulty(),
                review.getWouldTakeAgain(),
                review.getStatus(),
                moderationResponse,
                review.getCreatedAt(),
                review.getUpdatedAt());
    }
}

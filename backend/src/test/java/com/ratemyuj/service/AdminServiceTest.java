package com.ratemyuj.service;

import com.ratemyuj.domain.Review;
import com.ratemyuj.domain.ReviewStatus;
import com.ratemyuj.dto.AdminStatusRequest;
import com.ratemyuj.exception.ApiException;
import com.ratemyuj.repository.ProfessorRepository;
import com.ratemyuj.repository.ReviewRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock private ReviewRepository reviews;
    @Mock private ProfessorRepository professors;
    @Mock private ProfessorStatsService stats;
    @InjectMocks private AdminService service;

    private Review held() {
        Review review = new Review();
        review.setId("rev-1");
        review.setProfessorId("p1");
        review.setRating(5);
        review.setStatus(ReviewStatus.MANUAL_REVIEW);
        return review;
    }

    private void stubFound(Review review) {
        when(reviews.findById("rev-1")).thenReturn(Optional.of(review));
        when(reviews.save(any(Review.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("publishing a held review applies its rating to stats")
    void publishHeldAppliesStats() {
        Review review = held();
        stubFound(review);

        var response = service.changeStatus("rev-1", new AdminStatusRequest(ReviewStatus.PUBLISHED));

        assertThat(response.status()).isEqualTo(ReviewStatus.PUBLISHED);
        verify(stats).applyNewRating("p1", 5);
        verify(stats, never()).removeRating(anyString(), anyInt());
    }

    @Test
    @DisplayName("hiding a published review rolls its rating back out of stats")
    void hidePublishedRemovesStats() {
        Review review = held();
        review.setStatus(ReviewStatus.PUBLISHED);
        stubFound(review);

        service.changeStatus("rev-1", new AdminStatusRequest(ReviewStatus.HIDDEN));

        verify(stats).removeRating("p1", 5);
        verify(stats, never()).applyNewRating(anyString(), anyInt());
    }

    @Test
    @DisplayName("re-publishing a hidden review applies its rating again")
    void republishHiddenAppliesStats() {
        Review review = held();
        review.setStatus(ReviewStatus.HIDDEN);
        stubFound(review);

        service.changeStatus("rev-1", new AdminStatusRequest(ReviewStatus.PUBLISHED));

        verify(stats).applyNewRating("p1", 5);
    }

    @Test
    @DisplayName("a same-status change is a no-op that never touches stats")
    void sameStatusNoop() {
        Review review = held();
        review.setStatus(ReviewStatus.PUBLISHED);
        when(reviews.findById("rev-1")).thenReturn(Optional.of(review));

        service.changeStatus("rev-1", new AdminStatusRequest(ReviewStatus.PUBLISHED));

        verifyNoInteractions(stats);
        verify(reviews, never()).save(any(Review.class));
    }

    @Test
    @DisplayName("PENDING_MODERATION and MANUAL_REVIEW are not valid admin targets")
    void unsupportedTarget() {
        assertThatThrownBy(() -> service.changeStatus("rev-1", new AdminStatusRequest(ReviewStatus.MANUAL_REVIEW)))
                .isInstanceOfSatisfying(ApiException.class, error ->
                        assertThat(error.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> service.changeStatus("rev-1", new AdminStatusRequest(ReviewStatus.PENDING_MODERATION)))
                .isInstanceOf(ApiException.class);
        verifyNoInteractions(reviews, stats);
    }

    @Test
    @DisplayName("an unknown review id is a 404")
    void unknownReview() {
        when(reviews.findById("rev-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.changeStatus("rev-1", new AdminStatusRequest(ReviewStatus.PUBLISHED)))
                .isInstanceOfSatisfying(ApiException.class, error ->
                        assertThat(error.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        verifyNoInteractions(stats);
    }
}

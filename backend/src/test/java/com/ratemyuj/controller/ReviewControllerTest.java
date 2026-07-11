package com.ratemyuj.controller;

import com.ratemyuj.domain.ReviewStatus;
import com.ratemyuj.dto.CreateReviewRequest;
import com.ratemyuj.dto.ReviewSubmissionResponse;
import com.ratemyuj.exception.ApiException;
import com.ratemyuj.service.ReviewService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ReviewController.class)
class ReviewControllerTest {

    @Autowired private MockMvc mvc;
    @MockBean private ReviewService reviewService;

    private static final String VALID_BODY = """
            {"professorId":"p1","courseId":"c1","rating":5,
             "comment":"Lectures are clear and the exams match the sheets.",
             "grade":"A","difficulty":3,"wouldTakeAgain":true}""";

    @Nested
    @DisplayName("POST /api/reviews")
    class Submit {

        @Test
        @DisplayName("valid submission returns the moderation outcome as JSON")
        void validSubmission() throws Exception {
            when(reviewService.submit(eq("hash-abc"), any(CreateReviewRequest.class)))
                    .thenReturn(new ReviewSubmissionResponse(
                            "rev-1", ReviewStatus.PUBLISHED, null, List.of()));

            mvc.perform(post("/api/reviews")
                            .header("X-Student-Hash", "hash-abc")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_BODY))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.reviewId").value("rev-1"))
                    .andExpect(jsonPath("$.outcome").value("PUBLISHED"));
        }

        @Test
        @DisplayName("rejected outcome carries feedback and flagged categories")
        void rejectedOutcome() throws Exception {
            when(reviewService.submit(anyString(), any(CreateReviewRequest.class)))
                    .thenReturn(new ReviewSubmissionResponse(
                            "rev-1", ReviewStatus.REJECTED,
                            "Keep it about the teaching.", List.of("PERSONAL_ATTACK")));

            mvc.perform(post("/api/reviews")
                            .header("X-Student-Hash", "hash-abc")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_BODY))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.outcome").value("REJECTED"))
                    .andExpect(jsonPath("$.feedback").value("Keep it about the teaching."))
                    .andExpect(jsonPath("$.flaggedCategories[0]").value("PERSONAL_ATTACK"));
        }

        @Test
        @DisplayName("a comment under 20 chars fails validation with a helpful message")
        void tooShortComment() throws Exception {
            String body = VALID_BODY.replace(
                    "Lectures are clear and the exams match the sheets.", "meh");

            mvc.perform(post("/api/reviews")
                            .header("X-Student-Hash", "hash-abc")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value(
                            org.hamcrest.Matchers.containsString("comment")));
        }

        @Test
        @DisplayName("rating outside 1..5 is rejected")
        void ratingOutOfRange() throws Exception {
            mvc.perform(post("/api/reviews")
                            .header("X-Student-Hash", "hash-abc")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_BODY.replace("\"rating\":5", "\"rating\":9")))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("an unknown grade enum value is a 400, not a 500")
        void invalidEnumValue() throws Exception {
            mvc.perform(post("/api/reviews")
                            .header("X-Student-Hash", "hash-abc")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_BODY.replace("\"grade\":\"A\"", "\"grade\":\"Z\"")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").exists());
        }

        @Test
        @DisplayName("missing student identity header is a 400")
        void missingHeader() throws Exception {
            mvc.perform(post("/api/reviews")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_BODY))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("service conflicts surface as 409 through the exception handler")
        void conflictFromService() throws Exception {
            when(reviewService.submit(anyString(), any(CreateReviewRequest.class)))
                    .thenThrow(ApiException.conflict("You have already reviewed this professor for this course"));

            mvc.perform(post("/api/reviews")
                            .header("X-Student-Hash", "hash-abc")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_BODY))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error").exists());
        }
    }

    @Nested
    @DisplayName("GET /api/professors/{id}/reviews")
    class ListReviews {

        @Test
        @DisplayName("page size is clamped to 50 and page floor is 0")
        void pagingIsClamped() throws Exception {
            when(reviewService.publishedReviews(anyString(), anyString(), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of()));

            mvc.perform(get("/api/professors/p1/reviews")
                            .param("page", "-3")
                            .param("size", "500"))
                    .andExpect(status().isOk());

            ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
            verify(reviewService).publishedReviews(eq("p1"), eq(""), pageable.capture());
            assertThat(pageable.getValue().getPageNumber()).isEqualTo(0);
            assertThat(pageable.getValue().getPageSize()).isEqualTo(50);
        }

        @Test
        @DisplayName("anonymous listing works — the identity header is optional here")
        void anonymousListing() throws Exception {
            when(reviewService.publishedReviews(anyString(), anyString(), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of()));

            mvc.perform(get("/api/professors/p1/reviews"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray());
        }
    }

    @Nested
    @DisplayName("DELETE /api/reviews/{id}")
    class Delete {

        @Test
        @DisplayName("owner delete returns 204 with no body")
        void ownerDelete() throws Exception {
            mvc.perform(delete("/api/reviews/rev-1")
                            .header("X-Student-Hash", "hash-abc"))
                    .andExpect(status().isNoContent());

            verify(reviewService).delete("rev-1", "hash-abc");
        }
    }
}

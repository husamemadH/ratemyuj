package com.ratemyuj.controller;

import com.ratemyuj.dto.CreateReviewRequest;
import com.ratemyuj.dto.ReviewResponse;
import com.ratemyuj.dto.ReviewSubmissionResponse;
import com.ratemyuj.service.ReviewService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    /**
     * NOTE: studentHash comes from the authenticated JWT principal in production.
     * The @RequestHeader below is a development placeholder — replace with
     * @AuthenticationPrincipal StudentPrincipal once the OTP/JWT filter is wired.
     */
    @PostMapping("/reviews")
    public ResponseEntity<ReviewSubmissionResponse> submit(
            @RequestHeader("X-Student-Hash") String studentHash,
            @Valid @RequestBody CreateReviewRequest request) {
        return ResponseEntity.ok(reviewService.submit(studentHash, request));
    }

    @GetMapping("/professors/{professorId}/reviews")
    public Page<ReviewResponse> list(
            @PathVariable String professorId,
            @RequestHeader(value = "X-Student-Hash", required = false) String studentHash,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), 50);
        return reviewService.publishedReviews(
                professorId,
                studentHash == null ? "" : studentHash,
                PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt")));
    }

    @DeleteMapping("/reviews/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable String id,
            @RequestHeader("X-Student-Hash") String studentHash) {
        reviewService.delete(id, studentHash);
        return ResponseEntity.noContent().build();
    }
}

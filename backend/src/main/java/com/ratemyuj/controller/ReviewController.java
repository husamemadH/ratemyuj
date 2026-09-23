package com.ratemyuj.controller;

import com.ratemyuj.auth.StudentPrincipal;
import com.ratemyuj.dto.CreateReviewRequest;
import com.ratemyuj.dto.ReviewResponse;
import com.ratemyuj.dto.ReviewSubmissionResponse;
import com.ratemyuj.service.ReviewService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @PostMapping("/reviews")
    public ResponseEntity<ReviewSubmissionResponse> submit(
            @AuthenticationPrincipal StudentPrincipal student,
            @Valid @RequestBody CreateReviewRequest request) {
        return ResponseEntity.ok(reviewService.submit(student.studentHash(), request));
    }

    @GetMapping("/professors/{professorId}/reviews")
    public Page<ReviewResponse> list(
            @PathVariable String professorId,
            @AuthenticationPrincipal StudentPrincipal student,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), 50);
        String studentHash = student == null ? "" : student.studentHash();
        return reviewService.publishedReviews(
                professorId,
                studentHash,
                PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt")));
    }

    @DeleteMapping("/reviews/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable String id,
            @AuthenticationPrincipal StudentPrincipal student) {
        reviewService.delete(id, student.studentHash());
        return ResponseEntity.noContent().build();
    }
}

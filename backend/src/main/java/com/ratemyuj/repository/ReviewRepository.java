package com.ratemyuj.repository;

import com.ratemyuj.domain.Review;
import com.ratemyuj.domain.ReviewStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;

public interface ReviewRepository extends JpaRepository<Review, String> {

    Page<Review> findByProfessorIdAndStatus(String professorId, ReviewStatus status, Pageable pageable);

    Optional<Review> findByStudentHashAndProfessorIdAndCourseId(
            String studentHash, String professorId, String courseId);

    Page<Review> findByStatus(ReviewStatus status, Pageable pageable);

    long countByStudentHashAndUpdatedAtAfter(String studentHash, Instant updatedAt);
}

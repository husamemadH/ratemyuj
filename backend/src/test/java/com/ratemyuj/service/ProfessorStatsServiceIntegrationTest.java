package com.ratemyuj.service;

import com.ratemyuj.PostgresIntegrationTest;
import com.ratemyuj.domain.Professor;
import com.ratemyuj.repository.ProfessorRepository;
import com.ratemyuj.repository.ReviewRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

class ProfessorStatsServiceIntegrationTest extends PostgresIntegrationTest {

    @Autowired private ProfessorStatsService stats;
    @Autowired private ProfessorRepository professors;
    @Autowired private ReviewRepository reviews;

    private String professorId;

    @BeforeEach
    void setUp() {
        reviews.deleteAll();
        professors.deleteAll();
        Professor professor = new Professor();
        professor.setFullName("Khaled Mansour");
        professorId = professors.save(professor).getId();
    }

    private Professor reload() {
        return professors.findById(professorId).orElseThrow();
    }

    @Test
    @DisplayName("a new 5-star rating increments count, sum, bucket, and average")
    void applyNewRating() {
        stats.applyNewRating(professorId, 5);

        Professor professor = reload();
        assertThat(professor.getReviewCount()).isEqualTo(1);
        assertThat(professor.getRatingSum()).isEqualTo(5);
        assertThat(professor.getBreakdown().getFive()).isEqualTo(1);
        assertThat(professor.getAvgRating()).isEqualTo(5.0);
    }

    @Test
    @DisplayName("removing the last rating resets the average to 0.0, never NaN")
    void removeLastRating() {
        stats.applyNewRating(professorId, 4);
        stats.removeRating(professorId, 4);

        Professor professor = reload();
        assertThat(professor.getReviewCount()).isZero();
        assertThat(professor.getRatingSum()).isZero();
        assertThat(professor.getBreakdown().getFour()).isZero();
        assertThat(professor.getAvgRating()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("the average is rounded to one decimal place")
    void averageRoundsToOneDecimal() {
        stats.applyNewRating(professorId, 5);
        stats.applyNewRating(professorId, 4);
        stats.applyNewRating(professorId, 4);

        assertThat(reload().getAvgRating()).isEqualTo(4.3);   // 13 / 3
    }

    @Test
    @DisplayName("replaceRating swaps buckets and adjusts the sum without touching the count")
    void replaceRating() {
        stats.applyNewRating(professorId, 2);
        stats.applyNewRating(professorId, 3);
        stats.applyNewRating(professorId, 3);

        stats.replaceRating(professorId, 2, 5);

        Professor professor = reload();
        assertThat(professor.getReviewCount()).isEqualTo(3);
        assertThat(professor.getRatingSum()).isEqualTo(11);   // 5 + 3 + 3
        assertThat(professor.getBreakdown().getTwo()).isZero();
        assertThat(professor.getBreakdown().getFive()).isEqualTo(1);
        assertThat(professor.getBreakdown().getThree()).isEqualTo(2);
        assertThat(professor.getAvgRating()).isEqualTo(3.7);
    }

    @Test
    @DisplayName("replaceRating with the same value changes nothing")
    void replaceWithSameRating() {
        stats.applyNewRating(professorId, 3);
        stats.replaceRating(professorId, 3, 3);

        assertThat(reload().getRatingSum()).isEqualTo(3);
    }

    @Test
    @DisplayName("an unknown professor id is a silent no-op")
    void missingProfessorIsNoOp() {
        stats.applyNewRating("00000000-0000-0000-0000-000000000000", 5);
        stats.removeRating("00000000-0000-0000-0000-000000000000", 5);
    }

    @Test
    @DisplayName("concurrent ratings never lose an update or persist a stale average")
    void concurrentRatingsAreAtomic() throws Exception {
        int threads = 16;
        int perThread = 5;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        List<Callable<Void>> tasks = new ArrayList<>();
        for (int i = 0; i < threads * perThread; i++) {
            tasks.add(() -> {
                stats.applyNewRating(professorId, 5);
                return null;
            });
        }

        for (Future<Void> future : pool.invokeAll(tasks)) {
            future.get();
        }
        pool.shutdown();

        Professor professor = reload();
        assertThat(professor.getReviewCount()).isEqualTo(threads * perThread);
        assertThat(professor.getRatingSum()).isEqualTo(threads * perThread * 5);
        assertThat(professor.getBreakdown().getFive()).isEqualTo(threads * perThread);
        assertThat(professor.getAvgRating()).isEqualTo(5.0);
    }
}

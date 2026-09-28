package com.ratemyuj.service;

import com.ratemyuj.repository.ProfessorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Maintains the denormalized rating aggregates on the professor row.
 *
 * Concurrency design: every change is one atomic UPDATE that adjusts the
 * counters, the star bucket, and avg_rating together in the database, so
 * concurrent reviews cannot lose an increment or persist a stale average.
 */
@Service
public class ProfessorStatsService {

    private final ProfessorRepository professors;

    public ProfessorStatsService(ProfessorRepository professors) {
        this.professors = professors;
    }

    @Transactional
    public void applyNewRating(String professorId, int rating) {
        professors.applyRatingDelta(professorId, rating, +1);
    }

    @Transactional
    public void removeRating(String professorId, int rating) {
        professors.applyRatingDelta(professorId, rating, -1);
    }

    @Transactional
    public void replaceRating(String professorId, int oldRating, int newRating) {
        if (oldRating == newRating) return;
        professors.replaceRating(professorId, oldRating, newRating);
    }
}

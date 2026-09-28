package com.ratemyuj.repository;

import com.ratemyuj.domain.Professor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;

/**
 * Counter/aggregate updates run as single atomic UPDATE statements, so
 * concurrent reviews cannot lose an increment and avg_rating is always
 * derived from the same snapshot as the counters.
 */
public interface ProfessorRepository extends JpaRepository<Professor, String> {

    Page<Professor> findByActiveTrue(Pageable pageable);

    @Query("""
            SELECT p FROM Professor p
            WHERE p.active = true AND (
                LOWER(p.fullName) LIKE :pattern
                OR LOWER(p.department) LIKE :pattern
                OR LOWER(p.college) LIKE :pattern
                OR EXISTS (
                    SELECT 1 FROM Professor p2 JOIN p2.courseIds cid
                    WHERE p2.id = p.id AND cid IN :courseIds
                )
            )
            """)
    Page<Professor> search(@Param("pattern") String pattern,
                           @Param("courseIds") Collection<String> courseIds,
                           Pageable pageable);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE professors SET
              review_count = review_count + :direction,
              rating_sum = rating_sum + (:rating * :direction),
              breakdown_one = breakdown_one + CASE WHEN :rating = 1 THEN :direction ELSE 0 END,
              breakdown_two = breakdown_two + CASE WHEN :rating = 2 THEN :direction ELSE 0 END,
              breakdown_three = breakdown_three + CASE WHEN :rating = 3 THEN :direction ELSE 0 END,
              breakdown_four = breakdown_four + CASE WHEN :rating = 4 THEN :direction ELSE 0 END,
              breakdown_five = breakdown_five + CASE WHEN :rating = 5 THEN :direction ELSE 0 END,
              avg_rating = COALESCE(ROUND(
                  (rating_sum + (:rating * :direction))::numeric
                  / NULLIF(review_count + :direction, 0), 1), 0),
              updated_at = now()
            WHERE id = :professorId
            """, nativeQuery = true)
    int applyRatingDelta(@Param("professorId") String professorId,
                         @Param("rating") int rating,
                         @Param("direction") int direction);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE professors SET
              rating_sum = rating_sum + (:newRating - :oldRating),
              breakdown_one = breakdown_one
                + CASE WHEN :oldRating = 1 THEN -1 WHEN :newRating = 1 THEN 1 ELSE 0 END,
              breakdown_two = breakdown_two
                + CASE WHEN :oldRating = 2 THEN -1 WHEN :newRating = 2 THEN 1 ELSE 0 END,
              breakdown_three = breakdown_three
                + CASE WHEN :oldRating = 3 THEN -1 WHEN :newRating = 3 THEN 1 ELSE 0 END,
              breakdown_four = breakdown_four
                + CASE WHEN :oldRating = 4 THEN -1 WHEN :newRating = 4 THEN 1 ELSE 0 END,
              breakdown_five = breakdown_five
                + CASE WHEN :oldRating = 5 THEN -1 WHEN :newRating = 5 THEN 1 ELSE 0 END,
              avg_rating = COALESCE(ROUND(
                  (rating_sum + (:newRating - :oldRating))::numeric
                  / NULLIF(review_count, 0), 1), 0),
              updated_at = now()
            WHERE id = :professorId
            """, nativeQuery = true)
    int replaceRating(@Param("professorId") String professorId,
                      @Param("oldRating") int oldRating,
                      @Param("newRating") int newRating);
}

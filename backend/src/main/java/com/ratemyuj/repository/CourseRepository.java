package com.ratemyuj.repository;

import com.ratemyuj.domain.Course;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CourseRepository extends JpaRepository<Course, String> {

    @Query("""
            SELECT c.id FROM Course c
            WHERE c.active = true
              AND (LOWER(c.code) LIKE :pattern OR LOWER(c.name) LIKE :pattern)
            """)
    List<String> findIdsMatching(@Param("pattern") String pattern);
}

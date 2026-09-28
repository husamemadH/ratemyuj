package com.ratemyuj.service;

import com.ratemyuj.domain.Course;
import com.ratemyuj.domain.Professor;
import com.ratemyuj.dto.CourseResponse;
import com.ratemyuj.dto.ProfessorDetailResponse;
import com.ratemyuj.dto.ProfessorSummaryResponse;
import com.ratemyuj.exception.ApiException;
import com.ratemyuj.repository.CourseRepository;
import com.ratemyuj.repository.ProfessorRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Read-only catalog: professor search, detail page, and course lookups. */
@Service
public class ProfessorService {

    private static final String NO_COURSES = "__none__";

    private final ProfessorRepository professors;
    private final CourseRepository courses;

    public ProfessorService(ProfessorRepository professors, CourseRepository courses) {
        this.professors = professors;
        this.courses = courses;
    }

    public Page<ProfessorSummaryResponse> search(String query, Pageable pageable) {
        String q = query == null ? "" : query.trim();
        Page<Professor> page;
        if (q.isEmpty()) {
            page = professors.findByActiveTrue(pageable);
        } else {
            String pattern = "%" + q.toLowerCase(Locale.ROOT) + "%";
            List<String> matchingCourses = courses.findIdsMatching(pattern);
            page = professors.search(pattern, matchingCourses.isEmpty() ? List.of(NO_COURSES) : matchingCourses, pageable);
        }

        Map<String, String> codesById = courseCodes(page.getContent());
        return page.map(professor -> new ProfessorSummaryResponse(
                professor.getId(),
                professor.getTitle(),
                professor.getFullName(),
                professor.getDepartment(),
                professor.getCollege(),
                professor.getPhotoUrl(),
                professor.getAvgRating(),
                professor.getReviewCount(),
                courseIds(professor).stream().map(codesById::get).filter(Objects::nonNull).toList()));
    }

    public ProfessorDetailResponse detail(String professorId) {
        Professor professor = professors.findById(professorId)
                .orElseThrow(() -> ApiException.notFound("Professor not found"));

        Map<String, Course> coursesById = courses.findAllById(courseIds(professor)).stream()
                .collect(Collectors.toMap(Course::getId, Function.identity()));

        List<CourseResponse> professorCourses = courseIds(professor).stream()
                .map(coursesById::get)
                .filter(Objects::nonNull)
                .map(course -> new CourseResponse(course.getId(), course.getCode(), course.getName(),
                        course.getDepartment(), course.getCreditHours()))
                .toList();

        Professor.RatingBreakdown breakdown = professor.getBreakdown();
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("1", breakdown == null ? 0 : breakdown.getOne());
        counts.put("2", breakdown == null ? 0 : breakdown.getTwo());
        counts.put("3", breakdown == null ? 0 : breakdown.getThree());
        counts.put("4", breakdown == null ? 0 : breakdown.getFour());
        counts.put("5", breakdown == null ? 0 : breakdown.getFive());

        return new ProfessorDetailResponse(
                professor.getId(),
                professor.getTitle(),
                professor.getFullName(),
                professor.getDepartment(),
                professor.getCollege(),
                professor.getPhotoUrl(),
                professor.getAvgRating(),
                professor.getReviewCount(),
                counts,
                professorCourses);
    }

    private Map<String, String> courseCodes(List<Professor> professorPage) {
        List<String> ids = new ArrayList<>();
        for (Professor professor : professorPage) {
            ids.addAll(courseIds(professor));
        }
        return courses.findAllById(ids).stream()
                .collect(Collectors.toMap(Course::getId, Course::getCode, (left, right) -> left));
    }

    private static List<String> courseIds(Professor professor) {
        return professor.getCourseIds() == null ? List.of() : professor.getCourseIds();
    }
}

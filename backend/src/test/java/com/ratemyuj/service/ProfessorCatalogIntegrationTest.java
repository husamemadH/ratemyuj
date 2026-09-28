package com.ratemyuj.service;

import com.ratemyuj.PostgresIntegrationTest;
import com.ratemyuj.domain.Course;
import com.ratemyuj.domain.Professor;
import com.ratemyuj.exception.ApiException;
import com.ratemyuj.repository.CourseRepository;
import com.ratemyuj.repository.ProfessorRepository;
import com.ratemyuj.repository.ReviewRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProfessorCatalogIntegrationTest extends PostgresIntegrationTest {

    @Autowired private ProfessorService catalog;
    @Autowired private ProfessorRepository professors;
    @Autowired private CourseRepository courses;
    @Autowired private ReviewRepository reviews;

    private Course cs101;
    private Course cs211;
    private Course math101;
    private Professor khaled;
    private Professor rania;

    @BeforeEach
    void setUp() {
        reviews.deleteAll();
        professors.deleteAll();
        courses.deleteAll();

        cs101 = course("CS101", "Intro to Programming", "Computer Science");
        cs211 = course("CS211", "Data Structures", "Computer Science");
        math101 = course("MATH101", "Calculus 1", "Mathematics");

        khaled = professor("Khaled Mansour", "Computer Science", List.of(cs101.getId(), cs211.getId()));
        rania = professor("Rania Al-Tal", "Computer Science", List.of(math101.getId()));
        Professor retired = professor("Retired Teacher", "Physics", List.of());
        retired.setActive(false);
        professors.save(retired);
    }

    private Course course(String code, String name, String department) {
        Course course = new Course();
        course.setCode(code);
        course.setName(name);
        course.setDepartment(department);
        course.setCreditHours(3);
        return courses.save(course);
    }

    private Professor professor(String name, String department, List<String> courseIds) {
        Professor professor = new Professor();
        professor.setFullName(name);
        professor.setDepartment(department);
        professor.setCollege("King Abdullah II School of IT");
        professor.setTitle("Dr.");
        professor.setCourseIds(courseIds);
        return professors.save(professor);
    }

    private List<String> names(String q) {
        return catalog.search(q, PageRequest.of(0, 20, Sort.by("fullName"))).getContent().stream()
                .map(dto -> dto.fullName())
                .toList();
    }

    @Test
    @DisplayName("an empty query lists only active professors with their course codes")
    void emptyQueryListsActive() {
        var page = catalog.search("", PageRequest.of(0, 20));

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).extracting("fullName")
                .containsExactlyInAnyOrder("Khaled Mansour", "Rania Al-Tal");
        var khaledSummary = page.getContent().stream()
                .filter(dto -> dto.fullName().equals("Khaled Mansour")).findFirst().orElseThrow();
        assertThat(khaledSummary.courseCodes()).containsExactlyInAnyOrder("CS101", "CS211");
    }

    @Test
    @DisplayName("search matches name, department, and course code/name")
    void searchMatchesAllFields() {
        assertThat(names("mansour")).containsExactly("Khaled Mansour");
        assertThat(names("computer science")).containsExactlyInAnyOrder("Khaled Mansour", "Rania Al-Tal");
        assertThat(names("math101")).containsExactly("Rania Al-Tal");
        assertThat(names("data structures")).containsExactly("Khaled Mansour");
        assertThat(names("no such thing")).isEmpty();
    }

    @Test
    @DisplayName("paging and sorting are applied")
    void pagingAndSorting() {
        var page = catalog.search("", PageRequest.of(0, 1, Sort.by("fullName")));

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).fullName()).isEqualTo("Khaled Mansour");
    }

    @Test
    @DisplayName("detail returns the rating breakdown and the professor's courses")
    void detailResponse() {
        khaled.setAvgRating(4.3);
        khaled.setReviewCount(27);
        khaled.getBreakdown().setFive(14);
        khaled.getBreakdown().setFour(8);
        khaled.getBreakdown().setThree(3);
        khaled.getBreakdown().setTwo(1);
        khaled.getBreakdown().setOne(1);
        professors.save(khaled);

        var detail = catalog.detail(khaled.getId());

        assertThat(detail.avgRating()).isEqualTo(4.3);
        assertThat(detail.reviewCount()).isEqualTo(27);
        assertThat(detail.breakdown()).containsEntry("5", 14).containsEntry("1", 1);
        assertThat(detail.courses()).extracting("code")
                .containsExactlyInAnyOrder("CS101", "CS211");
    }

    @Test
    @DisplayName("an unknown professor id is a 404")
    void unknownDetail() {
        assertThatThrownBy(() -> catalog.detail("00000000-0000-0000-0000-000000000000"))
                .isInstanceOf(ApiException.class);
    }
}

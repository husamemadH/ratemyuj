package com.ratemyuj.config;

import com.ratemyuj.domain.Course;
import com.ratemyuj.domain.Professor;
import com.ratemyuj.repository.CourseRepository;
import com.ratemyuj.repository.ProfessorRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Demo catalog for local development only: run with
 * SPRING_PROFILES_ACTIVE=dev on an empty database. Never active in tests
 * or in the default profile.
 */
@Component
@Profile("dev")
public class DevDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevDataSeeder.class);

    private final CourseRepository courses;
    private final ProfessorRepository professors;

    public DevDataSeeder(CourseRepository courses, ProfessorRepository professors) {
        this.courses = courses;
        this.professors = professors;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (courses.count() > 0 || professors.count() > 0) {
            return;
        }

        Course cs101 = course("CS101", "مقدمة في البرمجة", "علم الحاسوب");
        Course cs211 = course("CS211", "هياكل البيانات", "علم الحاسوب");
        Course cs317 = course("CS317", "أنظمة التشغيل", "علم الحاسوب");
        Course math101 = course("MATH101", "تفاضل وتكامل ١", "الرياضيات");
        Course cs452 = course("CS452", "تعلّم الآلة", "علم الحاسوب");

        professor("د.", "خالد منصور", "علم الحاسوب", List.of(cs101.getId(), cs211.getId()));
        professor("أ.د.", "رانيا التل", "علم الحاسوب", List.of(cs317.getId(), cs452.getId()));
        professor("د.", "عمر حدادين", "الرياضيات", List.of(math101.getId()));

        log.info("Seeded dev catalog: 5 courses, 3 professors");
    }

    private Course course(String code, String name, String department) {
        Course course = new Course();
        course.setCode(code);
        course.setName(name);
        course.setDepartment(department);
        course.setCreditHours(3);
        return courses.save(course);
    }

    private void professor(String title, String fullName, String department, List<String> courseIds) {
        Professor professor = new Professor();
        professor.setTitle(title);
        professor.setFullName(fullName);
        professor.setDepartment(department);
        professor.setCollege("كلية الملك عبدالله الثاني لتكنولوجيا المعلومات");
        professor.setCourseIds(courseIds);
        professors.save(professor);
    }
}

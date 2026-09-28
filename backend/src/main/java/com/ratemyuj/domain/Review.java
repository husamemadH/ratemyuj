package com.ratemyuj.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

@Entity
@Table(name = "reviews", uniqueConstraints = @UniqueConstraint(
        name = "uniq_review", columnNames = {"student_hash", "professor_id", "course_id"}))
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "professor_id", nullable = false)
    private String professorId;

    @Column(name = "course_id", nullable = false)
    private String courseId;

    private String courseCode;   // denormalized for display
    private String courseName;

    @Column(name = "student_hash", nullable = false)
    private String studentHash;  // HMAC-SHA256(email, pepper) — never plaintext

    private int rating;          // 1..5
    @Column(length = 1000, nullable = false)
    private String comment;
    @Enumerated(EnumType.STRING)
    private Grade grade;
    private Integer difficulty;  // 1..5, nullable
    private Boolean wouldTakeAgain;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReviewStatus status;

    @Embedded
    private ModerationResult moderation;
    private int reportCount;

    private Instant createdAt;
    private Instant updatedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getProfessorId() { return professorId; }
    public void setProfessorId(String professorId) { this.professorId = professorId; }
    public String getCourseId() { return courseId; }
    public void setCourseId(String courseId) { this.courseId = courseId; }
    public String getCourseCode() { return courseCode; }
    public void setCourseCode(String courseCode) { this.courseCode = courseCode; }
    public String getCourseName() { return courseName; }
    public void setCourseName(String courseName) { this.courseName = courseName; }
    public String getStudentHash() { return studentHash; }
    public void setStudentHash(String studentHash) { this.studentHash = studentHash; }
    public int getRating() { return rating; }
    public void setRating(int rating) { this.rating = rating; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
    public Grade getGrade() { return grade; }
    public void setGrade(Grade grade) { this.grade = grade; }
    public Integer getDifficulty() { return difficulty; }
    public void setDifficulty(Integer difficulty) { this.difficulty = difficulty; }
    public Boolean getWouldTakeAgain() { return wouldTakeAgain; }
    public void setWouldTakeAgain(Boolean wouldTakeAgain) { this.wouldTakeAgain = wouldTakeAgain; }
    public ReviewStatus getStatus() { return status; }
    public void setStatus(ReviewStatus status) { this.status = status; }
    public ModerationResult getModeration() { return moderation; }
    public void setModeration(ModerationResult moderation) { this.moderation = moderation; }
    public int getReportCount() { return reportCount; }
    public void setReportCount(int reportCount) { this.reportCount = reportCount; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}

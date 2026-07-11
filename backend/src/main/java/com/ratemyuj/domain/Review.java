package com.ratemyuj.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document("reviews")
@CompoundIndex(name = "uniq_review",
        def = "{'studentHash': 1, 'professorId': 1, 'courseId': 1}", unique = true)
public class Review {

    @Id
    private String id;

    @Indexed
    private String professorId;
    private String courseId;
    private String courseCode;   // denormalized for display
    private String courseName;

    private String studentHash;  // HMAC-SHA256(email, pepper) — never plaintext

    private int rating;          // 1..5
    private String comment;
    private Grade grade;
    private Integer difficulty;  // 1..5, nullable
    private Boolean wouldTakeAgain;

    @Indexed
    private ReviewStatus status;
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

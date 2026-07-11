package com.ratemyuj.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.TextIndexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

@Document("professors")
public class Professor {

    @Id
    private String id;

    @TextIndexed(weight = 3)
    private String fullName;
    @TextIndexed
    private String department;
    private String college;
    private String title;      // Dr., Prof., Eng.
    private String photoUrl;

    private List<String> courseIds;

    // denormalized aggregates, maintained atomically by ProfessorStatsService
    private double avgRating;
    private int reviewCount;
    private int ratingSum;     // kept so avg can be recomputed with $inc only
    private RatingBreakdown breakdown = new RatingBreakdown();

    private boolean active = true;
    private Instant createdAt;
    private Instant updatedAt;

    public static class RatingBreakdown {
        private int one, two, three, four, five;
        public int getOne() { return one; }
        public void setOne(int one) { this.one = one; }
        public int getTwo() { return two; }
        public void setTwo(int two) { this.two = two; }
        public int getThree() { return three; }
        public void setThree(int three) { this.three = three; }
        public int getFour() { return four; }
        public void setFour(int four) { this.four = four; }
        public int getFive() { return five; }
        public void setFive(int five) { this.five = five; }
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }
    public String getCollege() { return college; }
    public void setCollege(String college) { this.college = college; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getPhotoUrl() { return photoUrl; }
    public void setPhotoUrl(String photoUrl) { this.photoUrl = photoUrl; }
    public List<String> getCourseIds() { return courseIds; }
    public void setCourseIds(List<String> courseIds) { this.courseIds = courseIds; }
    public double getAvgRating() { return avgRating; }
    public void setAvgRating(double avgRating) { this.avgRating = avgRating; }
    public int getReviewCount() { return reviewCount; }
    public void setReviewCount(int reviewCount) { this.reviewCount = reviewCount; }
    public int getRatingSum() { return ratingSum; }
    public void setRatingSum(int ratingSum) { this.ratingSum = ratingSum; }
    public RatingBreakdown getBreakdown() { return breakdown; }
    public void setBreakdown(RatingBreakdown breakdown) { this.breakdown = breakdown; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}

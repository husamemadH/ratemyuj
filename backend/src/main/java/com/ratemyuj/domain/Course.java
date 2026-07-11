package com.ratemyuj.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("courses")
public class Course {

    @Id
    private String id;

    @Indexed(unique = true)
    private String code;   // "CS101"
    private String name;   // "Intro to Programming"
    private String department;
    private int creditHours;
    private boolean active = true;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }
    public int getCreditHours() { return creditHours; }
    public void setCreditHours(int creditHours) { this.creditHours = creditHours; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}

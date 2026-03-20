package com.example.luminae.models;

/**
 * Stored in Firestore "users" collection.
 * Document ID = Firebase Auth UID
 *
 * Firebase Auth handles: email, password, password reset
 * Firestore handles: fullName, username, role, status
 */
public class User {
    private String fName;
    private String lName;
    private String username;
    private String email;
    private String role;
    private String status;
    private String campus;
    private String college;
    private String course;

    public User() {}

    public User(String fName, String lName, String username, String email,
                String campus, String college, String course, String role, String status) {
        this.fName    = fName;
        this.lName    = lName;
        this.username = username;
        this.email    = email;
        this.role     = role;
        this.status   = status;
        this.campus   = campus;
        this.college  = college;
        this.course   = course;
    }

    public String getFName()    { return fName; }
    public String getlName()    { return lName; }
    public String getUsername() { return username; }
    public String getEmail()    { return email; }
    public String getRole()     { return role; }
    public String getStatus()   { return status; }
    public String getCampus()   { return campus; }
    public String getCollege()  { return college; }
    public String getCourse()   { return course; }
}
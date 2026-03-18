package com.example.luminae.models;

/**
 * Stored in Firestore "users" collection.
 * Document ID = Firebase Auth UID
 *
 * Firebase Auth handles: email, password, password reset
 * Firestore handles: fullName, username, role, status
 */
public class User {
    private String fullName;
    private String username;
    private String email;
    private String role;
    private String status; // "pending" or "active"

    public User() {}

    public User(String fullName, String username, String email,
                String role, String status) {
        this.fullName = fullName;
        this.username = username;
        this.email    = email;
        this.role     = role;
        this.status   = status;
    }

    public String getFullName() { return fullName; }
    public String getUsername() { return username; }
    public String getEmail()    { return email; }
    public String getRole()     { return role; }
    public String getStatus()   { return status; }
}

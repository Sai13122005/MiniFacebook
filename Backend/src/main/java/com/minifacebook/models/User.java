
package com.minifacebook.models;

import java.util.LinkedHashSet;
import java.util.Set;

public class User {

    private String userId;
    private String username;
    private String passwordHash;
    private String bio;
    private String createdAt;

    private Set<String> followers = new LinkedHashSet<>();
    private Set<String> following = new LinkedHashSet<>();

    public User() {
    }

    public User(String userId, String username,
                String passwordHash, String bio, String createdAt) {

        this.userId = userId;
        this.username = username;
        this.passwordHash = passwordHash;
        this.bio = bio;
        this.createdAt = createdAt;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getBio() {
        return bio;
    }

    public void setBio(String bio) {
        this.bio = bio;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public Set<String> getFollowers() {
        if (followers == null) {
            followers = new LinkedHashSet<>();
        }
        return followers;
    }

    public void setFollowers(Set<String> followers) {
        this.followers = followers == null
                ? new LinkedHashSet<>()
                : new LinkedHashSet<>(followers);
    }

    public Set<String> getFollowing() {
        if (following == null) {
            following = new LinkedHashSet<>();
        }
        return following;
    }

    public void setFollowing(Set<String> following) {
        this.following = following == null
                ? new LinkedHashSet<>()
                : new LinkedHashSet<>(following);
    }
}


package com.minifacebook.services;

import com.minifacebook.models.User;
import com.minifacebook.storage.JsonStorage;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class UserService {

    private final JsonStorage storage;

    public UserService(JsonStorage storage) {
        this.storage = storage;
    }

    // Return all users except the current user
    public synchronized List<User> getAllUsers(String currentUserId)
            throws IOException {

        List<User> users = storage.loadUsers();
        List<User> result = new ArrayList<>();

        for (User user : users) {
            if (!user.getUserId().equals(currentUserId)) {
                result.add(user);
            }
        }

        return result;
    }

    // Find a user by ID
    public synchronized User getUserById(String userId)
            throws IOException {

        List<User> users = storage.loadUsers();

        for (User user : users) {
            if (user.getUserId().equals(userId)) {
                return user;
            }
        }

        throw new IllegalArgumentException("User not found");
    }

    // Follow another user
    public synchronized void followUser(
            String currentUserId, String targetUserId)
            throws IOException {

        if (currentUserId.equals(targetUserId)) {
            throw new IllegalArgumentException(
                    "You cannot follow yourself");
        }

        List<User> users = storage.loadUsers();

        User currentUser = findUser(users, currentUserId);
        User targetUser = findUser(users, targetUserId);

        if (!currentUser.getFollowing().add(targetUserId)) {
            throw new IllegalArgumentException(
                    "You are already following this user");
        }

        targetUser.getFollowers().add(currentUserId);

        storage.saveUsers(users);
    }

    // Unfollow another user
    public synchronized void unfollowUser(
            String currentUserId, String targetUserId)
            throws IOException {

        List<User> users = storage.loadUsers();

        User currentUser = findUser(users, currentUserId);
        User targetUser = findUser(users, targetUserId);

        if (!currentUser.getFollowing().remove(targetUserId)) {
            throw new IllegalArgumentException(
                    "You are not following this user");
        }

        targetUser.getFollowers().remove(currentUserId);

        storage.saveUsers(users);
    }

    public synchronized void updateBio(String userId, String bio)
            throws IOException {

        if (bio == null) {
            throw new IllegalArgumentException("Bio cannot be empty");
        }

        String cleanedBio = bio.trim();

        if (cleanedBio.length() > 250) {
            throw new IllegalArgumentException(
                    "Bio cannot exceed 250 characters");
        }

        List<User> users = storage.loadUsers();

        User currentUser = findUser(users, userId);
        currentUser.setBio(cleanedBio);

        storage.saveUsers(users);
    }

    // Check whether two users follow each other.
    public synchronized boolean areMutualConnections(
            String userId1, String userId2) throws IOException {

        List<User> users = storage.loadUsers();

        User user1 = findUser(users, userId1);
        User user2 = findUser(users, userId2);

        return user1.getFollowing().contains(userId2)
                && user2.getFollowing().contains(userId1);
    }

    private User findUser(List<User> users, String userId) {

        for (User user : users) {
            if (user.getUserId().equals(userId)) {
                return user;
            }
        }

        throw new IllegalArgumentException("User not found");
    }
}

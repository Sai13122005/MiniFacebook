package com.minifacebook.services;

import com.minifacebook.models.User;
import com.minifacebook.repositories.UserRepository;
import com.minifacebook.storage.JsonStorage;
import com.minifacebook.config.TransactionManager;
import com.minifacebook.models.Notification;
import com.minifacebook.repositories.NotificationRepository;
import java.time.Instant;
import java.util.UUID;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class UserService {

    private final JsonStorage storage;
    private final UserRepository userRepository;
    private final TransactionManager transactionManager;
    private final NotificationRepository notificationRepository;

    // Existing JSON constructor (temporary compatibility).
    public UserService(JsonStorage storage) {
        this.storage = storage;
        this.userRepository = null;
        this.transactionManager = null;
        this.notificationRepository = null;
    }

    public UserService(UserRepository userRepository) {
        this.storage = null;
        this.userRepository = userRepository;
        this.transactionManager = null;
        this.notificationRepository = null;
    }

    public UserService(
            UserRepository userRepository,
            TransactionManager transactionManager,
            NotificationRepository notificationRepository) {

        this.storage = null;
        this.userRepository = userRepository;
        this.transactionManager = transactionManager;
        this.notificationRepository = notificationRepository;
    }

    public void followUserWithNotification(
            String currentUserId,
            String targetUserId,
            String actorName) throws IOException {

        if (currentUserId.equals(targetUserId)) {
            throw new IllegalArgumentException(
                    "You cannot follow yourself");
        }

        if (transactionManager == null || notificationRepository == null) {
            throw new IllegalStateException(
                    "Transaction support is not configured");
        }

        try {
            transactionManager.execute(connection -> {

                // Confirm both users exist.
                User follower = userRepository.findById(currentUserId);
                User target = userRepository.findById(targetUserId);

                if (follower == null || target == null) {
                    throw new IllegalArgumentException("User not found");
                }

                // Insert the follow relationship.
                boolean followed = userRepository.addFollow(
                        connection,
                        currentUserId,
                        targetUserId);

                if (!followed) {
                    throw new IllegalArgumentException(
                            "You are already following this user");
                }

                // Create the notification in the same transaction.
                Notification notification = new Notification(
                        UUID.randomUUID().toString(),
                        targetUserId,
                        currentUserId,
                        "FOLLOW",
                        currentUserId,
                        actorName + " started following you",
                        Instant.now().toString(),
                        false);

                notificationRepository.insert(connection, notification);

                return null;
            });

        } catch (SQLException e) {
            throw new IOException(
                    "Database error while following user", e);
        }
    }

    // Return all users except the current user.
    public synchronized List<User> getAllUsers(String currentUserId)
            throws IOException {

        if (userRepository != null) {
            try {
                List<User> users = userRepository.findAll();
                List<User> result = new ArrayList<>();

                for (User user : users) {
                    if (!user.getUserId().equals(currentUserId)) {
                        result.add(user);
                    }
                }

                return result;
            } catch (SQLException e) {
                throw databaseError("fetch users", e);
            }
        }

        List<User> users = storage.loadUsers();
        List<User> result = new ArrayList<>();

        for (User user : users) {
            if (!user.getUserId().equals(currentUserId)) {
                result.add(user);
            }
        }

        return result;
    }

    // Find a user by ID.
    public synchronized User getUserById(String userId)
            throws IOException {

        if (userRepository != null) {
            try {
                User user = userRepository.findById(userId);

                if (user == null) {
                    throw new IllegalArgumentException("User not found");
                }

                return user;
            } catch (SQLException e) {
                throw databaseError("fetch user", e);
            }
        }

        List<User> users = storage.loadUsers();

        for (User user : users) {
            if (user.getUserId().equals(userId)) {
                return user;
            }
        }

        throw new IllegalArgumentException("User not found");
    }

    // Follow another user.
    public synchronized void followUser(
            String currentUserId, String targetUserId)
            throws IOException {

        if (currentUserId.equals(targetUserId)) {
            throw new IllegalArgumentException(
                    "You cannot follow yourself");
        }

        if (userRepository != null) {
            try {
                requireUser(currentUserId);
                requireUser(targetUserId);

                if (!userRepository.addFollow(
                        currentUserId, targetUserId)) {
                    throw new IllegalArgumentException(
                            "You are already following this user");
                }

                return;
            } catch (SQLException e) {
                throw databaseError("follow user", e);
            }
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

    // Unfollow another user.
    public synchronized void unfollowUser(
            String currentUserId, String targetUserId)
            throws IOException {

        if (userRepository != null) {
            try {
                requireUser(currentUserId);
                requireUser(targetUserId);

                if (!userRepository.removeFollow(
                        currentUserId, targetUserId)) {
                    throw new IllegalArgumentException(
                            "You are not following this user");
                }

                return;
            } catch (SQLException e) {
                throw databaseError("unfollow user", e);
            }
        }

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

    // Update a user's bio.
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

        if (userRepository != null) {
            try {
                requireUser(userId);
                userRepository.updateBio(userId, cleanedBio);
                return;
            } catch (SQLException e) {
                throw databaseError("update bio", e);
            }
        }

        List<User> users = storage.loadUsers();

        User currentUser = findUser(users, userId);
        currentUser.setBio(cleanedBio);

        storage.saveUsers(users);
    }

    // Check whether two users follow each other.
    public synchronized boolean areMutualConnections(
            String userId1, String userId2) throws IOException {

        if (userRepository != null) {
            try {
                User user1 = requireUser(userId1);
                User user2 = requireUser(userId2);

                return user1.getFollowing().contains(userId2)
                        && user2.getFollowing().contains(userId1);
            } catch (SQLException e) {
                throw databaseError("check mutual connections", e);
            }
        }

        List<User> users = storage.loadUsers();

        User user1 = findUser(users, userId1);
        User user2 = findUser(users, userId2);

        return user1.getFollowing().contains(userId2)
                && user2.getFollowing().contains(userId1);
    }

    private User requireUser(String userId) throws SQLException {

        User user = userRepository.findById(userId);

        if (user == null) {
            throw new IllegalArgumentException("User not found");
        }

        return user;
    }

    private User findUser(List<User> users, String userId) {

        for (User user : users) {
            if (user.getUserId().equals(userId)) {
                return user;
            }
        }

        throw new IllegalArgumentException("User not found");
    }

    private IOException databaseError(String operation, SQLException e) {
        return new IOException(
                "Database error while attempting to " + operation, e);
    }
}
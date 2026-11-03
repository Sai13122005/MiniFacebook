package com.minifacebook.services;

import com.minifacebook.models.User;
import com.minifacebook.repositories.UserRepository;
import com.minifacebook.security.PasswordUtil;
import com.minifacebook.storage.JsonStorage;

import java.io.IOException;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class AuthService {

    // Transitional support for the existing JSON implementation.
    private final JsonStorage storage;

    // PostgreSQL implementation.
    private final UserRepository userRepository;

    // Existing constructor — keeps the current MainServer working.
    public AuthService(JsonStorage storage) {
        this.storage = storage;
        this.userRepository = null;
    }

    // New constructor — uses PostgreSQL.
    public AuthService(UserRepository userRepository) {
        this.storage = null;
        this.userRepository = userRepository;
    }

    public synchronized User register(
            String username, String password)
            throws IOException, SQLException {

        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException(
                    "Username cannot be empty");
        }

        if (password == null || password.length() < 8) {
            throw new IllegalArgumentException(
                    "Password must contain at least 8 characters");
        }

        String cleanedUsername = username.trim();

        if (userRepository != null) {

            if (userRepository.findByUsername(cleanedUsername) != null) {
                throw new IllegalArgumentException(
                        "Username already exists");
            }

        } else {

            List<User> users = storage.loadUsers();

            for (User user : users) {
                if (user.getUsername().equalsIgnoreCase(cleanedUsername)) {
                    throw new IllegalArgumentException(
                            "Username already exists");
                }
            }
        }

        String passwordHash =
                PasswordUtil.hashPassword(password);

        User newUser = new User(
                UUID.randomUUID().toString(),
                cleanedUsername,
                passwordHash,
                "",
                Instant.now().toString()
        );

        if (userRepository != null) {
            userRepository.insert(newUser);
        } else {
            List<User> users = storage.loadUsers();
            users.add(newUser);
            storage.saveUsers(users);
        }

        return newUser;
    }

    public synchronized User login(
            String username, String password)
            throws IOException, SQLException {

        if (username == null || password == null) {
            throw new IllegalArgumentException(
                    "Username and password are required");
        }

        if (userRepository != null) {

            User user = userRepository.findByUsername(username.trim());

            if (user != null && PasswordUtil.verifyPassword(
                    password, user.getPasswordHash())) {
                return user;
            }

        } else {

            List<User> users = storage.loadUsers();

            for (User user : users) {

                if (user.getUsername().equalsIgnoreCase(username.trim())
                        && PasswordUtil.verifyPassword(
                                password, user.getPasswordHash())) {
                    return user;
                }
            }
        }

        throw new IllegalArgumentException(
                "Invalid username or password");
    }
}
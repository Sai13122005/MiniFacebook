
package com.minifacebook.services;

import com.minifacebook.models.User;
import com.minifacebook.security.PasswordUtil;
import com.minifacebook.storage.JsonStorage;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class AuthService {

    private final JsonStorage storage;

    public AuthService(JsonStorage storage) {
        this.storage = storage;
    }

    public synchronized User register(
            String username,
            String password) throws IOException {

        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException(
                    "Username cannot be empty");
        }

        if (password == null || password.length() < 8) {
            throw new IllegalArgumentException(
                    "Password must contain at least 8 characters");
        }

        List<User> users = storage.loadUsers();

        for (User user : users) {
            if (user.getUsername().equalsIgnoreCase(username.trim())) {
                throw new IllegalArgumentException(
                        "Username already exists");
            }
        }

        String passwordHash =
                PasswordUtil.hashPassword(password);

        User newUser = new User(
                UUID.randomUUID().toString(),
                username.trim(),
                passwordHash,
                "",
                Instant.now().toString()
        );

        users.add(newUser);
        storage.saveUsers(users);

        return newUser;
    }

    public synchronized User login(
            String username,
            String password) throws IOException {

        if (username == null || password == null) {
            throw new IllegalArgumentException(
                    "Username and password are required");
        }

        List<User> users = storage.loadUsers();

        for (User user : users) {

            if (user.getUsername().equalsIgnoreCase(username.trim())
                    && PasswordUtil.verifyPassword(
                            password, user.getPasswordHash())) {

                return user;
            }
        }

        throw new IllegalArgumentException(
                "Invalid username or password");
    }
}

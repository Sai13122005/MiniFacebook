package com.minifacebook.repositories;

import com.minifacebook.models.User;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class UserRepository {

    private final DataSource dataSource;

    public UserRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    // Retrieve all users, including their follower/following relationships.
    public List<User> findAll() throws SQLException {

        String sql = """
                SELECT user_id, username, password_hash, bio, created_at
                FROM users
                ORDER BY username
                """;

        List<User> users = new ArrayList<>();

        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet rs = statement.executeQuery()) {

            while (rs.next()) {
                users.add(mapUser(rs));
            }

            loadRelationships(connection, users);
        }

        return users;
    }

    // Find one user by ID.
    public User findById(String userId) throws SQLException {

        String sql = """
                SELECT user_id, username, password_hash, bio, created_at
                FROM users
                WHERE user_id = ?
                """;

        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, userId);

            try (ResultSet rs = statement.executeQuery()) {

                if (!rs.next()) {
                    return null;
                }

                User user = mapUser(rs);
                loadRelationships(connection, List.of(user));

                return user;
            }
        }
    }

    // Find a user by username, ignoring case.
    public User findByUsername(String username) throws SQLException {

        String sql = """
                SELECT user_id, username, password_hash, bio, created_at
                FROM users
                WHERE LOWER(username) = LOWER(?)
                """;

        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, username.trim());

            try (ResultSet rs = statement.executeQuery()) {

                if (!rs.next()) {
                    return null;
                }

                User user = mapUser(rs);
                loadRelationships(connection, List.of(user));

                return user;
            }
        }
    }

    // Insert a new user.
    public void insert(User user) throws SQLException {

        String sql = """
                INSERT INTO users
                    (user_id, username, password_hash, bio, created_at)
                VALUES (?, ?, ?, ?, ?)
                """;

        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, user.getUserId());
            statement.setString(2, user.getUsername());
            statement.setString(3, user.getPasswordHash());
            statement.setString(4, user.getBio());
            statement.setTimestamp(
                    5, Timestamp.from(
                            java.time.Instant.parse(user.getCreatedAt())));

            statement.executeUpdate();
        }
    }

    // Update a user's bio.
    public void updateBio(String userId, String bio) throws SQLException {

        String sql = """
                UPDATE users
                SET bio = ?
                WHERE user_id = ?
                """;

        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, bio);
            statement.setString(2, userId);

            if (statement.executeUpdate() == 0) {
                throw new IllegalArgumentException("User not found");
            }
        }
    }

    public boolean addFollow(
            String followerId,
            String followingId) throws SQLException {

        try (Connection connection = dataSource.getConnection()) {
            return addFollow(connection, followerId, followingId);
        }
    }

    public boolean addFollow(
            Connection connection,
            String followerId,
            String followingId) throws SQLException {

        String sql = """
                INSERT INTO follows (follower_id, following_id, created_at)
                VALUES (?, ?, CURRENT_TIMESTAMP)
                ON CONFLICT (follower_id, following_id) DO NOTHING
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, followerId);
            statement.setString(2, followingId);

            return statement.executeUpdate() == 1;
        }
    }

    // Remove a follower -> following relationship.
    public boolean removeFollow(
            String followerId, String followingId) throws SQLException {

        String sql = """
                DELETE FROM follows
                WHERE follower_id = ? AND following_id = ?
                """;

        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, followerId);
            statement.setString(2, followingId);

            return statement.executeUpdate() == 1;
        }
    }

    // Convert a database row into the existing Java User model.
    private User mapUser(ResultSet rs) throws SQLException {

        Timestamp createdAt = rs.getTimestamp("created_at");

        return new User(
                rs.getString("user_id"),
                rs.getString("username"),
                rs.getString("password_hash"),
                rs.getString("bio"),
                createdAt == null ? null : createdAt.toInstant().toString());
    }

    // Load relationships into User.followers and User.following.
    private void loadRelationships(
            Connection connection, List<User> users) throws SQLException {

        if (users.isEmpty()) {
            return;
        }

        String sql = """
                SELECT follower_id, following_id
                FROM follows
                WHERE follower_id = ANY (?) OR following_id = ANY (?)
                """;

        String[] userIds = users.stream()
                .map(User::getUserId)
                .toArray(String[]::new);

        try (PreparedStatement statement = connection.prepareStatement(sql)) {

            Array ids1 = connection.createArrayOf("varchar", userIds);
            Array ids2 = connection.createArrayOf("varchar", userIds);

            try {
                statement.setArray(1, ids1);
                statement.setArray(2, ids2);

                try (ResultSet rs = statement.executeQuery()) {

                    // Index users by ID for efficient relationship mapping.
                    java.util.Map<String, User> userMap = new java.util.HashMap<>();

                    for (User user : users) {
                        userMap.put(user.getUserId(), user);
                    }

                    while (rs.next()) {

                        String followerId = rs.getString("follower_id");
                        String followingId = rs.getString("following_id");

                        User follower = userMap.get(followerId);
                        User following = userMap.get(followingId);

                        if (follower != null) {
                            follower.getFollowing().add(followingId);
                        }

                        if (following != null) {
                            following.getFollowers().add(followerId);
                        }
                    }
                }
            } finally {
                ids1.free();
                ids2.free();
            }
        }
    }
}
package com.minifacebook.repositories;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.*;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

public class SessionRepository {

    private final DataSource dataSource;

    public SessionRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    // Create a session and store only the token hash.
    public void createSession(
            String userId,
            String rawToken,
            Instant expiresAt) throws SQLException {

        String sql = """
                INSERT INTO sessions
                    (session_id, user_id, token_hash,
                     created_at, expires_at, revoked_at)
                VALUES (?, ?, ?, ?, ?, NULL)
                """;

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, UUID.randomUUID().toString());
            statement.setString(2, userId);
            statement.setString(3, hashToken(rawToken));
            statement.setTimestamp(4, Timestamp.from(Instant.now()));
            statement.setTimestamp(5, Timestamp.from(expiresAt));

            statement.executeUpdate();
        }
    }

    // Find the user associated with a valid session token.
    // Returns null if the token is invalid, expired, or revoked.
    public String findUserIdByToken(String rawToken) throws SQLException {

        String sql = """
                SELECT user_id
                FROM sessions
                WHERE token_hash = ?
                  AND revoked_at IS NULL
                  AND expires_at > CURRENT_TIMESTAMP
                """;

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, hashToken(rawToken));

            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getString("user_id") : null;
            }
        }
    }

    // Revoke a session.
    public boolean revokeSession(String rawToken) throws SQLException {

        String sql = """
                UPDATE sessions
                SET revoked_at = CURRENT_TIMESTAMP
                WHERE token_hash = ?
                  AND revoked_at IS NULL
                """;

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, hashToken(rawToken));
            return statement.executeUpdate() == 1;
        }
    }

    // Revoke all active sessions belonging to a user.
    public int revokeAllUserSessions(String userId) throws SQLException {

        String sql = """
                UPDATE sessions
                SET revoked_at = CURRENT_TIMESTAMP
                WHERE user_id = ?
                  AND revoked_at IS NULL
                """;

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, userId);
            return statement.executeUpdate();
        }
    }

    // Remove expired or revoked sessions.
    public int deleteExpiredSessions() throws SQLException {

        String sql = """
                DELETE FROM sessions
                WHERE expires_at <= CURRENT_TIMESTAMP
                   OR revoked_at IS NOT NULL
                """;

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            return statement.executeUpdate();
        }
    }

    private String hashToken(String rawToken) {

        if (rawToken == null || rawToken.isBlank()) {
            throw new IllegalArgumentException("Session token is required");
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            byte[] hash = digest.digest(
                    rawToken.getBytes(StandardCharsets.UTF_8));

            return HexFormat.of().formatHex(hash);

        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(
                    "SHA-256 algorithm is unavailable", e);
        }
    }
}
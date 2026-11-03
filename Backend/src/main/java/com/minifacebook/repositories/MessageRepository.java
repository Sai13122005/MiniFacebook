package com.minifacebook.repositories;

import com.minifacebook.models.Message;

import javax.sql.DataSource;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class MessageRepository {

    private static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");

    private final DataSource dataSource;

    public MessageRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    // Save a new message.
    public void insert(Message message) throws SQLException {

        String sql = """
                INSERT INTO messages
                    (message_id, sender_id, receiver_id,
                     content, created_at, is_read)
                VALUES (?, ?, ?, ?, ?, ?)
                """;

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, message.getMessageId());
            statement.setString(2, message.getSenderId());
            statement.setString(3, message.getReceiverId());
            statement.setString(4, message.getContent());

            LocalDateTime localTime =
                    LocalDateTime.parse(message.getCreatedAt());

            statement.setObject(
                    5, localTime.atZone(INDIA).toOffsetDateTime());

            statement.setBoolean(6, message.isRead());

            statement.executeUpdate();
        }
    }

    // Retrieve a conversation between two users.
    public List<Message> findConversation(
            String userId, String otherUserId) throws SQLException {

        String sql = """
                SELECT message_id, sender_id, receiver_id,
                       content, created_at, is_read
                FROM messages
                WHERE (sender_id = ? AND receiver_id = ?)
                   OR (sender_id = ? AND receiver_id = ?)
                ORDER BY created_at ASC, message_id ASC
                """;

        List<Message> messages = new ArrayList<>();

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, userId);
            statement.setString(2, otherUserId);
            statement.setString(3, otherUserId);
            statement.setString(4, userId);

            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    messages.add(mapMessage(rs));
                }
            }
        }

        return messages;
    }

    // Retrieve all conversation partner IDs.
    public List<String> findConversationUserIds(String userId)
            throws SQLException {

        String sql = """
                SELECT sender_id, receiver_id
                FROM messages
                WHERE sender_id = ? OR receiver_id = ?
                ORDER BY created_at ASC, message_id ASC
                """;

        Set<String> userIds = new LinkedHashSet<>();

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, userId);
            statement.setString(2, userId);

            try (ResultSet rs = statement.executeQuery()) {

                while (rs.next()) {
                    String sender = rs.getString("sender_id");
                    String receiver = rs.getString("receiver_id");

                    String otherUserId =
                            sender.equals(userId) ? receiver : sender;

                    userIds.add(otherUserId);
                }
            }
        }

        return new ArrayList<>(userIds);
    }

    // Count unread messages received from a particular user.
    public int countUnread(String userId, String otherUserId)
            throws SQLException {

        String sql = """
                SELECT COUNT(*)
                FROM messages
                WHERE sender_id = ?
                  AND receiver_id = ?
                  AND is_read = FALSE
                """;

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, otherUserId);
            statement.setString(2, userId);

            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    // Mark incoming messages from another user as read.
    public int markAsRead(String userId, String otherUserId)
            throws SQLException {

        String sql = """
                UPDATE messages
                SET is_read = TRUE
                WHERE sender_id = ?
                  AND receiver_id = ?
                  AND is_read = FALSE
                """;

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, otherUserId);
            statement.setString(2, userId);

            return statement.executeUpdate();
        }
    }

    private Message mapMessage(ResultSet rs) throws SQLException {

        Message message = new Message();

        message.setMessageId(rs.getString("message_id"));
        message.setSenderId(rs.getString("sender_id"));
        message.setReceiverId(rs.getString("receiver_id"));
        message.setContent(rs.getString("content"));

        OffsetDateTime createdAt =
                rs.getObject("created_at", OffsetDateTime.class);

        message.setCreatedAt(
                createdAt == null
                        ? null
                        : createdAt.atZoneSameInstant(INDIA)
                                   .toLocalDateTime().toString()
        );

        message.setRead(rs.getBoolean("is_read"));

        return message;
    }
}
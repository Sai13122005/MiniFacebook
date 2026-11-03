package com.minifacebook.repositories;

import com.minifacebook.models.Notification;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class NotificationRepository {

    private final DataSource dataSource;

    public NotificationRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    // Insert a new notification.

    public void insert(Notification notification) throws SQLException {

        try (Connection connection = dataSource.getConnection()) {
            insert(connection, notification);
        }
    }

    public void insert(
            Connection connection,
            Notification notification) throws SQLException {

        String sql = """
                INSERT INTO notifications
                    (notification_id, receiver_id, actor_id, type,
                     reference_id, message, created_at, is_read)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, notification.getNotificationId());
            statement.setString(2, notification.getReceiverId());
            statement.setString(3, notification.getActorId());
            statement.setString(4, notification.getType());
            statement.setString(5, notification.getReferenceId());
            statement.setString(6, notification.getMessage());

            statement.setTimestamp(
                    7,
                    Timestamp.from(
                            Instant.parse(notification.getCreatedAt())));

            statement.setBoolean(8, notification.isRead());

            statement.executeUpdate();
        }
    }

    // Retrieve notifications for one user, newest first.
    public List<Notification> findByReceiverId(String receiverId)
            throws SQLException {

        String sql = """
                SELECT notification_id, receiver_id, actor_id, type,
                       reference_id, message, created_at, is_read
                FROM notifications
                WHERE receiver_id = ?
                ORDER BY created_at DESC, notification_id DESC
                """;

        List<Notification> notifications = new ArrayList<>();

        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, receiverId);

            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    notifications.add(mapNotification(rs));
                }
            }
        }

        return notifications;
    }

    // Mark a notification as read only if it belongs to the receiver.
    public boolean markAsRead(String receiverId, String notificationId)
            throws SQLException {

        String sql = """
                UPDATE notifications
                SET is_read = TRUE
                WHERE receiver_id = ?
                  AND notification_id = ?
                """;

        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, receiverId);
            statement.setString(2, notificationId);

            return statement.executeUpdate() == 1;
        }
    }

    private Notification mapNotification(ResultSet rs)
            throws SQLException {

        Timestamp createdAt = rs.getTimestamp("created_at");

        return new Notification(
                rs.getString("notification_id"),
                rs.getString("receiver_id"),
                rs.getString("actor_id"),
                rs.getString("type"),
                rs.getString("reference_id"),
                rs.getString("message"),
                createdAt == null ? null : createdAt.toInstant().toString(),
                rs.getBoolean("is_read"));
    }
}
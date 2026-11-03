package com.minifacebook.services;

import com.minifacebook.models.Notification;
import com.minifacebook.repositories.NotificationRepository;
import com.minifacebook.storage.JsonStorage;

import java.io.IOException;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public class NotificationService {

    private final JsonStorage storage;
    private final NotificationRepository notificationRepository;

    // Existing JSON constructor.
    public NotificationService(JsonStorage storage) {
        this.storage = storage;
        this.notificationRepository = null;
    }

    // PostgreSQL constructor.
    public NotificationService(
            NotificationRepository notificationRepository) {

        this.storage = null;
        this.notificationRepository = notificationRepository;
    }

    // Create a notification.
    public synchronized void createNotification(
            String receiverId,
            String actorId,
            String type,
            String referenceId,
            String message) throws IOException {

        // Do not notify users about their own actions.
        if (receiverId.equals(actorId)) {
            return;
        }

        Notification notification = new Notification(
                UUID.randomUUID().toString(),
                receiverId,
                actorId,
                type,
                referenceId,
                message,
                Instant.now().toString(),
                false
        );

        if (notificationRepository != null) {
            try {
                notificationRepository.insert(notification);
                return;
            } catch (SQLException e) {
                throw databaseError("create notification", e);
            }
        }

        List<Notification> notifications =
                storage.loadNotifications();

        notifications.add(notification);
        storage.saveNotifications(notifications);
    }

    // Get notifications for a particular user.
    public synchronized List<Notification> getNotifications(
            String receiverId) throws IOException {

        if (notificationRepository != null) {
            try {
                return notificationRepository.findByReceiverId(receiverId);
            } catch (SQLException e) {
                throw databaseError("retrieve notifications", e);
            }
        }

        List<Notification> notifications =
                storage.loadNotifications();

        return notifications.stream()
                .filter(n -> n.getReceiverId().equals(receiverId))
                .sorted(Comparator.comparing(
                        Notification::getCreatedAt).reversed())
                .toList();
    }

    // Mark one notification as read.
    public synchronized void markAsRead(
            String receiverId,
            String notificationId) throws IOException {

        if (notificationRepository != null) {
            try {
                if (!notificationRepository.markAsRead(
                        receiverId, notificationId)) {
                    throw new IllegalArgumentException(
                            "Notification not found");
                }

                return;
            } catch (SQLException e) {
                throw databaseError("mark notification as read", e);
            }
        }

        List<Notification> notifications =
                storage.loadNotifications();

        boolean found = false;

        for (Notification notification : notifications) {

            if (notification.getNotificationId().equals(notificationId)
                    && notification.getReceiverId().equals(receiverId)) {

                notification.setRead(true);
                found = true;
                break;
            }
        }

        if (!found) {
            throw new IllegalArgumentException(
                    "Notification not found");
        }

        storage.saveNotifications(notifications);
    }

    private IOException databaseError(
            String operation, SQLException e) {

        return new IOException(
                "Database error while attempting to " + operation, e);
    }
}
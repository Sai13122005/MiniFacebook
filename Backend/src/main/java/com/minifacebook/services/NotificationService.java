
package com.minifacebook.services;

import com.minifacebook.models.Notification;
import com.minifacebook.storage.JsonStorage;

import java.io.IOException;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public class NotificationService {

    private final JsonStorage storage;

    public NotificationService(JsonStorage storage) {
        this.storage = storage;
    }

    // Create a notification
    public synchronized void createNotification(
            String receiverId,
            String actorId,
            String type,
            String referenceId,
            String message) throws IOException {

        // Do not notify users about their own actions
        if (receiverId.equals(actorId)) {
            return;
        }

        List<Notification> notifications =
                storage.loadNotifications();

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

        notifications.add(notification);

        storage.saveNotifications(notifications);
    }

    // Get notifications for a particular user
    public synchronized List<Notification> getNotifications(
            String receiverId) throws IOException {

        List<Notification> notifications =
                storage.loadNotifications();

        return notifications.stream()
                .filter(n -> n.getReceiverId().equals(receiverId))
                .sorted(Comparator.comparing(
                        Notification::getCreatedAt).reversed())
                .toList();
    }

    // Mark one notification as read
    public synchronized void markAsRead(
            String receiverId,
            String notificationId) throws IOException {

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
}

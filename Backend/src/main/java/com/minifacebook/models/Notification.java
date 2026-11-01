
package com.minifacebook.models;

public class Notification {

    private String notificationId;
    private String receiverId;
    private String actorId;
    private String type;
    private String referenceId;
    private String message;
    private String createdAt;
    private boolean read;

    public Notification() {
    }

    public Notification(
            String notificationId,
            String receiverId,
            String actorId,
            String type,
            String referenceId,
            String message,
            String createdAt,
            boolean read) {

        this.notificationId = notificationId;
        this.receiverId = receiverId;
        this.actorId = actorId;
        this.type = type;
        this.referenceId = referenceId;
        this.message = message;
        this.createdAt = createdAt;
        this.read = read;
    }

    public String getNotificationId() {
        return notificationId;
    }

    public void setNotificationId(String notificationId) {
        this.notificationId = notificationId;
    }

    public String getReceiverId() {
        return receiverId;
    }

    public void setReceiverId(String receiverId) {
        this.receiverId = receiverId;
    }

    public String getActorId() {
        return actorId;
    }

    public void setActorId(String actorId) {
        this.actorId = actorId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getReferenceId() {
        return referenceId;
    }

    public void setReferenceId(String referenceId) {
        this.referenceId = referenceId;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public boolean isRead() {
        return read;
    }

    public void setRead(boolean read) {
        this.read = read;
    }
}

package com.minifacebook.services;

import com.minifacebook.models.Message;
import com.minifacebook.repositories.MessageRepository;
import com.minifacebook.storage.JsonStorage;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class MessageService {

    private final JsonStorage storage;
    private final MessageRepository messageRepository;

    // Existing JSON constructor.
    public MessageService(JsonStorage storage) {
        this.storage = storage;
        this.messageRepository = null;
    }

    // PostgreSQL constructor.
    public MessageService(MessageRepository messageRepository) {
        this.storage = null;
        this.messageRepository = messageRepository;
    }

    public synchronized int getUnreadCount(
            String userId, String otherUserId) throws IOException {

        if (messageRepository != null) {
            try {
                return messageRepository.countUnread(userId, otherUserId);
            } catch (SQLException e) {
                throw databaseError("count unread messages", e);
            }
        }

        List<Message> messages = storage.loadMessages();
        int count = 0;

        for (Message message : messages) {
            if (message.getSenderId().equals(otherUserId)
                    && message.getReceiverId().equals(userId)
                    && !message.isRead()) {
                count++;
            }
        }

        return count;
    }

    public synchronized Message sendMessage(
            String senderId,
            String receiverId,
            String content) throws IOException {

        validateMessage(senderId, receiverId, content);

        Message message = new Message(
                senderId, receiverId, content.trim());

        if (messageRepository != null) {
            try {
                messageRepository.insert(message);
                return message;
            } catch (SQLException e) {
                throw databaseError("send message", e);
            }
        }

        List<Message> messages = storage.loadMessages();
        messages.add(message);
        storage.saveMessages(messages);

        return message;
    }

    public synchronized List<Message> getConversation(
            String userId, String otherUserId) throws IOException {

        if (messageRepository != null) {
            try {
                return messageRepository.findConversation(
                        userId, otherUserId);
            } catch (SQLException e) {
                throw databaseError("retrieve conversation", e);
            }
        }

        List<Message> messages = storage.loadMessages();
        List<Message> conversation = new ArrayList<>();

        for (Message message : messages) {

            boolean betweenUsers =
                    (message.getSenderId().equals(userId)
                            && message.getReceiverId().equals(otherUserId))
                    ||
                    (message.getSenderId().equals(otherUserId)
                            && message.getReceiverId().equals(userId));

            if (betweenUsers) {
                conversation.add(message);
            }
        }

        conversation.sort(
                Comparator.comparing(Message::getCreatedAt));

        return conversation;
    }

    public synchronized List<String> getConversationUserIds(
            String userId) throws IOException {

        if (messageRepository != null) {
            try {
                return messageRepository.findConversationUserIds(userId);
            } catch (SQLException e) {
                throw databaseError("retrieve conversation users", e);
            }
        }

        List<Message> messages = storage.loadMessages();
        List<String> userIds = new ArrayList<>();

        for (Message message : messages) {

            String otherUserId = null;

            if (message.getSenderId().equals(userId)) {
                otherUserId = message.getReceiverId();
            } else if (message.getReceiverId().equals(userId)) {
                otherUserId = message.getSenderId();
            }

            if (otherUserId != null && !userIds.contains(otherUserId)) {
                userIds.add(otherUserId);
            }
        }

        return userIds;
    }

    public synchronized void markMessagesAsRead(
            String userId, String otherUserId) throws IOException {

        if (messageRepository != null) {
            try {
                messageRepository.markAsRead(userId, otherUserId);
                return;
            } catch (SQLException e) {
                throw databaseError("mark messages as read", e);
            }
        }

        List<Message> messages = storage.loadMessages();
        boolean changed = false;

        for (Message message : messages) {

            if (message.getSenderId().equals(otherUserId)
                    && message.getReceiverId().equals(userId)
                    && !message.isRead()) {

                message.setRead(true);
                changed = true;
            }
        }

        if (changed) {
            storage.saveMessages(messages);
        }
    }

    private void validateMessage(
            String senderId, String receiverId, String content) {

        if (senderId == null || senderId.isBlank()
                || receiverId == null || receiverId.isBlank()) {
            throw new IllegalArgumentException(
                    "Invalid sender or receiver");
        }

        if (senderId.equals(receiverId)) {
            throw new IllegalArgumentException(
                    "You cannot send a message to yourself");
        }

        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException(
                    "Message cannot be empty");
        }

        if (content.length() > 2000) {
            throw new IllegalArgumentException(
                    "Message cannot exceed 2000 characters");
        }
    }

    private IOException databaseError(
            String operation, SQLException e) {

        return new IOException(
                "Database error while attempting to " + operation, e);
    }
}
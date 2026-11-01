
package com.minifacebook.services;

import com.minifacebook.models.Message;
import com.minifacebook.storage.JsonStorage;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class MessageService {

    private final JsonStorage storage;

    public MessageService(JsonStorage storage) {
        this.storage = storage;
    }

    public synchronized int getUnreadCount(
            String userId,
            String otherUserId) throws IOException {

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

        if (senderId == null || senderId.isBlank()
                || receiverId == null || receiverId.isBlank()) {
            throw new IllegalArgumentException("Invalid sender or receiver");
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

        List<Message> messages = storage.loadMessages();

        Message message = new Message(
                senderId,
                receiverId,
                content.trim());

        messages.add(message);
        storage.saveMessages(messages);

        return message;
    }

    public synchronized List<Message> getConversation(
            String userId,
            String otherUserId) throws IOException {

        List<Message> messages = storage.loadMessages();

        List<Message> conversation = new ArrayList<>();

        for (Message message : messages) {

            boolean betweenUsers = (message.getSenderId().equals(userId)
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
            String userId,
            String otherUserId) throws IOException {

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
}


package com.minifacebook.storage;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.minifacebook.models.User;
import com.minifacebook.models.Post;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import com.minifacebook.models.Comment;
import com.minifacebook.models.Message;
import com.minifacebook.models.Notification;
import com.minifacebook.models.Message;
public class JsonStorage {

    private static final ObjectMapper mapper = new ObjectMapper();
    private static final Path POSTS_FILE = Path.of("..", "data", "posts.json");
    // We run Maven from the backend directory.
    private static final Path USERS_FILE = Path.of("..", "data", "users.json");

    private static final Path COMMENTS_FILE = Path.of("..", "data", "comments.json");
    private static final Path NOTIFICATIONS_FILE = Path.of("..", "data", "notifications.json");
    private static final Path MESSAGES_FILE = Path.of("..", "data", "messages.json");
    
    public synchronized List<Message> loadMessages()
            throws IOException {

        if (!Files.exists(MESSAGES_FILE)) {
            return new ArrayList<>();
        }

        return mapper.readValue(
                MESSAGES_FILE.toFile(),
                new TypeReference<List<Message>>() {
                });
    }

    public synchronized void saveMessages(List<Message> messages)
            throws IOException {

        Files.createDirectories(MESSAGES_FILE.getParent());

        mapper.writerWithDefaultPrettyPrinter()
                .writeValue(MESSAGES_FILE.toFile(), messages);
    }

    public synchronized List<User> loadUsers() throws IOException {

        if (!Files.exists(USERS_FILE)) {
            return new ArrayList<>();
        }

        return mapper.readValue(
                USERS_FILE.toFile(),
                new TypeReference<List<User>>() {
                });
    }

    public synchronized List<Post> loadPosts() throws IOException {

        if (!Files.exists(POSTS_FILE)) {
            return new ArrayList<>();
        }

        return mapper.readValue(
                POSTS_FILE.toFile(),
                new TypeReference<List<Post>>() {
                });
    }

    public synchronized void savePosts(List<Post> posts)
            throws IOException {

        Files.createDirectories(POSTS_FILE.getParent());

        mapper.writerWithDefaultPrettyPrinter()
                .writeValue(POSTS_FILE.toFile(), posts);
    }

    public synchronized void saveUsers(List<User> users)
            throws IOException {

        Files.createDirectories(USERS_FILE.getParent());

        mapper.writerWithDefaultPrettyPrinter()
                .writeValue(USERS_FILE.toFile(), users);
    }

    public synchronized List<Comment> loadComments()
            throws IOException {

        if (!Files.exists(COMMENTS_FILE)) {
            return new ArrayList<>();
        }

        return mapper.readValue(
                COMMENTS_FILE.toFile(),
                new TypeReference<List<Comment>>() {
                });
    }

    public synchronized void saveComments(List<Comment> comments)
            throws IOException {

        Files.createDirectories(COMMENTS_FILE.getParent());

        mapper.writerWithDefaultPrettyPrinter()
                .writeValue(COMMENTS_FILE.toFile(), comments);
    }

    public synchronized List<Notification> loadNotifications()
            throws IOException {

        if (!Files.exists(NOTIFICATIONS_FILE)) {
            return new ArrayList<>();
        }

        return mapper.readValue(
                NOTIFICATIONS_FILE.toFile(),
                new TypeReference<List<Notification>>() {
                });
    }

    public synchronized void saveNotifications(
            List<Notification> notifications) throws IOException {

        Files.createDirectories(NOTIFICATIONS_FILE.getParent());

        mapper.writerWithDefaultPrettyPrinter()
                .writeValue(NOTIFICATIONS_FILE.toFile(), notifications);
    }

}

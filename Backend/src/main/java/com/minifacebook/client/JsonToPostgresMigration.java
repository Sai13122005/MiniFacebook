package com.minifacebook.client;

import com.minifacebook.config.DatabaseConfig;
import com.minifacebook.models.*;
import com.minifacebook.storage.JsonStorage;

import javax.sql.DataSource;
import java.sql.*;
import java.time.*;
import java.time.format.DateTimeParseException;
import java.util.*;

public class JsonToPostgresMigration {

    private static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");

    public static void main(String[] args) {

        DataSource dataSource = null;

        try {
            dataSource = DatabaseConfig.getDataSource();

            JsonStorage storage = new JsonStorage();

            List<User> users = storage.loadUsers();
            List<Post> posts = storage.loadPosts();
            List<Comment> comments = storage.loadComments();
            List<Message> messages = storage.loadMessages();
            List<Notification> notifications =
                    storage.loadNotifications();

            System.out.println("JSON data loaded:");
            System.out.println("Users: " + users.size());
            System.out.println("Posts: " + posts.size());
            System.out.println("Comments: " + comments.size());
            System.out.println("Messages: " + messages.size());
            System.out.println("Notifications: " + notifications.size());

            validateData(users, posts, comments, messages, notifications);

            migrate(dataSource, users, posts, comments, messages,
                    notifications);

            System.out.println("\nMigration completed successfully.");

        } catch (Exception e) {
            System.err.println("\nMigration failed. Check the error below.");
            e.printStackTrace();

        } finally {
            DatabaseConfig.close();
        }
    }

    private static void migrate(
            DataSource dataSource,
            List<User> users,
            List<Post> posts,
            List<Comment> comments,
            List<Message> messages,
            List<Notification> notifications) throws SQLException {

        try (Connection connection = dataSource.getConnection()) {

            connection.setAutoCommit(false);

            try {
                ensureDatabaseIsEmpty(connection);

                migrateUsers(connection, users);
                migratePosts(connection, posts);
                migrateFollows(connection, users);
                migrateLikes(connection, posts);
                migrateComments(connection, comments);
                migrateMessages(connection, messages);
                migrateNotifications(connection, notifications);

                connection.commit();

            } catch (Exception e) {
                connection.rollback();
                throw e;
            }
        }
    }

    private static void ensureDatabaseIsEmpty(Connection connection)
            throws SQLException {

        String[] tables = {
                "users", "posts", "follows", "post_likes",
                "comments", "messages", "notifications", "sessions"
        };

        for (String table : tables) {

            String sql = "SELECT EXISTS (SELECT 1 FROM " + table + " LIMIT 1)";

            try (Statement statement = connection.createStatement();
                 ResultSet rs = statement.executeQuery(sql)) {

                rs.next();

                if (rs.getBoolean(1)) {
                    throw new IllegalStateException(
                            "Table '" + table + "' is not empty. "
                            + "Migration stopped to prevent duplicate or "
                            + "conflicting data.");
                }
            }
        }
    }

    private static void migrateUsers(
            Connection connection, List<User> users) throws SQLException {

        String sql = """
                INSERT INTO users
                    (user_id, username, password_hash, bio, created_at)
                VALUES (?, ?, ?, ?, ?)
                """;

        try (PreparedStatement ps = connection.prepareStatement(sql)) {

            for (User user : users) {
                ps.setString(1, user.getUserId());
                ps.setString(2, user.getUsername());
                ps.setString(3, user.getPasswordHash());
                ps.setString(4, user.getBio());
                ps.setObject(5, parseTimestamp(user.getCreatedAt()));
                ps.addBatch();
            }

            ps.executeBatch();
        }

        System.out.println("Migrated users: " + users.size());
    }

    private static void migratePosts(
            Connection connection, List<Post> posts) throws SQLException {

        String sql = """
                INSERT INTO posts
                    (post_id, author_id, content, created_at)
                VALUES (?, ?, ?, ?)
                """;

        try (PreparedStatement ps = connection.prepareStatement(sql)) {

            for (Post post : posts) {
                ps.setString(1, post.getPostId());
                ps.setString(2, post.getAuthorId());
                ps.setString(3, post.getContent());
                ps.setObject(4, parseTimestamp(post.getCreatedAt()));
                ps.addBatch();
            }

            ps.executeBatch();
        }

        System.out.println("Migrated posts: " + posts.size());
    }

    private static void migrateFollows(
            Connection connection, List<User> users) throws SQLException {

        String sql = """
                INSERT INTO follows
                    (follower_id, following_id, created_at)
                VALUES (?, ?, CURRENT_TIMESTAMP)
                ON CONFLICT (follower_id, following_id) DO NOTHING
                """;

        Set<String> relationships = new LinkedHashSet<>();

        for (User user : users) {

            for (String followingId : user.getFollowing()) {
                relationships.add(user.getUserId() + "|" + followingId);
            }

            // Also recover a relationship if it appears only in followers.
            for (String followerId : user.getFollowers()) {
                relationships.add(followerId + "|" + user.getUserId());
            }
        }

        try (PreparedStatement ps = connection.prepareStatement(sql)) {

            for (String relationship : relationships) {
                String[] ids = relationship.split("\\|", 2);

                ps.setString(1, ids[0]);
                ps.setString(2, ids[1]);
                ps.addBatch();
            }

            ps.executeBatch();
        }

        System.out.println("Migrated follow relationships: "
                + relationships.size());
    }

    private static void migrateLikes(
            Connection connection, List<Post> posts) throws SQLException {

        String sql = """
                INSERT INTO post_likes (post_id, user_id, created_at)
                VALUES (?, ?, CURRENT_TIMESTAMP)
                ON CONFLICT (post_id, user_id) DO NOTHING
                """;

        int count = 0;

        try (PreparedStatement ps = connection.prepareStatement(sql)) {

            for (Post post : posts) {

                for (String userId : post.getLikes()) {
                    ps.setString(1, post.getPostId());
                    ps.setString(2, userId);
                    ps.addBatch();
                    count++;
                }
            }

            ps.executeBatch();
        }

        System.out.println("Migrated likes: " + count);
    }

    private static void migrateComments(
            Connection connection, List<Comment> comments)
            throws SQLException {

        String sql = """
                INSERT INTO comments
                    (comment_id, post_id, author_id, content, created_at)
                VALUES (?, ?, ?, ?, ?)
                """;

        try (PreparedStatement ps = connection.prepareStatement(sql)) {

            for (Comment comment : comments) {
                ps.setString(1, comment.getCommentId());
                ps.setString(2, comment.getPostId());
                ps.setString(3, comment.getAuthorId());
                ps.setString(4, comment.getContent());
                ps.setObject(5, parseTimestamp(comment.getCreatedAt()));
                ps.addBatch();
            }

            ps.executeBatch();
        }

        System.out.println("Migrated comments: " + comments.size());
    }

    private static void migrateMessages(
            Connection connection, List<Message> messages)
            throws SQLException {

        String sql = """
                INSERT INTO messages
                    (message_id, sender_id, receiver_id, content,
                     created_at, is_read)
                VALUES (?, ?, ?, ?, ?, ?)
                """;

        try (PreparedStatement ps = connection.prepareStatement(sql)) {

            for (Message message : messages) {
                ps.setString(1, message.getMessageId());
                ps.setString(2, message.getSenderId());
                ps.setString(3, message.getReceiverId());
                ps.setString(4, message.getContent());
                ps.setObject(5, parseTimestamp(message.getCreatedAt()));
                ps.setBoolean(6, message.isRead());
                ps.addBatch();
            }

            ps.executeBatch();
        }

        System.out.println("Migrated messages: " + messages.size());
    }

    private static void migrateNotifications(
            Connection connection, List<Notification> notifications)
            throws SQLException {

        String sql = """
                INSERT INTO notifications
                    (notification_id, receiver_id, actor_id, type,
                     reference_id, message, created_at, is_read)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;

        try (PreparedStatement ps = connection.prepareStatement(sql)) {

            for (Notification notification : notifications) {
                ps.setString(1, notification.getNotificationId());
                ps.setString(2, notification.getReceiverId());
                ps.setString(3, notification.getActorId());
                ps.setString(4, notification.getType());
                ps.setString(5, notification.getReferenceId());
                ps.setString(6, notification.getMessage());
                ps.setObject(7,
                        parseTimestamp(notification.getCreatedAt()));
                ps.setBoolean(8, notification.isRead());
                ps.addBatch();
            }

            ps.executeBatch();
        }

        System.out.println("Migrated notifications: "
                + notifications.size());
    }

    // Supports ISO timestamps with offsets, UTC instants,
    // and legacy LocalDateTime values interpreted as IST.
    private static OffsetDateTime parseTimestamp(String value) {

        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "Encountered a missing timestamp during migration");
        }

        try {
            return Instant.parse(value).atOffset(ZoneOffset.UTC);
        } catch (DateTimeParseException ignored) {
        }

        try {
            return OffsetDateTime.parse(value);
        } catch (DateTimeParseException ignored) {
        }

        try {
            return LocalDateTime.parse(value)
                    .atZone(INDIA)
                    .toOffsetDateTime();
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(
                    "Unrecognized timestamp: " + value, e);
        }
    }

    private static void validateData(
            List<User> users,
            List<Post> posts,
            List<Comment> comments,
            List<Message> messages,
            List<Notification> notifications) {

        Set<String> userIds = new HashSet<>();

        for (User user : users) {
            if (user.getUserId() == null
                    || !userIds.add(user.getUserId())) {
                throw new IllegalArgumentException(
                        "Missing or duplicate user ID");
            }
        }

        Set<String> postIds = new HashSet<>();

        for (Post post : posts) {
            if (post.getPostId() == null
                    || !postIds.add(post.getPostId())) {
                throw new IllegalArgumentException(
                        "Missing or duplicate post ID");
            }

            if (!userIds.contains(post.getAuthorId())) {
                throw new IllegalArgumentException(
                        "Post references a missing author: "
                                + post.getPostId());
            }
        }

        for (User user : users) {
            for (String id : user.getFollowing()) {
                if (!userIds.contains(id)) {
                    throw new IllegalArgumentException(
                            "Follow relationship references missing user: "
                                    + id);
                }
            }

            for (String id : user.getFollowers()) {
                if (!userIds.contains(id)) {
                    throw new IllegalArgumentException(
                            "Follower relationship references missing user: "
                                    + id);
                }
            }
        }

        for (Post post : posts) {
            for (String id : post.getLikes()) {
                if (!userIds.contains(id)) {
                    throw new IllegalArgumentException(
                            "Like references missing user: " + id);
                }
            }
        }

        for (Comment comment : comments) {
            if (!postIds.contains(comment.getPostId())
                    || !userIds.contains(comment.getAuthorId())) {
                throw new IllegalArgumentException(
                        "Comment references missing post or user: "
                                + comment.getCommentId());
            }
        }

        for (Message message : messages) {
            if (!userIds.contains(message.getSenderId())
                    || !userIds.contains(message.getReceiverId())) {
                throw new IllegalArgumentException(
                        "Message references missing user: "
                                + message.getMessageId());
            }
        }

        for (Notification notification : notifications) {
            if (!userIds.contains(notification.getReceiverId())
                    || !userIds.contains(notification.getActorId())) {
                throw new IllegalArgumentException(
                        "Notification references missing user: "
                                + notification.getNotificationId());
            }
        }
    }
}
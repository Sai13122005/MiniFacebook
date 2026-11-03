package com.minifacebook.repositories;

import com.minifacebook.models.Post;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class PostRepository {

    private final DataSource dataSource;

    public PostRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    // Create a post.
    public void insert(Post post) throws SQLException {

        String sql = """
                INSERT INTO posts (post_id, author_id, content, created_at)
                VALUES (?, ?, ?, ?)
                """;

        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, post.getPostId());
            statement.setString(2, post.getAuthorId());
            statement.setString(3, post.getContent());
            statement.setTimestamp(
                    4, Timestamp.from(Instant.parse(post.getCreatedAt())));

            statement.executeUpdate();
        }
    }

    // Find a post by ID.
    public Post findById(String postId) throws SQLException {

        String sql = """
                SELECT post_id, author_id, content, created_at
                FROM posts
                WHERE post_id = ?
                """;

        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, postId);

            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }

                Post post = mapPost(rs);
                loadLikes(connection, List.of(post));
                return post;
            }
        }
    }

    public Post findById(
            Connection connection,
            String postId) throws SQLException {

        String sql = """
                SELECT post_id, author_id, content, created_at
                FROM posts
                WHERE post_id = ?
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, postId);

            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }

                Post post = mapPost(rs);
                loadLikes(connection, List.of(post));

                return post;
            }
        }
    }

    // Retrieve all posts.
    public List<Post> findAll() throws SQLException {

        String sql = """
                SELECT post_id, author_id, content, created_at
                FROM posts
                ORDER BY created_at DESC
                """;

        List<Post> posts = new ArrayList<>();

        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet rs = statement.executeQuery()) {

            while (rs.next()) {
                posts.add(mapPost(rs));
            }

            loadLikes(connection, posts);
        }

        return posts;
    }

    // Retrieve posts created by a particular user.
    public List<Post> findByAuthorId(String authorId)
            throws SQLException {

        String sql = """
                SELECT post_id, author_id, content, created_at
                FROM posts
                WHERE author_id = ?
                ORDER BY created_at DESC
                """;

        List<Post> posts = new ArrayList<>();

        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, authorId);

            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    posts.add(mapPost(rs));
                }
            }

            loadLikes(connection, posts);
        }

        return posts;
    }

    // Retrieve posts by a set of author IDs.
    public List<Post> findByAuthorIds(List<String> authorIds)
            throws SQLException {

        if (authorIds.isEmpty()) {
            return new ArrayList<>();
        }

        String sql = """
                SELECT post_id, author_id, content, created_at
                FROM posts
                WHERE author_id = ANY (?)
                ORDER BY created_at DESC
                """;

        List<Post> posts = new ArrayList<>();

        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {

            Array ids = connection.createArrayOf(
                    "varchar", authorIds.toArray(new String[0]));

            try {
                statement.setArray(1, ids);

                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) {
                        posts.add(mapPost(rs));
                    }
                }
            } finally {
                ids.free();
            }

            loadLikes(connection, posts);
        }

        return posts;
    }

    // Update post content.
    public boolean updateContent(String postId, String content)
            throws SQLException {

        String sql = """
                UPDATE posts
                SET content = ?
                WHERE post_id = ?
                """;

        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, content);
            statement.setString(2, postId);

            return statement.executeUpdate() == 1;
        }
    }

    // Delete a post.
    public boolean delete(
            Connection connection,
            String postId) throws SQLException {

        String sql = "DELETE FROM posts WHERE post_id = ?";

        try (
                PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, postId);
            return statement.executeUpdate() == 1;
        }
    }

    public boolean delete(String postId) throws SQLException {

        try (Connection connection = dataSource.getConnection()) {
            return delete(connection, postId);
        }
    }
    
    // Like a post.

    public boolean addLike(
            String postId,
            String userId) throws SQLException {

        try (Connection connection = dataSource.getConnection()) {
            return addLike(connection, postId, userId);
        }
    }

    public boolean addLike(
            Connection connection,
            String postId,
            String userId) throws SQLException {

        String sql = """
                INSERT INTO post_likes (post_id, user_id, created_at)
                VALUES (?, ?, CURRENT_TIMESTAMP)
                ON CONFLICT (post_id, user_id) DO NOTHING
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, postId);
            statement.setString(2, userId);

            return statement.executeUpdate() == 1;
        }
    }

    // Unlike a post.
    public boolean removeLike(String postId, String userId)
            throws SQLException {

        String sql = """
                DELETE FROM post_likes
                WHERE post_id = ? AND user_id = ?
                """;

        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, postId);
            statement.setString(2, userId);

            return statement.executeUpdate() == 1;
        }
    }

    private Post mapPost(ResultSet rs) throws SQLException {

        Timestamp createdAt = rs.getTimestamp("created_at");

        return new Post(
                rs.getString("post_id"),
                rs.getString("author_id"),
                rs.getString("content"),
                createdAt == null ? null : createdAt.toInstant().toString());
    }

    // Populate the existing Post.likes set.
    private void loadLikes(Connection connection, List<Post> posts)
            throws SQLException {

        if (posts.isEmpty()) {
            return;
        }

        String sql = """
                SELECT post_id, user_id
                FROM post_likes
                WHERE post_id = ANY (?)
                """;

        String[] postIds = posts.stream()
                .map(Post::getPostId)
                .toArray(String[]::new);

        Array ids = connection.createArrayOf("varchar", postIds);

        try (PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setArray(1, ids);

            try (ResultSet rs = statement.executeQuery()) {

                java.util.Map<String, Post> postMap = new java.util.HashMap<>();

                for (Post post : posts) {
                    postMap.put(post.getPostId(), post);
                }

                while (rs.next()) {
                    Post post = postMap.get(rs.getString("post_id"));

                    if (post != null) {
                        post.getLikes().add(rs.getString("user_id"));
                    }
                }
            }
        } finally {
            ids.free();
        }
    }
}
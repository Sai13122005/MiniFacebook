package com.minifacebook.repositories;

import com.minifacebook.models.Comment;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class CommentRepository {

    private final DataSource dataSource;

    public CommentRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    // Insert a new comment.
    public void insert(
            Connection connection,
            Comment comment) throws SQLException {

        String sql = """
                INSERT INTO comments
                    (comment_id, post_id, author_id, content, created_at)
                VALUES (?, ?, ?, ?, ?)
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, comment.getCommentId());
            statement.setString(2, comment.getPostId());
            statement.setString(3, comment.getAuthorId());
            statement.setString(4, comment.getContent());
            statement.setTimestamp(
                    5, Timestamp.from(Instant.parse(comment.getCreatedAt())));

            statement.executeUpdate();
        }
    }

    public void insert(Comment comment) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            insert(connection, comment);
        }
    }

    // Find a comment by its ID.
    public Comment findById(String commentId) throws SQLException {

        String sql = """
                SELECT comment_id, post_id, author_id, content, created_at
                FROM comments
                WHERE comment_id = ?
                """;

        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, commentId);

            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? mapComment(rs) : null;
            }
        }
    }

    // Get all comments associated with a post.
    public List<Comment> findByPostId(String postId) throws SQLException {

        String sql = """
                SELECT comment_id, post_id, author_id, content, created_at
                FROM comments
                WHERE post_id = ?
                ORDER BY created_at ASC
                """;

        List<Comment> comments = new ArrayList<>();

        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, postId);

            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    comments.add(mapComment(rs));
                }
            }
        }

        return comments;
    }

    // Update comment content.
    public boolean updateContent(String commentId, String content)
            throws SQLException {

        String sql = """
                UPDATE comments
                SET content = ?
                WHERE comment_id = ?
                """;

        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, content);
            statement.setString(2, commentId);

            return statement.executeUpdate() == 1;
        }
    }

    // Delete a comment by ID.
    public boolean delete(String commentId) throws SQLException {

        String sql = "DELETE FROM comments WHERE comment_id = ?";

        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, commentId);
            return statement.executeUpdate() == 1;
        }
    }

    // Delete all comments belonging to a post.
    public int deleteByPostId(String postId) throws SQLException {

        String sql = "DELETE FROM comments WHERE post_id = ?";

        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, postId);
            return statement.executeUpdate();
        }
    }

    private Comment mapComment(ResultSet rs) throws SQLException {

        Timestamp createdAt = rs.getTimestamp("created_at");

        return new Comment(
                rs.getString("comment_id"),
                rs.getString("post_id"),
                rs.getString("author_id"),
                rs.getString("content"),
                createdAt == null ? null : createdAt.toInstant().toString());
    }
}
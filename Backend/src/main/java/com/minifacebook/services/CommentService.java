package com.minifacebook.services;

import com.minifacebook.models.Comment;
import com.minifacebook.models.Post;
import com.minifacebook.repositories.CommentRepository;
import com.minifacebook.repositories.PostRepository;
import com.minifacebook.storage.JsonStorage;
import com.minifacebook.config.TransactionManager;
import com.minifacebook.models.Notification;
import com.minifacebook.repositories.NotificationRepository;
import java.io.IOException;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public class CommentService {

    private final JsonStorage storage;
    private final CommentRepository commentRepository;
    private final PostRepository postRepository;
    private final TransactionManager transactionManager;
    private final NotificationRepository notificationRepository;

    // Existing JSON constructor.
    public CommentService(JsonStorage storage) {
        this.storage = storage;
        this.commentRepository = null;
        this.postRepository = null;
        this.transactionManager = null;
        this.notificationRepository = null;
    }

    // PostgreSQL constructor.
    public CommentService(
            CommentRepository commentRepository,
            PostRepository postRepository) {

        this.storage = null;
        this.commentRepository = commentRepository;
        this.postRepository = postRepository;
        this.transactionManager = null;
        this.notificationRepository = null;
    }

    public CommentService(
            CommentRepository commentRepository,
            PostRepository postRepository,
            TransactionManager transactionManager,
            NotificationRepository notificationRepository) {

        this.storage = null;
        this.commentRepository = commentRepository;
        this.postRepository = postRepository;
        this.transactionManager = transactionManager;
        this.notificationRepository = notificationRepository;
    }

    public synchronized Comment addCommentWithNotification(
            String postId,
            String authorId,
            String content,
            String actorName) throws IOException {

        validateContent(content);

        if (transactionManager == null || notificationRepository == null) {
            throw new IllegalStateException(
                    "Transaction support is not configured");
        }

        try {
            return transactionManager.execute(connection -> {

                // Verify the post exists.
                Post post = postRepository.findById(connection, postId);

                if (post == null) {
                    throw new IllegalArgumentException("Post not found");
                }

                // Create the comment.
                Comment comment = new Comment(
                        UUID.randomUUID().toString(),
                        postId,
                        authorId,
                        content.trim(),
                        Instant.now().toString());

                // Insert the comment using the transaction connection.
                commentRepository.insert(connection, comment);

                // Don't notify the author about their own comment.
                if (!post.getAuthorId().equals(authorId)) {

                    Notification notification = new Notification(
                            UUID.randomUUID().toString(),
                            post.getAuthorId(),
                            authorId,
                            "COMMENT",
                            postId,
                            actorName + " commented on your post",
                            Instant.now().toString(),
                            false);

                    notificationRepository.insert(
                            connection,
                            notification);
                }

                return comment;
            });

        } catch (SQLException e) {
            throw databaseError("add comment", e);
        }
    }

    // Add a comment to an existing post.
    public synchronized Comment addComment(
            String postId,
            String authorId,
            String content) throws IOException {

        validateContent(content);

        if (commentRepository != null) {
            try {
                requirePost(postId);

                Comment comment = new Comment(
                        UUID.randomUUID().toString(),
                        postId,
                        authorId,
                        content.trim(),
                        Instant.now().toString());

                commentRepository.insert(comment);
                return comment;

            } catch (SQLException e) {
                throw databaseError("add comment", e);
            }
        }

        List<Post> posts = storage.loadPosts();

        boolean postExists = posts.stream()
                .anyMatch(post -> post.getPostId().equals(postId));

        if (!postExists) {
            throw new IllegalArgumentException("Post not found");
        }

        Comment comment = new Comment(
                UUID.randomUUID().toString(),
                postId,
                authorId,
                content.trim(),
                Instant.now().toString());

        var comments = storage.loadComments();
        comments.add(comment);
        storage.saveComments(comments);

        return comment;
    }

    // Update an existing comment.
    public synchronized Comment updateComment(
            String commentId,
            String userId,
            String content) throws IOException {

        validateContent(content);

        if (commentRepository != null) {
            try {
                Comment comment = requireComment(commentId);

                if (!comment.getAuthorId().equals(userId)) {
                    throw new IllegalArgumentException(
                            "You are not authorized to update this comment");
                }

                String cleanedContent = content.trim();

                if (!commentRepository.updateContent(
                        commentId, cleanedContent)) {
                    throw new IllegalArgumentException("Comment not found");
                }

                comment.setContent(cleanedContent);
                return comment;

            } catch (SQLException e) {
                throw databaseError("update comment", e);
            }
        }

        var comments = storage.loadComments();
        Comment comment = findComment(comments, commentId);

        if (!comment.getAuthorId().equals(userId)) {
            throw new IllegalArgumentException(
                    "You are not authorized to update this comment");
        }

        comment.setContent(content.trim());
        storage.saveComments(comments);

        return comment;
    }

    // Delete an existing comment.
    public synchronized Comment deleteComment(
            String commentId,
            String userId) throws IOException {

        if (commentRepository != null) {
            try {
                Comment comment = requireComment(commentId);

                if (!comment.getAuthorId().equals(userId)) {
                    throw new IllegalArgumentException(
                            "You are not authorized to delete this comment");
                }

                if (!commentRepository.delete(commentId)) {
                    throw new IllegalArgumentException("Comment not found");
                }

                return comment;

            } catch (SQLException e) {
                throw databaseError("delete comment", e);
            }
        }

        var comments = storage.loadComments();
        Comment comment = findComment(comments, commentId);

        if (!comment.getAuthorId().equals(userId)) {
            throw new IllegalArgumentException(
                    "You are not authorized to delete this comment");
        }

        comments.remove(comment);
        storage.saveComments(comments);

        return comment;
    }

    // Delete all comments associated with a post.
    public synchronized void deleteCommentsByPostId(
            String postId) throws IOException {

        if (commentRepository != null) {
            try {
                commentRepository.deleteByPostId(postId);
                return;
            } catch (SQLException e) {
                throw databaseError("delete post comments", e);
            }
        }

        var comments = storage.loadComments();

        boolean changed = comments.removeIf(
                comment -> comment.getPostId().equals(postId));

        if (changed) {
            storage.saveComments(comments);
        }
    }

    // Get all comments for a particular post.
    public synchronized List<Comment> getComments(String postId)
            throws IOException {

        if (commentRepository != null) {
            try {
                requirePost(postId);
                return commentRepository.findByPostId(postId);
            } catch (SQLException e) {
                throw databaseError("retrieve comments", e);
            }
        }

        List<Post> posts = storage.loadPosts();

        boolean postExists = posts.stream()
                .anyMatch(post -> post.getPostId().equals(postId));

        if (!postExists) {
            throw new IllegalArgumentException("Post not found");
        }

        var comments = storage.loadComments();

        return comments.stream()
                .filter(comment -> comment.getPostId().equals(postId))
                .sorted(Comparator.comparing(Comment::getCreatedAt))
                .toList();
    }

    private void requirePost(String postId) throws SQLException {

        Post post = postRepository.findById(postId);

        if (post == null) {
            throw new IllegalArgumentException("Post not found");
        }
    }

    private Comment requireComment(String commentId)
            throws SQLException {

        Comment comment = commentRepository.findById(commentId);

        if (comment == null) {
            throw new IllegalArgumentException("Comment not found");
        }

        return comment;
    }

    private void validateContent(String content) {

        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException(
                    "Comment cannot be empty");
        }

        if (content.length() > 1000) {
            throw new IllegalArgumentException(
                    "Comment cannot exceed 1000 characters");
        }
    }

    private Comment findComment(
            List<Comment> comments, String commentId) {

        for (Comment comment : comments) {
            if (comment.getCommentId().equals(commentId)) {
                return comment;
            }
        }

        throw new IllegalArgumentException("Comment not found");
    }

    private IOException databaseError(
            String operation, SQLException e) {

        return new IOException(
                "Database error while attempting to " + operation, e);
    }
}
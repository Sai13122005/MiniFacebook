
package com.minifacebook.services;

import com.minifacebook.models.Comment;
import com.minifacebook.models.Post;
import com.minifacebook.storage.JsonStorage;

import java.io.IOException;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public class CommentService {

    private final JsonStorage storage;

    public CommentService(JsonStorage storage) {
        this.storage = storage;
    }

    // Add a comment to an existing post
    public synchronized Comment addComment(
            String postId,
            String authorId,
            String content) throws IOException {

        validateContent(content);

        // Verify that the post exists
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
                Instant.now().toString()
        );

        List<Comment> comments = storage.loadComments();

        comments.add(comment);

        storage.saveComments(comments);

        return comment;
    }

    // Update an existing comment
    public synchronized Comment updateComment(
            String commentId,
            String userId,
            String content) throws IOException {

        validateContent(content);

        List<Comment> comments = storage.loadComments();

        Comment comment = findComment(comments, commentId);

        // Only the original author can update the comment
        if (!comment.getAuthorId().equals(userId)) {
            throw new IllegalArgumentException(
                    "You are not authorized to update this comment");
        }

        comment.setContent(content.trim());

        storage.saveComments(comments);

        return comment;
    }

    // Delete an existing comment
    public synchronized Comment deleteComment(
            String commentId,
            String userId) throws IOException {

        List<Comment> comments = storage.loadComments();

        Comment comment = findComment(comments, commentId);

        // Only the original author can delete the comment
        if (!comment.getAuthorId().equals(userId)) {
            throw new IllegalArgumentException(
                    "You are not authorized to delete this comment");
        }

        comments.remove(comment);

        storage.saveComments(comments);

        return comment;
    }

    // Delete all comments associated with a post
    public synchronized void deleteCommentsByPostId(
            String postId) throws IOException {

        List<Comment> comments = storage.loadComments();

        boolean changed = comments.removeIf(
                comment -> comment.getPostId().equals(postId));

        if (changed) {
            storage.saveComments(comments);
        }
    }

    // Get all comments for a particular post
    public synchronized List<Comment> getComments(String postId)
            throws IOException {

        List<Post> posts = storage.loadPosts();

        boolean postExists = posts.stream()
                .anyMatch(post -> post.getPostId().equals(postId));

        if (!postExists) {
            throw new IllegalArgumentException("Post not found");
        }

        List<Comment> comments = storage.loadComments();

        return comments.stream()
                .filter(comment ->
                        comment.getPostId().equals(postId))
                .sorted(Comparator.comparing(Comment::getCreatedAt))
                .toList();
    }

    // Validate comment content
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

    // Find a comment by ID
    private Comment findComment(
            List<Comment> comments, String commentId) {

        for (Comment comment : comments) {
            if (comment.getCommentId().equals(commentId)) {
                return comment;
            }
        }

        throw new IllegalArgumentException("Comment not found");
    }
}

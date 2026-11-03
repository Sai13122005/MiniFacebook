package com.minifacebook.services;

import com.minifacebook.models.Post;
import com.minifacebook.models.User;
import com.minifacebook.repositories.PostRepository;
import com.minifacebook.repositories.UserRepository;
import com.minifacebook.storage.JsonStorage;
import com.minifacebook.config.TransactionManager;
import com.minifacebook.models.Notification;
import com.minifacebook.repositories.NotificationRepository;
import java.io.IOException;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class PostService {

    private final JsonStorage storage;
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final TransactionManager transactionManager;
    private final NotificationRepository notificationRepository;

    // Existing JSON constructor.
    public PostService(JsonStorage storage) {
        this.storage = storage;
        this.postRepository = null;
        this.userRepository = null;
        this.transactionManager = null;
        this.notificationRepository = null;
    }

    // New PostgreSQL constructor.
    public PostService(
            PostRepository postRepository,
            UserRepository userRepository) {

        this.storage = null;
        this.postRepository = postRepository;
        this.userRepository = userRepository;
        this.transactionManager = null;
        this.notificationRepository = null;
    }

    public PostService(
            PostRepository postRepository,
            UserRepository userRepository,
            TransactionManager transactionManager,
            NotificationRepository notificationRepository) {

        this.storage = null;
        this.postRepository = postRepository;
        this.userRepository = userRepository;
        this.transactionManager = transactionManager;
        this.notificationRepository = notificationRepository;
    }

    public void likePostWithNotification(
            String userId,
            String postId,
            String actorName) throws IOException {

        if (transactionManager == null || notificationRepository == null) {
            throw new IllegalStateException(
                    "Transaction support is not configured");
        }

        try {
            transactionManager.execute(connection -> {

                // Verify that the post exists.
                Post post = postRepository.findById(connection, postId);

                if (post == null) {
                    throw new IllegalArgumentException("Post not found");
                }

                // Insert the like using the transaction connection.
                boolean liked = postRepository.addLike(
                        connection,
                        postId,
                        userId);

                if (!liked) {
                    throw new IllegalArgumentException(
                            "You have already liked this post");
                }

                // Create notification within the same transaction.
                Notification notification = new Notification(
                        UUID.randomUUID().toString(),
                        post.getAuthorId(),
                        userId,
                        "LIKE",
                        postId,
                        actorName + " liked your post",
                        Instant.now().toString(),
                        false);

                notificationRepository.insert(
                        connection,
                        notification);

                return null;
            });

        } catch (SQLException e) {
            throw databaseError("like post", e);
        }
    }

    public void deletePostTransactionally(
            String postId,
            String userId) throws IOException {

        if (transactionManager == null) {
            throw new IllegalStateException(
                    "Transaction support is not configured");
        }

        try {
            transactionManager.execute(connection -> {

                Post post = postRepository.findById(connection, postId);

                if (post == null) {
                    throw new IllegalArgumentException("Post not found");
                }

                if (!post.getAuthorId().equals(userId)) {
                    throw new IllegalArgumentException(
                            "You cannot delete another user's post");
                }

                boolean deleted = postRepository.delete(
                        connection,
                        postId);

                if (!deleted) {
                    throw new SQLException(
                            "Post could not be deleted");
                }

                return null;
            });

        } catch (SQLException e) {
            throw databaseError("delete post", e);
        }
    }

    // Create a new post.
    public synchronized Post createPost(
            String authorId, String content) throws IOException {

        validateContent(content);

        Post post = new Post(
                UUID.randomUUID().toString(),
                authorId,
                content.trim(),
                Instant.now().toString());

        if (postRepository != null) {
            try {
                postRepository.insert(post);
                return post;
            } catch (SQLException e) {
                throw databaseError("create post", e);
            }
        }

        List<Post> posts = storage.loadPosts();
        posts.add(post);
        storage.savePosts(posts);

        return post;
    }

    // Update an existing post.
    public synchronized Post updatePost(
            String postId, String userId, String content)
            throws IOException {

        validateContent(content);

        Post post = getPostById(postId);

        if (!post.getAuthorId().equals(userId)) {
            throw new IllegalArgumentException(
                    "You are not authorized to update this post");
        }

        post.setContent(content.trim());

        if (postRepository != null) {
            try {
                if (!postRepository.updateContent(
                        postId, content.trim())) {
                    throw new IllegalArgumentException("Post not found");
                }

                return post;
            } catch (SQLException e) {
                throw databaseError("update post", e);
            }
        }

        List<Post> posts = storage.loadPosts();
        storage.savePosts(posts);

        return post;
    }

    // Delete an existing post.
    public synchronized Post deletePost(
            String postId, String userId) throws IOException {

        Post post = getPostById(postId);

        if (!post.getAuthorId().equals(userId)) {
            throw new IllegalArgumentException(
                    "You are not authorized to delete this post");
        }

        if (postRepository != null) {
            try {
                if (!postRepository.delete(postId)) {
                    throw new IllegalArgumentException("Post not found");
                }

                return post;
            } catch (SQLException e) {
                throw databaseError("delete post", e);
            }
        }

        List<Post> posts = storage.loadPosts();
        posts.removeIf(p -> p.getPostId().equals(postId));
        storage.savePosts(posts);

        return post;
    }

    // Get posts from the current user and followed users.
    public synchronized List<Post> getFeed(
            String currentUserId) throws IOException {

        if (postRepository != null) {
            try {
                User currentUser = userRepository.findById(currentUserId);

                if (currentUser == null) {
                    throw new IllegalArgumentException("User not found");
                }

                List<String> allowedAuthors = new ArrayList<>(currentUser.getFollowing());

                allowedAuthors.add(currentUserId);

                return postRepository.findByAuthorIds(allowedAuthors);

            } catch (SQLException e) {
                throw databaseError("retrieve feed", e);
            }
        }

        List<User> users = storage.loadUsers();

        User currentUser = null;

        for (User user : users) {
            if (user.getUserId().equals(currentUserId)) {
                currentUser = user;
                break;
            }
        }

        if (currentUser == null) {
            throw new IllegalArgumentException("User not found");
        }

        List<String> allowedAuthors = new ArrayList<>(currentUser.getFollowing());

        allowedAuthors.add(currentUserId);

        List<Post> posts = storage.loadPosts();

        return posts.stream()
                .filter(post -> allowedAuthors.contains(post.getAuthorId()))
                .sorted(Comparator.comparing(Post::getCreatedAt).reversed())
                .toList();
    }

    // Get posts by a particular user.
    public synchronized List<Post> getPostsByUser(String userId)
            throws IOException {

        if (postRepository != null) {
            try {
                return postRepository.findByAuthorId(userId);
            } catch (SQLException e) {
                throw databaseError("retrieve user posts", e);
            }
        }

        List<Post> posts = storage.loadPosts();

        return posts.stream()
                .filter(post -> post.getAuthorId().equals(userId))
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .collect(Collectors.toList());
    }

    // Like a post.
    public synchronized void likePost(
            String userId, String postId) throws IOException {

        if (postRepository != null) {
            try {
                if (postRepository.findById(postId) == null) {
                    throw new IllegalArgumentException("Post not found");
                }

                if (!postRepository.addLike(postId, userId)) {
                    throw new IllegalArgumentException(
                            "You have already liked this post");
                }

                return;
            } catch (SQLException e) {
                throw databaseError("like post", e);
            }
        }

        List<Post> posts = storage.loadPosts();
        Post post = findPost(posts, postId);

        if (!post.getLikes().add(userId)) {
            throw new IllegalArgumentException(
                    "You have already liked this post");
        }

        storage.savePosts(posts);
    }

    // Unlike a post.
    public synchronized void unlikePost(
            String userId, String postId) throws IOException {

        if (postRepository != null) {
            try {
                if (postRepository.findById(postId) == null) {
                    throw new IllegalArgumentException("Post not found");
                }

                if (!postRepository.removeLike(postId, userId)) {
                    throw new IllegalArgumentException(
                            "You have not liked this post");
                }

                return;
            } catch (SQLException e) {
                throw databaseError("unlike post", e);
            }
        }

        List<Post> posts = storage.loadPosts();
        Post post = findPost(posts, postId);

        if (!post.getLikes().remove(userId)) {
            throw new IllegalArgumentException(
                    "You have not liked this post");
        }

        storage.savePosts(posts);
    }

    // Get a post by ID.
    public synchronized Post getPostById(String postId)
            throws IOException {

        if (postRepository != null) {
            try {
                Post post = postRepository.findById(postId);

                if (post == null) {
                    throw new IllegalArgumentException("Post not found");
                }

                return post;
            } catch (SQLException e) {
                throw databaseError("retrieve post", e);
            }
        }

        List<Post> posts = storage.loadPosts();
        return findPost(posts, postId);
    }

    private void validateContent(String content) {

        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException(
                    "Post content cannot be empty");
        }

        if (content.length() > 2000) {
            throw new IllegalArgumentException(
                    "Post cannot exceed 2000 characters");
        }
    }

    private Post findPost(List<Post> posts, String postId) {

        for (Post post : posts) {
            if (post.getPostId().equals(postId)) {
                return post;
            }
        }

        throw new IllegalArgumentException("Post not found");
    }

    private IOException databaseError(
            String operation, SQLException e) {

        return new IOException(
                "Database error while attempting to " + operation, e);
    }
}
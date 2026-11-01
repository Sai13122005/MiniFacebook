
package com.minifacebook.services;

import com.minifacebook.models.Post;
import com.minifacebook.models.User;
import com.minifacebook.storage.JsonStorage;

import java.io.IOException;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class PostService {

    private final JsonStorage storage;

    public PostService(JsonStorage storage) {
        this.storage = storage;
    }

    // Create a new post
    public synchronized Post createPost(
            String authorId, String content) throws IOException {

        validateContent(content);

        List<Post> posts = storage.loadPosts();

        Post post = new Post(
                UUID.randomUUID().toString(),
                authorId,
                content.trim(),
                Instant.now().toString());

        posts.add(post);
        storage.savePosts(posts);

        return post;
    }

    // Update an existing post
    public synchronized Post updatePost(
            String postId, String userId, String content)
            throws IOException {

        validateContent(content);

        List<Post> posts = storage.loadPosts();

        Post post = findPost(posts, postId);

        // Only the original author can update the post
        if (!post.getAuthorId().equals(userId)) {
            throw new IllegalArgumentException(
                    "You are not authorized to update this post");
        }

        post.setContent(content.trim());

        storage.savePosts(posts);

        return post;
    }

    // Delete an existing post
    public synchronized Post deletePost(
            String postId, String userId) throws IOException {

        List<Post> posts = storage.loadPosts();

        Post post = findPost(posts, postId);

        // Only the original author can delete the post
        if (!post.getAuthorId().equals(userId)) {
            throw new IllegalArgumentException(
                    "You are not authorized to delete this post");
        }

        posts.remove(post);
        storage.savePosts(posts);

        return post;
    }

    // Get posts from the current user and followed users
    public synchronized List<Post> getFeed(
            String currentUserId) throws IOException {

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

        List<String> allowedAuthors = new java.util.ArrayList<>(
                currentUser.getFollowing());

        allowedAuthors.add(currentUserId);

        List<Post> posts = storage.loadPosts();

        return posts.stream()
                .filter(post -> allowedAuthors.contains(post.getAuthorId()))
                .sorted(Comparator.comparing(Post::getCreatedAt).reversed())
                .toList();
    }

    // Get posts by a particular user
    public synchronized List<Post> getPostsByUser(String userId)
            throws IOException {

        List<Post> posts = storage.loadPosts();

        return posts.stream()
                .filter(post -> post.getAuthorId().equals(userId))
                .sorted((a, b) ->
                        b.getCreatedAt().compareTo(a.getCreatedAt()))
                .collect(Collectors.toList());
    }

    // Like a post
    public synchronized void likePost(
            String userId, String postId) throws IOException {

        List<Post> posts = storage.loadPosts();

        Post post = findPost(posts, postId);

        if (!post.getLikes().add(userId)) {
            throw new IllegalArgumentException(
                    "You have already liked this post");
        }

        storage.savePosts(posts);
    }

    // Unlike a post
    public synchronized void unlikePost(
            String userId, String postId) throws IOException {

        List<Post> posts = storage.loadPosts();

        Post post = findPost(posts, postId);

        if (!post.getLikes().remove(userId)) {
            throw new IllegalArgumentException(
                    "You have not liked this post");
        }

        storage.savePosts(posts);
    }

    // Get a post by ID
    public synchronized Post getPostById(String postId)
            throws IOException {

        List<Post> posts = storage.loadPosts();

        return findPost(posts, postId);
    }

    // Validate post content
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

    // Find a post by ID
    private Post findPost(List<Post> posts, String postId) {

        for (Post post : posts) {
            if (post.getPostId().equals(postId)) {
                return post;
            }
        }

        throw new IllegalArgumentException("Post not found");
    }
}

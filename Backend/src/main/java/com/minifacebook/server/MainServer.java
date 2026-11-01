
package com.minifacebook.server;

import com.minifacebook.models.Message;
import com.minifacebook.services.MessageService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.minifacebook.models.Comment;
import com.minifacebook.services.CommentService;
import com.minifacebook.models.User;
import com.minifacebook.protocol.MessageProtocol;
import com.minifacebook.services.AuthService;
import com.minifacebook.services.UserService;
import com.minifacebook.storage.JsonStorage;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import com.minifacebook.models.Post;
import com.minifacebook.services.PostService;
import com.minifacebook.models.Notification;
import com.minifacebook.services.NotificationService;

public class MainServer {

    private static final int PORT = 5000;

    private static final ExecutorService threadPool = Executors.newFixedThreadPool(10);

    private static final ObjectMapper mapper = new ObjectMapper();
    private static final MessageService messageService = new MessageService(new JsonStorage());
    private static final AuthService authService = new AuthService(new JsonStorage());
    private static final CommentService commentService = new CommentService(new JsonStorage());
    private static final UserService userService = new UserService(new JsonStorage());
    private static final PostService postService = new PostService(new JsonStorage());
    private static final NotificationService notificationService = new NotificationService(new JsonStorage());
    // Maps session tokens to authenticated user IDs
    private static final ConcurrentHashMap<String, String> sessions = new ConcurrentHashMap<>();

    public static void main(String[] args) {

        System.out.println("MiniFacebook TCP Server starting...");

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {

            System.out.println("Server running on port " + PORT);
            System.out.println("Waiting for clients...");

            while (true) {

                Socket clientSocket = serverSocket.accept();

                System.out.println("Client connected: "
                        + clientSocket.getRemoteSocketAddress());

                threadPool.submit(() -> handleClient(clientSocket));
            }

        } catch (IOException e) {
            System.out.println("Server error: " + e.getMessage());
        } finally {
            threadPool.shutdown();
        }
    }

    private static void handleClient(Socket socket) {

        try (Socket client = socket) {

            while (true) {

                JsonNode request = MessageProtocol.readMessage(
                        client.getInputStream());

                if (request == null) {
                    break;
                }

                ObjectNode response = processRequest(request);

                MessageProtocol.writeMessage(
                        client.getOutputStream(), response);
            }

        } catch (IOException e) {
            System.out.println("Client disconnected: " + e.getMessage());
        }

        System.out.println("Client handler completed.");
    }

    private static ObjectNode processRequest(JsonNode request) {

        ObjectNode response = mapper.createObjectNode();

        try {

            String command = request.path("command").asText();

            switch (command.toUpperCase()) {

                case "REGISTER": {

                    String username = request.path("username").asText();

                    String password = request.path("password").asText();

                    User user = authService.register(username, password);

                    response.put("status", "SUCCESS");
                    response.put("message", "Registration successful");

                    ObjectNode userData = response.putObject("user");
                    userData.put("userId", user.getUserId());
                    userData.put("username", user.getUsername());

                    break;
                }

                case "LOGIN": {

                    String username = request.path("username").asText();

                    String password = request.path("password").asText();

                    User user = authService.login(username, password);

                    String token = UUID.randomUUID().toString();

                    sessions.put(token, user.getUserId());

                    response.put("status", "SUCCESS");
                    response.put("message", "Login successful");
                    response.put("sessionToken", token);

                    ObjectNode userData = response.putObject("user");
                    userData.put("userId", user.getUserId());
                    userData.put("username", user.getUsername());

                    break;
                }

                case "LOGOUT": {

                    String token = request.path("sessionToken").asText();

                    if (sessions.remove(token) != null) {
                        response.put("status", "SUCCESS");
                        response.put("message", "Logged out successfully");
                    } else {
                        response.put("status", "ERROR");
                        response.put("message", "Invalid session");
                    }

                    break;
                }

                case "GET_USERS": {

                    String token = request.path("sessionToken").asText();
                    String currentUserId = sessions.get(token);

                    if (currentUserId == null) {
                        throw new IllegalArgumentException("Please login first");
                    }

                    var users = userService.getAllUsers(currentUserId);

                    var usersArray = response.putArray("users");

                    User currentUser = userService.getUserById(currentUserId);

                    for (User user : users) {

                        ObjectNode userData = usersArray.addObject();

                        userData.put("userId", user.getUserId());
                        userData.put("username", user.getUsername());
                        userData.put("bio", user.getBio());
                        userData.put("followersCount", user.getFollowers().size());
                        userData.put("followingCount", user.getFollowing().size());

                        boolean isFollowing = currentUser.getFollowing().contains(user.getUserId());

                        boolean isFollowedBy = currentUser.getFollowers().contains(user.getUserId());

                        userData.put("isFollowing", isFollowing);

                        userData.put(
                                "isMutualConnection",
                                isFollowing && isFollowedBy);
                    }

                    response.put("status", "SUCCESS");

                    break;
                }

                case "GET_MY_PROFILE": {

                    String token = request.path("sessionToken").asText();
                    String currentUserId = sessions.get(token);

                    if (currentUserId == null) {
                        throw new IllegalArgumentException("Please login first");
                    }

                    User user = userService.getUserById(currentUserId);

                    response.put("status", "SUCCESS");

                    ObjectNode userData = response.putObject("user");

                    userData.put("userId", user.getUserId());
                    userData.put("username", user.getUsername());
                    userData.put("bio", user.getBio() == null ? "" : user.getBio());
                    userData.put("createdAt", user.getCreatedAt() == null ? "" : user.getCreatedAt());
                    userData.put("followersCount", user.getFollowers().size());
                    userData.put("followingCount", user.getFollowing().size());

                    break;
                }
                case "GET_MY_POSTS": {

                    String token = request.path("sessionToken").asText();
                    String currentUserId = sessions.get(token);

                    if (currentUserId == null) {
                        throw new IllegalArgumentException("Please login first");
                    }

                    var posts = postService.getPostsByUser(currentUserId);

                    var postsArray = response.putArray("posts");

                    for (Post post : posts) {

                        ObjectNode postData = postsArray.addObject();

                        postData.put("postId", post.getPostId());
                        postData.put("authorId", post.getAuthorId());
                        postData.put("content", post.getContent());
                        postData.put("createdAt", post.getCreatedAt());
                        postData.put("authorUsername",
                                userService.getUserById(post.getAuthorId()).getUsername());

                        postData.put("likesCount", post.getLikes().size());

                        postData.put("isLiked",
                                post.getLikes().contains(currentUserId));

                        postData.put("commentsCount",
                                commentService.getComments(post.getPostId()).size());
                    }

                    response.put("status", "SUCCESS");

                    break;
                }
                case "UPDATE_PROFILE": {

                    String token = request.path("sessionToken").asText();
                    String currentUserId = sessions.get(token);

                    if (currentUserId == null) {
                        throw new IllegalArgumentException("Please login first");
                    }

                    String bio = request.path("bio").asText(null);

                    userService.updateBio(currentUserId, bio);

                    User updatedUser = userService.getUserById(currentUserId);

                    response.put("status", "SUCCESS");
                    response.put("message", "Profile updated successfully");

                    ObjectNode userData = response.putObject("user");

                    userData.put("userId", updatedUser.getUserId());
                    userData.put("username", updatedUser.getUsername());
                    userData.put("bio", updatedUser.getBio() == null
                            ? ""
                            : updatedUser.getBio());
                    userData.put("createdAt", updatedUser.getCreatedAt() == null
                            ? ""
                            : updatedUser.getCreatedAt());
                    userData.put("followersCount", updatedUser.getFollowers().size());
                    userData.put("followingCount", updatedUser.getFollowing().size());

                    break;
                }

                case "FOLLOW_USER": {

                    String token = request.path("sessionToken").asText();
                    String currentUserId = sessions.get(token);

                    if (currentUserId == null) {
                        throw new IllegalArgumentException("Please login first");
                    }

                    String targetUserId = request.path("targetUserId").asText();

                    userService.followUser(currentUserId, targetUserId);
                    String actorName = userService.getUserById(currentUserId).getUsername();

                    notificationService.createNotification(
                            targetUserId,
                            currentUserId,
                            "FOLLOW",
                            currentUserId,
                            actorName + " started following you");

                    response.put("status", "SUCCESS");
                    response.put("message", "User followed successfully");

                    break;
                }

                case "UNFOLLOW_USER": {

                    String token = request.path("sessionToken").asText();
                    String currentUserId = sessions.get(token);

                    if (currentUserId == null) {
                        throw new IllegalArgumentException("Please login first");
                    }

                    String targetUserId = request.path("targetUserId").asText();

                    userService.unfollowUser(currentUserId, targetUserId);

                    response.put("status", "SUCCESS");
                    response.put("message", "User unfollowed successfully");

                    break;
                }

                case "CREATE_POST": {

                    String token = request.path("sessionToken").asText();
                    String currentUserId = sessions.get(token);

                    if (currentUserId == null) {
                        throw new IllegalArgumentException("Please login first");
                    }

                    String content = request.path("content").asText();

                    Post post = postService.createPost(currentUserId, content);

                    response.put("status", "SUCCESS");
                    response.put("message", "Post created successfully");

                    ObjectNode postData = response.putObject("post");
                    postData.put("postId", post.getPostId());
                    postData.put("content", post.getContent());
                    postData.put("createdAt", post.getCreatedAt());

                    break;
                }

                case "GET_FEED": {

                    String token = request.path("sessionToken").asText();
                    String currentUserId = sessions.get(token);
                    if (currentUserId == null) {
                        throw new IllegalArgumentException("Please login first");
                    }

                    var posts = postService.getFeed(currentUserId);
                    var feedArray = response.putArray("posts");

                    for (Post post : posts) {

                        ObjectNode postData = feedArray.addObject();

                        postData.put("postId", post.getPostId());
                        postData.put("authorId", post.getAuthorId());
                        postData.put("content", post.getContent());
                        postData.put("createdAt", post.getCreatedAt());

                        User author = userService.getUserById(post.getAuthorId());

                        postData.put("authorUsername", author.getUsername());
                        postData.put("likesCount", post.getLikes().size());
                        postData.put(
                                "isLiked",
                                post.getLikes().contains(currentUserId));
                    }

                    response.put("status", "SUCCESS");
                    break;
                }

                case "LIKE_POST": {

                    String token = request.path("sessionToken").asText();
                    String currentUserId = sessions.get(token);

                    if (currentUserId == null) {
                        throw new IllegalArgumentException("Please login first");
                    }

                    String postId = request.path("postId").asText();

                    Post post = postService.getPostById(postId);

                    postService.likePost(currentUserId, postId);

                    String actorName = userService.getUserById(currentUserId).getUsername();

                    notificationService.createNotification(
                            post.getAuthorId(),
                            currentUserId,
                            "LIKE",
                            postId,
                            actorName + " liked your post");

                    response.put("status", "SUCCESS");
                    response.put("message", "Post liked successfully");

                    break;
                }

                case "UNLIKE_POST": {

                    String token = request.path("sessionToken").asText();
                    String currentUserId = sessions.get(token);

                    if (currentUserId == null) {
                        throw new IllegalArgumentException("Please login first");
                    }

                    String postId = request.path("postId").asText();

                    postService.unlikePost(currentUserId, postId);

                    response.put("status", "SUCCESS");
                    response.put("message", "Post unliked successfully");

                    break;
                }

                case "ADD_COMMENT": {

                    String token = request.path("sessionToken").asText();
                    String currentUserId = sessions.get(token);

                    if (currentUserId == null) {
                        throw new IllegalArgumentException("Please login first");
                    }

                    String postId = request.path("postId").asText();
                    String content = request.path("content").asText();

                    Comment comment = commentService.addComment(
                            postId, currentUserId, content);
                    Post post = postService.getPostById(postId);

                    String actorName = userService.getUserById(currentUserId).getUsername();

                    notificationService.createNotification(
                            post.getAuthorId(),
                            currentUserId,
                            "COMMENT",
                            postId,
                            actorName + " commented on your post");
                    response.put("status", "SUCCESS");
                    response.put("message", "Comment added successfully");

                    ObjectNode commentData = response.putObject("comment");

                    commentData.put("commentId", comment.getCommentId());
                    commentData.put("postId", comment.getPostId());
                    commentData.put("content", comment.getContent());
                    commentData.put("createdAt", comment.getCreatedAt());
                    commentData.put("authorUsername",
                            userService.getUserById(currentUserId).getUsername());

                    break;
                }

                case "GET_COMMENTS": {

                    String token = request.path("sessionToken").asText();
                    String currentUserId = sessions.get(token);

                    if (currentUserId == null) {
                        throw new IllegalArgumentException("Please login first");
                    }

                    String postId = request.path("postId").asText();

                    var comments = commentService.getComments(postId);

                    var commentsArray = response.putArray("comments");

                    for (Comment comment : comments) {

                        ObjectNode commentData = commentsArray.addObject();

                        commentData.put("commentId", comment.getCommentId());
                        commentData.put("postId", comment.getPostId());
                        commentData.put("authorId", comment.getAuthorId());
                        commentData.put("content", comment.getContent());
                        commentData.put("createdAt", comment.getCreatedAt());

                        User author = userService.getUserById(comment.getAuthorId());

                        commentData.put("authorUsername", author.getUsername());
                    }

                    response.put("status", "SUCCESS");

                    break;
                }

                case "UPDATE_POST": {

                    String token = request.path("sessionToken").asText();
                    String currentUserId = sessions.get(token);

                    if (currentUserId == null) {
                        throw new IllegalArgumentException("Please login first");
                    }

                    String postId = request.path("postId").asText();
                    String content = request.path("content").asText();

                    Post updatedPost = postService.updatePost(
                            postId, currentUserId, content);

                    response.put("status", "SUCCESS");
                    response.put("message", "Post updated successfully");

                    ObjectNode postData = response.putObject("post");

                    postData.put("postId", updatedPost.getPostId());
                    postData.put("content", updatedPost.getContent());
                    postData.put("createdAt", updatedPost.getCreatedAt());

                    break;
                }

                case "DELETE_POST": {

                    String token = request.path("sessionToken").asText();
                    String currentUserId = sessions.get(token);

                    if (currentUserId == null) {
                        throw new IllegalArgumentException("Please login first");
                    }

                    String postId = request.path("postId").asText();

                    // Verify ownership and delete the post
                    Post deletedPost = postService.deletePost(
                            postId, currentUserId);

                    // Delete all comments associated with this post
                    commentService.deleteCommentsByPostId(
                            deletedPost.getPostId());

                    response.put("status", "SUCCESS");
                    response.put("message",
                            "Post and its comments deleted successfully");

                    break;
                }

                case "UPDATE_COMMENT": {

                    String token = request.path("sessionToken").asText();
                    String currentUserId = sessions.get(token);

                    if (currentUserId == null) {
                        throw new IllegalArgumentException("Please login first");
                    }

                    String commentId = request.path("commentId").asText();
                    String content = request.path("content").asText();

                    Comment updatedComment = commentService.updateComment(
                            commentId, currentUserId, content);

                    response.put("status", "SUCCESS");
                    response.put("message", "Comment updated successfully");

                    ObjectNode commentData = response.putObject("comment");

                    commentData.put("commentId", updatedComment.getCommentId());
                    commentData.put("postId", updatedComment.getPostId());
                    commentData.put("content", updatedComment.getContent());
                    commentData.put("createdAt", updatedComment.getCreatedAt());

                    commentData.put("authorUsername",
                            userService.getUserById(currentUserId).getUsername());

                    break;
                }

                case "DELETE_COMMENT": {

                    String token = request.path("sessionToken").asText();
                    String currentUserId = sessions.get(token);

                    if (currentUserId == null) {
                        throw new IllegalArgumentException("Please login first");
                    }

                    String commentId = request.path("commentId").asText();

                    Comment deletedComment = commentService.deleteComment(
                            commentId, currentUserId);

                    response.put("status", "SUCCESS");
                    response.put("message", "Comment deleted successfully");

                    response.put("commentId", deletedComment.getCommentId());

                    break;
                }

                case "GET_NOTIFICATIONS": {

                    String token = request.path("sessionToken").asText();
                    String currentUserId = sessions.get(token);

                    if (currentUserId == null) {
                        throw new IllegalArgumentException("Please login first");
                    }

                    var notifications = notificationService.getNotifications(currentUserId);

                    var notificationArray = response.putArray("notifications");

                    int unreadCount = 0;

                    for (Notification notification : notifications) {

                        ObjectNode item = notificationArray.addObject();

                        item.put("notificationId",
                                notification.getNotificationId());

                        item.put("actorId", notification.getActorId());

                        item.put("type", notification.getType());

                        item.put("referenceId",
                                notification.getReferenceId());

                        item.put("message", notification.getMessage());

                        item.put("createdAt", notification.getCreatedAt());

                        item.put("read", notification.isRead());

                        User actor = userService.getUserById(
                                notification.getActorId());

                        item.put("actorUsername", actor.getUsername());

                        if (!notification.isRead()) {
                            unreadCount++;
                        }
                    }

                    response.put("status", "SUCCESS");
                    response.put("unreadCount", unreadCount);

                    break;
                }

                case "MARK_NOTIFICATION_READ": {

                    String token = request.path("sessionToken").asText();
                    String currentUserId = sessions.get(token);

                    if (currentUserId == null) {
                        throw new IllegalArgumentException("Please login first");
                    }

                    String notificationId = request.path("notificationId").asText();

                    notificationService.markAsRead(
                            currentUserId, notificationId);

                    response.put("status", "SUCCESS");
                    response.put("message", "Notification marked as read");

                    break;
                }
                case "VALIDATE_SESSION": {
                    String token = request.path("sessionToken").asText();
                    String userId = sessions.get(token);

                    if (userId == null) {
                        throw new IllegalArgumentException("Invalid session");
                    }

                    response.put("status", "SUCCESS");
                    response.put("userId", userId);
                    break;
                }

                case "SEND_MESSAGE": {

                    String token = request.path("sessionToken").asText();
                    String senderId = sessions.get(token);

                    if (senderId == null) {
                        throw new IllegalArgumentException("Please login first");
                    }

                    String receiverId = request.path("receiverId").asText();
                    String content = request.path("content").asText();

                    // Verify that the receiver exists
                    userService.getUserById(receiverId);

                    Message message = messageService.sendMessage(
                            senderId,
                            receiverId,
                            content);

                    response.put("status", "SUCCESS");
                    response.put("message", "Message sent successfully");

                    ObjectNode messageData = response.putObject("messageData");

                    messageData.put("messageId", message.getMessageId());
                    messageData.put("senderId", message.getSenderId());
                    messageData.put("receiverId", message.getReceiverId());
                    messageData.put("content", message.getContent());
                    messageData.put("createdAt", message.getCreatedAt());
                    messageData.put("read", message.isRead());

                    break;
                }

                case "GET_CONVERSATIONS": {

                    String token = request.path("sessionToken").asText();
                    String currentUserId = sessions.get(token);

                    if (currentUserId == null) {
                        throw new IllegalArgumentException("Please login first");
                    }

                    var userIds = messageService.getConversationUserIds(currentUserId);

                    var conversations = response.putArray("conversations");

                    for (String otherUserId : userIds) {

                        User otherUser = userService.getUserById(otherUserId);

                        ObjectNode item = conversations.addObject();

                        item.put("userId", otherUser.getUserId());
                        item.put("username", otherUser.getUsername());

                        int unreadCount = messageService.getUnreadCount(
                                currentUserId,
                                otherUserId);

                        item.put("unreadCount", unreadCount);
                    }

                    response.put("status", "SUCCESS");

                    break;
                }

                case "GET_CONVERSATION": {

                    String token = request.path("sessionToken").asText();
                    String currentUserId = sessions.get(token);

                    if (currentUserId == null) {
                        throw new IllegalArgumentException("Please login first");
                    }

                    String otherUserId = request.path("otherUserId").asText();

                    // Verify that the other user exists
                    userService.getUserById(otherUserId);

                    var messages = messageService.getConversation(
                            currentUserId,
                            otherUserId);

                    var messagesArray = response.putArray("messages");

                    for (Message message : messages) {

                        ObjectNode item = messagesArray.addObject();

                        item.put("messageId", message.getMessageId());
                        item.put("senderId", message.getSenderId());
                        item.put("receiverId", message.getReceiverId());
                        item.put("content", message.getContent());
                        item.put("createdAt", message.getCreatedAt());
                        item.put("read", message.isRead());
                    }

                    response.put("status", "SUCCESS");

                    break;
                }

                case "MARK_MESSAGE_READ": {

                    String token = request.path("sessionToken").asText();
                    String currentUserId = sessions.get(token);

                    if (currentUserId == null) {
                        throw new IllegalArgumentException("Please login first");
                    }

                    String otherUserId = request.path("otherUserId").asText();

                    userService.getUserById(otherUserId);

                    messageService.markMessagesAsRead(
                            currentUserId,
                            otherUserId);

                    response.put("status", "SUCCESS");
                    response.put("message", "Messages marked as read");

                    break;
                }

                case "EXIT": {

                    response.put("status", "SUCCESS");
                    response.put("message", "Goodbye!");

                    break;
                }

                default: {

                    response.put("status", "ERROR");
                    response.put("message", "Unknown command");
                }
            }

        } catch (IllegalArgumentException e) {

            response.put("status", "ERROR");
            response.put("message", e.getMessage());

        } catch (IOException e) {

            response.put("status", "ERROR");
            response.put("message", "Storage operation failed");
            System.err.println("Storage error: " + e.getMessage());

        } catch (Exception e) {

            response.put("status", "ERROR");
            response.put("message", "Internal server error");
            System.err.println("Request error: " + e.getMessage());
        }

        return response;
    }
}

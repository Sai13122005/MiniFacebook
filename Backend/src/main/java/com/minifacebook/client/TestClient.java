
package com.minifacebook.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.minifacebook.protocol.MessageProtocol;

import java.io.IOException;
import java.net.Socket;
import java.util.Scanner;

public class TestClient {

    private static final ObjectMapper mapper = new ObjectMapper();

    public static void main(String[] args) {

        String sessionToken = null;

        try (
                Socket socket = new Socket("localhost", 5000);
                Scanner scanner = new Scanner(System.in)) {

            System.out.println("Connected to MiniFacebook Server!");

            while (true) {

                System.out.println("\n========== MiniFacebook ==========");
                System.out.println("1. Register");
                System.out.println("2. Login");
                System.out.println("3. Logout");
                System.out.println("4. View Users");
                System.out.println("5. Follow User");
                System.out.println("6. Unfollow User");
                System.out.println("7. Create Post");
                System.out.println("8. View Feed");
                System.out.println("9. Like Post");
                System.out.println("10. Unlike Post");
                System.out.println("11. Add Comment");
                System.out.println("12. View Comments");
                System.out.println("13. View Notifications");
                System.out.println("14. Mark Notification as Read");
                System.out.println("15. Exit");

                System.out.print("Choose option: ");

                String choice = scanner.nextLine();

                ObjectNode request = mapper.createObjectNode();

                switch (choice) {

                    case "1":

                        request.put("command", "REGISTER");

                        System.out.print("Enter username: ");
                        request.put("username", scanner.nextLine());

                        System.out.print("Enter password: ");
                        request.put("password", scanner.nextLine());

                        break;

                    case "2":

                        request.put("command", "LOGIN");

                        System.out.print("Enter username: ");
                        request.put("username", scanner.nextLine());

                        System.out.print("Enter password: ");
                        request.put("password", scanner.nextLine());

                        break;

                    case "3":

                        if (sessionToken == null) {
                            System.out.println("Please login first.");
                            continue;
                        }

                        request.put("command", "LOGOUT");
                        request.put("sessionToken", sessionToken);

                        break;

                    case "4":

                        if (sessionToken == null) {
                            System.out.println("Please login first.");
                            continue;
                        }

                        request.put("command", "GET_USERS");
                        request.put("sessionToken", sessionToken);

                        break;

                    case "5":

                        if (sessionToken == null) {
                            System.out.println("Please login first.");
                            continue;
                        }

                        request.put("command", "FOLLOW_USER");
                        request.put("sessionToken", sessionToken);

                        System.out.print("Enter target User ID: ");
                        request.put("targetUserId", scanner.nextLine());

                        break;

                    case "6":

                        if (sessionToken == null) {
                            System.out.println("Please login first.");
                            continue;
                        }

                        request.put("command", "UNFOLLOW_USER");
                        request.put("sessionToken", sessionToken);

                        System.out.print("Enter target User ID: ");
                        request.put("targetUserId", scanner.nextLine());

                        break;

                    case "7":

                        if (sessionToken == null) {
                            System.out.println("Please login first.");
                            continue;
                        }

                        request.put("command", "CREATE_POST");
                        request.put("sessionToken", sessionToken);

                        System.out.print("Write your post: ");
                        request.put("content", scanner.nextLine());

                        break;

                    case "8":

                        if (sessionToken == null) {
                            System.out.println("Please login first.");
                            continue;
                        }

                        request.put("command", "GET_FEED");
                        request.put("sessionToken", sessionToken);

                        break;

                    case "9":

                        if (sessionToken == null) {
                            System.out.println("Please login first.");
                            continue;
                        }

                        request.put("command", "LIKE_POST");
                        request.put("sessionToken", sessionToken);

                        System.out.print("Enter Post ID: ");
                        request.put("postId", scanner.nextLine());

                        break;

                    case "10":

                        if (sessionToken == null) {
                            System.out.println("Please login first.");
                            continue;
                        }

                        request.put("command", "UNLIKE_POST");
                        request.put("sessionToken", sessionToken);

                        System.out.print("Enter Post ID: ");
                        request.put("postId", scanner.nextLine());

                        break;

                    case "11":

                        if (sessionToken == null) {
                            System.out.println("Please login first.");
                            continue;
                        }

                        request.put("command", "ADD_COMMENT");
                        request.put("sessionToken", sessionToken);

                        System.out.print("Enter Post ID: ");
                        request.put("postId", scanner.nextLine());

                        System.out.print("Write your comment: ");
                        request.put("content", scanner.nextLine());

                        break;

                    case "12":

                        if (sessionToken == null) {
                            System.out.println("Please login first.");
                            continue;
                        }

                        request.put("command", "GET_COMMENTS");
                        request.put("sessionToken", sessionToken);

                        System.out.print("Enter Post ID: ");
                        request.put("postId", scanner.nextLine());

                        break;

                    case "13":

                        if (sessionToken == null) {
                            System.out.println("Please login first.");
                            continue;
                        }

                        request.put("command", "GET_NOTIFICATIONS");
                        request.put("sessionToken", sessionToken);

                        break;

                    case "14":

                        if (sessionToken == null) {
                            System.out.println("Please login first.");
                            continue;
                        }

                        request.put("command", "MARK_NOTIFICATION_READ");
                        request.put("sessionToken", sessionToken);

                        System.out.print("Enter Notification ID: ");
                        request.put("notificationId", scanner.nextLine());

                        break;

                    case "15":

                        request.put("command", "EXIT");

                        break;

                    default:

                        System.out.println("Invalid option.");
                        continue;
                }

                MessageProtocol.writeMessage(
                        socket.getOutputStream(), request);

                JsonNode response = MessageProtocol.readMessage(
                        socket.getInputStream());

                if (response == null) {
                    System.out.println("Server disconnected.");
                    break;
                }

                System.out.println("\nServer Response:");

                System.out.println(
                        mapper.writerWithDefaultPrettyPrinter()
                                .writeValueAsString(response));

                // Save token after successful login
                if (choice.equals("2")
                        && "SUCCESS".equals(
                                response.path("status").asText())) {

                    sessionToken = response.path("sessionToken").asText();

                    System.out.println("Session saved successfully!");
                }

                // Clear token after successful logout
                if (choice.equals("3")
                        && "SUCCESS".equals(
                                response.path("status").asText())) {

                    sessionToken = null;
                }

                if (choice.equals("15")) {
                    break;
                }
            }

        } catch (IOException e) {
            System.out.println("Client error: " + e.getMessage());
        }
    }
}

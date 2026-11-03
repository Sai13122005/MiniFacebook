
package com.minifacebook.gateway;

import com.minifacebook.config.AppConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.minifacebook.protocol.MessageProtocol;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class MessageWebSocketServer extends WebSocketServer {

    private static final String TCP_HOST = AppConfig.TCP_HOST;
    private static final int TCP_PORT = AppConfig.TCP_PORT;

    private static final ObjectMapper mapper = new ObjectMapper();

    // User ID -> All active WebSocket connections of that user
    private static final ConcurrentHashMap<String, Set<WebSocket>> activeConnections = new ConcurrentHashMap<>();

    // WebSocket connection -> Authenticated session information
    private static final ConcurrentHashMap<WebSocket, String> connectionTokens = new ConcurrentHashMap<>();

    private static final ConcurrentHashMap<WebSocket, String> connectionUsers = new ConcurrentHashMap<>();

    public MessageWebSocketServer(int port) {
        super(new InetSocketAddress(port));
    }

    @Override
    public void onOpen(WebSocket connection, ClientHandshake handshake) {

        String requestOrigin = handshake.getFieldValue("Origin");
        String allowedOrigin = AppConfig.FRONTEND_URL;

        // Reject connections from unauthorized browser origins.
        if (requestOrigin == null ||
                !allowedOrigin.equals(requestOrigin)) {

            System.out.println(
                    "Rejected WebSocket connection from origin: "
                            + requestOrigin);

            connection.close(1008, "Origin not allowed");
            return;
        }

        System.out.println(
                "WebSocket connected: "
                        + connection.getRemoteSocketAddress());

        sendEvent(
                connection,
                "AUTH_REQUIRED",
                "Please authenticate");
    }

    private void sendToAuthenticatedConnections(
            String userId,
            ObjectNode event) {

        Set<WebSocket> connections = activeConnections.get(userId);

        if (connections == null) {
            return;
        }

        for (WebSocket connection : connections) {

            if (!connection.isOpen()) {
                continue;
            }

            String token = connectionTokens.get(connection);

            try {
                if (token == null || !isSessionValid(token)) {
                    connection.close(1008, "Session expired or revoked");
                    continue;
                }

                connection.send(event.toString());

            } catch (IOException e) {
                // Fail closed if the authoritative server cannot validate.
                connection.close(1011, "Session validation unavailable");
            }
        }
    }

    private boolean isSessionValid(String token) throws IOException {

        ObjectNode request = mapper.createObjectNode();
        request.put("command", "VALIDATE_SESSION");
        request.put("sessionToken", token);

        JsonNode response = forwardToTcpServer(request);

        return "SUCCESS".equals(
                response.path("status").asText());
    }

    private void handleMarkMessagesRead(
            WebSocket connection,
            JsonNode request) throws IOException {

        String currentUserId = connectionUsers.get(connection);
        String token = connectionTokens.get(connection);

        if (currentUserId == null || token == null) {
            sendError(connection, "Please authenticate first");
            return;
        }

        String otherUserId = request.path("otherUserId").asText("");

        if (otherUserId.isBlank()
                || otherUserId.equals(currentUserId)) {
            sendError(connection, "Invalid conversation");
            return;
        }

        // Ask the authoritative TCP server to update message storage.
        ObjectNode tcpRequest = mapper.createObjectNode();

        tcpRequest.put("command", "MARK_MESSAGE_READ");
        tcpRequest.put("sessionToken", token);
        tcpRequest.put("otherUserId", otherUserId);

        JsonNode response = forwardToTcpServer(tcpRequest);

        if (!"SUCCESS".equals(response.path("status").asText())) {
            sendError(connection, response.path("message").asText(
                    "Unable to update read status"));
            return;
        }

        // Notify the original sender that their messages were read.
        ObjectNode readEvent = mapper.createObjectNode();

        readEvent.put("event", "MESSAGE_READ");
        readEvent.put("readerId", currentUserId);

        Set<WebSocket> senderConnections = activeConnections.get(otherUserId);

        if (senderConnections != null) {

            sendToAuthenticatedConnections(otherUserId, readEvent);
        }

        // Confirm to the reader.
        ObjectNode acknowledgement = mapper.createObjectNode();
        acknowledgement.put("event", "READ_STATUS_UPDATED");
        acknowledgement.put("otherUserId", otherUserId);

        connection.send(acknowledgement.toString());
    }

    @Override
    public void onMessage(WebSocket connection, String text) {

        try {
            JsonNode request = mapper.readTree(text);

            if (request == null || !request.isObject()) {
                sendError(connection, "Invalid JSON message");
                return;
            }

            // First message must authenticate the connection
            if (!connectionUsers.containsKey(connection)) {

                authenticate(connection, request);
                return;
            }

            String command = request.path("command").asText();

            if ("SEND_MESSAGE".equalsIgnoreCase(command)) {

                handleSendMessage(connection, request);

            } else if ("MARK_MESSAGE_READ".equalsIgnoreCase(command)) {

                handleMarkMessagesRead(connection, request);

            } else {

                sendError(connection, "Unsupported WebSocket command");
            }

        } catch (Exception e) {
            System.err.println("WebSocket request error: " + e.getMessage());
            sendError(connection, "Unable to process request");
        }
    }

    private void authenticate(WebSocket connection, JsonNode request)
            throws IOException {

        if (!"AUTH".equalsIgnoreCase(
                request.path("command").asText())) {
            sendError(connection, "Authentication required");
            return;
        }

        String token = request.path("sessionToken").asText("");

        if (token.isBlank()) {
            sendError(connection, "Session token is required");
            return;
        }

        ObjectNode tcpRequest = mapper.createObjectNode();
        tcpRequest.put("command", "VALIDATE_SESSION");
        tcpRequest.put("sessionToken", token);

        JsonNode response = forwardToTcpServer(tcpRequest);

        if (!"SUCCESS".equals(response.path("status").asText())) {
            sendError(connection, "Invalid session");
            connection.close();
            return;
        }

        String userId = response.path("userId").asText();

        if (userId.isBlank()) {
            sendError(connection, "Authentication failed");
            connection.close();
            return;
        }

        connectionTokens.put(connection, token);
        connectionUsers.put(connection, userId);

        activeConnections
                .computeIfAbsent(userId, id -> ConcurrentHashMap.newKeySet())
                .add(connection);

        ObjectNode authResponse = mapper.createObjectNode();
        authResponse.put("event", "AUTH_SUCCESS");
        authResponse.put("userId", userId);

        connection.send(authResponse.toString());

        System.out.println("WebSocket authenticated user: " + userId);
    }

    private void handleSendMessage(
            WebSocket connection,
            JsonNode request) throws IOException {

        String senderId = connectionUsers.get(connection);
        String token = connectionTokens.get(connection);

        if (senderId == null || token == null) {
            sendError(connection, "Please authenticate first");
            return;
        }

        String receiverId = request.path("receiverId").asText("");
        String content = request.path("content").asText("");

        if (receiverId.isBlank() || content.isBlank()) {
            sendError(connection, "Receiver and message content are required");
            return;
        }

        // Forward to the authoritative TCP application server
        ObjectNode tcpRequest = mapper.createObjectNode();

        tcpRequest.put("command", "SEND_MESSAGE");
        tcpRequest.put("sessionToken", token);
        tcpRequest.put("receiverId", receiverId);
        tcpRequest.put("content", content);

        JsonNode response = forwardToTcpServer(tcpRequest);

        if (!"SUCCESS".equals(response.path("status").asText())) {
            sendError(connection, response.path("message").asText(
                    "Message could not be sent"));
            return;
        }

        JsonNode messageData = response.path("messageData");

        // Acknowledge successful persistence to sender
        ObjectNode acknowledgement = mapper.createObjectNode();
        acknowledgement.put("event", "MESSAGE_SENT");
        acknowledgement.set("messageData", messageData);

        connection.send(acknowledgement.toString());

        // Push only after the TCP server successfully saves the message
        ObjectNode newMessageEvent = mapper.createObjectNode();
        newMessageEvent.put("event", "NEW_MESSAGE");
        newMessageEvent.set("messageData", messageData);

        Set<WebSocket> receiverConnections = activeConnections.get(receiverId);

        if (receiverConnections != null) {

            sendToAuthenticatedConnections(receiverId, newMessageEvent);
        }

        System.out.println(
                "Message delivered from " + senderId + " to " + receiverId);
    }

    private JsonNode forwardToTcpServer(JsonNode request)
            throws IOException {

        try (Socket socket = new Socket()) {

            socket.connect(
                    new InetSocketAddress(TCP_HOST, TCP_PORT),
                    3000);

            socket.setSoTimeout(10000);

            MessageProtocol.writeMessage(
                    socket.getOutputStream(), request);

            JsonNode response = MessageProtocol.readMessage(
                    socket.getInputStream());

            if (response == null) {
                throw new IOException("TCP server returned no response");
            }

            return response;
        }
    }

    private void sendError(WebSocket connection, String message) {

        if (connection == null || !connection.isOpen()) {
            return;
        }

        ObjectNode error = mapper.createObjectNode();
        error.put("event", "ERROR");
        error.put("message", message);

        connection.send(error.toString());
    }

    private void sendEvent(
            WebSocket connection,
            String event,
            String message) {

        ObjectNode response = mapper.createObjectNode();
        response.put("event", event);
        response.put("message", message);

        if (connection.isOpen()) {
            connection.send(response.toString());
        }
    }

    @Override
    public void onClose(
            WebSocket connection,
            int code,
            String reason,
            boolean remote) {

        String userId = connectionUsers.remove(connection);
        connectionTokens.remove(connection);

        if (userId != null) {

            Set<WebSocket> connections = activeConnections.get(userId);

            if (connections != null) {
                connections.remove(connection);

                if (connections.isEmpty()) {
                    activeConnections.remove(userId, connections);
                }
            }
        }

        System.out.println("WebSocket disconnected: " + reason);
    }

    @Override
    public void onError(WebSocket connection, Exception exception) {

        System.err.println("WebSocket error: " + exception.getMessage());
    }

    @Override
    public void onStart() {

        System.out.println("MiniFacebook WebSocket Server started!");
        System.out.println("WebSocket Port: " + getPort());
    }
}

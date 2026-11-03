
package com.minifacebook.gateway;

import com.minifacebook.config.AppConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.minifacebook.protocol.MessageProtocol;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class HttpGateway {

    private static final int HTTP_PORT = AppConfig.HTTP_PORT;
    private static final String TCP_HOST = AppConfig.TCP_HOST;
    private static final int TCP_PORT = AppConfig.TCP_PORT;
    private static final int MAX_BODY_SIZE = 1024 * 1024;

    private static final ObjectMapper mapper = new ObjectMapper();

    public static void main(String[] args) {

        try {

            HttpServer server = HttpServer.create(
                    new InetSocketAddress(HTTP_PORT), 0);

            server.createContext("/health", exchange -> {
                if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                    sendError(exchange, 405, "Only GET is supported");
                    return;
                }

                sendHealthResponse(exchange, 200, "UP",
                        "HTTP Gateway is running");
            });

            server.createContext("/ready", exchange -> {
                if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                    sendError(exchange, 405, "Only GET is supported");
                    return;
                }

                try (Socket socket = new Socket()) {
                    socket.connect(
                            new InetSocketAddress(TCP_HOST, TCP_PORT),
                            2000);

                    sendHealthResponse(exchange, 200, "READY",
                            "TCP application server is reachable");

                } catch (IOException e) {
                    sendHealthResponse(exchange, 503, "NOT_READY",
                            "TCP application server is unavailable");
                }
            });

            server.createContext("/db-health", exchange -> {
                if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                    sendError(exchange, 405, "Only GET is supported");
                    return;
                }

                try {
                    JsonNode response = checkDatabaseHealth();

                    if (response.path("status").asText().equals("UP")) {
                        sendHealthResponse(exchange, 200, "UP",
                                "PostgreSQL connection is healthy");
                    } else {
                        sendHealthResponse(exchange, 503, "DOWN",
                                "PostgreSQL health check failed");
                    }

                } catch (IOException e) {
                    sendHealthResponse(exchange, 503, "DOWN",
                            "Database health check is unavailable");
                }
            });
            server.createContext("/api/request",
                    HttpGateway::handleRequest);

            server.setExecutor(
                    java.util.concurrent.Executors.newFixedThreadPool(10));
            MessageWebSocketServer webSocketServer = new MessageWebSocketServer(AppConfig.WS_PORT);

            webSocketServer.start();
            server.start();

            System.out.println("MiniFacebook HTTP Gateway started!");
            System.out.println("HTTP Port: " + HTTP_PORT);
            System.out.println("Waiting for React frontend requests...");

        } catch (IOException e) {
            System.out.println("Gateway error: " + e.getMessage());
        }
    }

    private static void sendHealthResponse(
            HttpExchange exchange,
            int statusCode,
            String status,
            String message) throws IOException {

        ObjectNode response = mapper.createObjectNode();

        response.put("status", status);
        response.put("message", message);

        byte[] bytes = mapper.writeValueAsBytes(response);

        exchange.getResponseHeaders().set(
                "Content-Type",
                "application/json; charset=UTF-8");

        exchange.sendResponseHeaders(statusCode, bytes.length);

        try {
            exchange.getResponseBody().write(bytes);
        } finally {
            exchange.close();
        }
    }

    private static JsonNode checkDatabaseHealth() throws IOException {

        ObjectNode request = mapper.createObjectNode();
        request.put("command", "HEALTH_CHECK");

        JsonNode response = forwardToTcpServer(request);

        System.out.println(
                "Database health response: " +
                        response.toPrettyString());

        return response;
    }

    private static void handleRequest(HttpExchange exchange)
            throws IOException {

        String allowedOrigin = AppConfig.FRONTEND_URL;
        String requestOrigin = exchange.getRequestHeaders()
                .getFirst("Origin");

        boolean originAllowed = requestOrigin == null
                || allowedOrigin.equals(requestOrigin);

        if (!originAllowed) {
            sendError(exchange, 403, "Origin not allowed");
            return;
        }

        if (requestOrigin != null) {
            exchange.getResponseHeaders().set(
                    "Access-Control-Allow-Origin",
                    allowedOrigin);

            exchange.getResponseHeaders().set("Vary", "Origin");
        }

        exchange.getResponseHeaders().set(
                "Access-Control-Allow-Methods",
                "POST, OPTIONS");

        exchange.getResponseHeaders().set(
                "Access-Control-Allow-Headers",
                "Content-Type, Authorization");

        exchange.getResponseHeaders().set(
                "Content-Type",
                "application/json; charset=UTF-8");

        String method = exchange.getRequestMethod();

        if ("OPTIONS".equalsIgnoreCase(method)) {
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
            return;
        }

        if (!"POST".equalsIgnoreCase(method)) {
            sendError(exchange, 405, "Only POST is supported");
            return;
        }
        String contentType = exchange.getRequestHeaders()
                .getFirst("Content-Type");

        if (contentType == null ||
                !contentType.toLowerCase()
                        .startsWith("application/json")) {

            sendError(exchange, 415,
                    "Content-Type must be application/json");
            return;
        }
        try {

            byte[] body = exchange.getRequestBody()
                    .readNBytes(MAX_BODY_SIZE + 1);

            if (body.length > MAX_BODY_SIZE) {
                sendError(exchange, 413, "Request body too large");
                return;
            }

            JsonNode request = mapper.readTree(body);

            if (request == null || !request.isObject()
                    || !request.hasNonNull("command")
                    || !request.get("command").isTextual()) {

                sendError(exchange, 400, "Invalid JSON request");
                return;
            }


            // Forward HTTP request to TCP application server
            JsonNode response = forwardToTcpServer(request);


            byte[] responseBytes = mapper.writeValueAsBytes(response);

            exchange.sendResponseHeaders(
                    200, responseBytes.length);

            exchange.getResponseBody().write(responseBytes);

        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {

            sendError(exchange, 400, "Malformed JSON");

        } catch (java.net.ConnectException e) {

            sendError(exchange, 503,
                    "TCP application server is unavailable");

        } catch (IOException e) {

            sendError(exchange, 502,
                    "Communication with TCP server failed");

        } finally {
            exchange.close();
        }
    }

    private static JsonNode forwardToTcpServer(JsonNode request)
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

    private static void sendError(
            HttpExchange exchange,
            int statusCode,
            String message) throws IOException {

        ObjectNode error = mapper.createObjectNode();

        error.put("status", "ERROR");
        error.put("message", message);

        byte[] bytes = mapper.writeValueAsBytes(error);

        exchange.sendResponseHeaders(statusCode, bytes.length);
        exchange.getResponseBody().write(bytes);
    }
}

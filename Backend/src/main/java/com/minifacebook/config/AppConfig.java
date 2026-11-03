package com.minifacebook.config;

public final class AppConfig {

    private AppConfig() {
    }

    public static final int HTTP_PORT = Integer.parseInt(
            System.getenv().getOrDefault("PORT", "8080"));

    public static final int TCP_PORT = Integer.parseInt(
            System.getenv().getOrDefault("TCP_PORT", "5000"));

    public static final int WS_PORT = Integer.parseInt(
            System.getenv().getOrDefault("WS_PORT", "8081"));

    public static final String TCP_HOST = System.getenv().getOrDefault("TCP_HOST", "localhost");

    public static final String FRONTEND_URL = System.getenv().getOrDefault(
            "FRONTEND_URL",
            "http://localhost:5173");
}
package com.minifacebook.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;

public final class DatabaseConfig {

    private static HikariDataSource dataSource;

    private DatabaseConfig() {
    }

    public static synchronized DataSource getDataSource() {

        if (dataSource == null || dataSource.isClosed()) {

            HikariConfig config = new HikariConfig();

            String databaseUrl = System.getenv()
                    .getOrDefault(
                            "DB_URL",
                            "jdbc:postgresql://localhost:5432/minifacebook_db"
                    );

            String username = System.getenv()
                    .getOrDefault("DB_USERNAME", "minifacebook_user");

            String password = System.getenv("DB_PASSWORD");

            if (password == null || password.isBlank()) {
                throw new IllegalStateException(
                        "DB_PASSWORD environment variable is not configured"
                );
            }

            config.setJdbcUrl(databaseUrl);
            config.setUsername(username);
            config.setPassword(password);

            config.setMaximumPoolSize(5);
            config.setMinimumIdle(1);

            config.setConnectionTimeout(10000);
            config.setIdleTimeout(300000);
            config.setMaxLifetime(1800000);

            config.setPoolName("MiniFacebook-DB-Pool");

            dataSource = new HikariDataSource(config);
        }

        return dataSource;
    }

    public static synchronized void close() {

        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
}
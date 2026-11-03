package com.minifacebook.client;

import com.minifacebook.config.DatabaseConfig;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

public class DatabaseConnectionTest {

    public static void main(String[] args) {

        try {
            DataSource dataSource = DatabaseConfig.getDataSource();

            try (Connection connection = dataSource.getConnection();
                 Statement statement = connection.createStatement();
                 ResultSet result = statement.executeQuery(
                         "SELECT current_database(), current_user"
                 )) {

                if (result.next()) {
                    System.out.println("Database: " + result.getString(1));
                    System.out.println("User: " + result.getString(2));
                }
            }

            System.out.println("PostgreSQL connection successful!");

        } catch (Exception e) {
            System.err.println("Database connection failed!");
            e.printStackTrace();

        } finally {
            DatabaseConfig.close();
        }
    }
}
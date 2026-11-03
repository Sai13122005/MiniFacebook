package com.minifacebook.client;

import com.minifacebook.config.DatabaseConfig;
import com.minifacebook.repositories.UserRepository;
import com.minifacebook.models.User;

import java.util.List;

public class UserRepositoryTest {

    public static void main(String[] args) {

        try {
            UserRepository repository =
                    new UserRepository(DatabaseConfig.getDataSource());

            List<User> users = repository.findAll();

            System.out.println("PostgreSQL UserRepository test");
            System.out.println("--------------------------------");

            System.out.println("Users found: " + users.size());

            for (User user : users) {
                System.out.println(
                        user.getUsername()
                                + " | Followers: "
                                + user.getFollowers().size()
                                + " | Following: "
                                + user.getFollowing().size()
                );
            }

            System.out.println("--------------------------------");
            System.out.println("UserRepository test successful!");

        } catch (Exception e) {
            System.err.println("UserRepository test failed!");
            e.printStackTrace();

        } finally {
            DatabaseConfig.close();
        }
    }
}
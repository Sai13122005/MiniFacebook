
package com.minifacebook.config;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

public final class TransactionManager {

    private final DataSource dataSource;

    public TransactionManager(DataSource dataSource) {
        if (dataSource == null) {
            throw new IllegalArgumentException(
                    "DataSource cannot be null"
            );
        }

        this.dataSource = dataSource;
    }

    @FunctionalInterface
    public interface TransactionWork<T> {
        T execute(Connection connection) throws SQLException;
    }

    public <T> T execute(TransactionWork<T> work)
            throws SQLException {

        try (Connection connection = dataSource.getConnection()) {

            connection.setAutoCommit(false);

            try {
                T result = work.execute(connection);

                connection.commit();

                return result;

            } catch (SQLException | RuntimeException | Error exception) {

                try {
                    connection.rollback();
                } catch (SQLException rollbackException) {
                    exception.addSuppressed(rollbackException);
                }

                throw exception;
            }
        }
    }
}

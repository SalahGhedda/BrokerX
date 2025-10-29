package com.brokerx.adapters.persistence.jdbc;

import com.brokerx.ports.TransactionCallback;
import com.brokerx.ports.TransactionManager;
import com.brokerx.ports.TransactionRunnable;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

public class JdbcTransactionManager implements TransactionManager {
    private final DataSource dataSource;

    public JdbcTransactionManager(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public <T> T inTransaction(TransactionCallback<T> callback) {
        try (Connection connection = dataSource.getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            JdbcSession.bind(connection);
            try {
                T result = callback.doInTransaction();
                connection.commit();
                return result;
            } catch (Exception ex) {
                try {
                    connection.rollback();
                } catch (SQLException rollback) {
                    throw new PersistenceException("Transaction rollback failed", rollback);
                }
                if (ex instanceof RuntimeException runtime) {
                    throw runtime;
                }
                throw new PersistenceException("Transaction failed", ex);
            } finally {
                JdbcSession.clear();
                connection.setAutoCommit(previousAutoCommit);
            }
        } catch (SQLException ex) {
            throw new PersistenceException("Unable to obtain transactional connection", ex);
        }
    }

    @Override
    public void inTransaction(TransactionRunnable runnable) {
        TransactionManager.super.inTransaction(runnable);
    }
}

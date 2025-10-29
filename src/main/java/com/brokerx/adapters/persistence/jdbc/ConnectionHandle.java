package com.brokerx.adapters.persistence.jdbc;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

final class ConnectionHandle implements AutoCloseable {
    private final Connection connection;
    private final boolean close;

    private ConnectionHandle(Connection connection, boolean close) {
        this.connection = connection;
        this.close = close;
    }

    static ConnectionHandle acquire(DataSource dataSource) throws SQLException {
        Connection current = JdbcSession.current();
        if (current != null) {
            return new ConnectionHandle(current, false);
        }
        return new ConnectionHandle(dataSource.getConnection(), true);
    }

    Connection connection() {
        return connection;
    }

    @Override
    public void close() throws SQLException {
        if (close) {
            connection.close();
        }
    }
}

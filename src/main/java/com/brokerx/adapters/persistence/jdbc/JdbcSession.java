package com.brokerx.adapters.persistence.jdbc;

import java.sql.Connection;

final class JdbcSession {
    private static final ThreadLocal<Connection> CONTEXT = new ThreadLocal<>();

    private JdbcSession() {
    }

    static Connection current() {
        return CONTEXT.get();
    }

    static void bind(Connection connection) {
        CONTEXT.set(connection);
    }

    static void clear() {
        CONTEXT.remove();
    }
}

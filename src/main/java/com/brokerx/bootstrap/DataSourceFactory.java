package com.brokerx.bootstrap;

import java.util.Optional;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

public final class DataSourceFactory {
    private DataSourceFactory() {
    }

    public static HikariDataSource createFromEnvironment() {
        var config = new HikariConfig();
        config.setJdbcUrl(envOrDefault("BROKERX_DB_URL", "jdbc:postgresql://localhost:5432/brokerx_db"));
        config.setUsername(envOrDefault("BROKERX_DB_USER", "brokerx"));
        config.setPassword(envOrDefault("BROKERX_DB_PASSWORD", "brokerx"));
        config.setDriverClassName("org.postgresql.Driver");
        config.setMaximumPoolSize(5);
        config.setMinimumIdle(1);
        config.setPoolName("brokerx-pool");
        config.setAutoCommit(true);
        return new HikariDataSource(config);
    }

    private static String envOrDefault(String key, String defaultValue) {
        return Optional.ofNullable(System.getenv(key)).filter(v -> !v.isBlank()).orElse(defaultValue);
    }
}

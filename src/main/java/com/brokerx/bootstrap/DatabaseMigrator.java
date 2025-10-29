package com.brokerx.bootstrap;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.stream.Collectors;
import javax.sql.DataSource;

import com.brokerx.adapters.persistence.jdbc.PersistenceException;

public class DatabaseMigrator {
    private static final String[] MIGRATIONS = {
        "db/migration/V1__init.sql"
    };

    private final DataSource dataSource;

    public DatabaseMigrator(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void migrate() {
        try (var connection = dataSource.getConnection()) {
            for (String migration : MIGRATIONS) {
                executeSqlScript(connection, migration);
            }
        } catch (SQLException e) {
            throw new PersistenceException("Unable to run database migrations", e);
        }
    }

    private void executeSqlScript(java.sql.Connection connection, String resourcePath) {
        String sql = loadSql(resourcePath);
        for (String statement : sql.split(";")) {
            String trimmed = statement.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            try (var stmt = connection.createStatement()) {
                stmt.execute(trimmed);
            } catch (SQLException e) {
                throw new PersistenceException("Failed executing migration statement: " + trimmed, e);
            }
        }
    }

    private String loadSql(String resourcePath) {
        var classLoader = Thread.currentThread().getContextClassLoader();
        var resource = classLoader.getResourceAsStream(resourcePath);
        if (resource == null) {
            throw new PersistenceException("Migration resource not found: " + resourcePath);
        }
        try (var reader = new BufferedReader(new InputStreamReader(resource, StandardCharsets.UTF_8))) {
            return reader.lines().collect(Collectors.joining("\n"));
        } catch (IOException e) {
            throw new PersistenceException("Unable to read migration resource: " + resourcePath, e);
        }
    }
}

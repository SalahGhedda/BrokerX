package com.brokerx.observability;

import com.brokerx.interfaces.rest.JsonSupport;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class StructuredLogger {
    private static final ConcurrentHashMap<String, StructuredLogger> CACHE = new ConcurrentHashMap<>();

    private final String component;

    private StructuredLogger(String component) {
        this.component = component;
    }

    public static StructuredLogger get(Class<?> type) {
        String name = type.getName();
        return CACHE.computeIfAbsent(name, StructuredLogger::new);
    }

    public void info(String message) {
        log("INFO", message, null);
    }

    public void info(String message, Map<String, ?> fields) {
        log("INFO", message, fields);
    }

    public void warn(String message, Map<String, ?> fields) {
        log("WARN", message, fields);
    }

    public void error(String message, Map<String, ?> fields) {
        log("ERROR", message, fields);
    }

    public void error(String message, Throwable error, Map<String, ?> fields) {
        ObjectNode node = baseNode("ERROR", message);
        if (fields != null && !fields.isEmpty()) {
            fields.forEach((k, v) -> node.putPOJO(k, v));
        }
        if (error != null) {
            node.put("exception", error.getClass().getName());
            node.put("errorMessage", error.getMessage());
        }
        emit(node);
    }

    private void log(String level, String message, Map<String, ?> fields) {
        ObjectNode node = baseNode(level, message);
        if (fields != null && !fields.isEmpty()) {
            fields.forEach((k, v) -> node.putPOJO(k, v));
        }
        emit(node);
    }

    private ObjectNode baseNode(String level, String message) {
        ObjectNode node = JsonSupport.mapper().createObjectNode();
        node.put("ts", Instant.now().toString());
        node.put("level", level);
        node.put("component", component);
        node.put("message", message);
        return node;
    }

    private void emit(ObjectNode node) {
        try {
            System.out.println(JsonSupport.mapper().writeValueAsString(node));
        } catch (Exception ex) {
            System.err.println("{\"level\":\"ERROR\",\"component\":\"" + component
                    + "\",\"message\":\"Failed to render structured log\",\"error\":\"" + ex.getMessage() + "\"}");
        }
    }
}

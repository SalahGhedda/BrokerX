package com.brokerx.interfaces.rest;

import com.brokerx.observability.AppMetrics;
import com.brokerx.observability.StructuredLogger;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import io.prometheus.client.exporter.common.TextFormat;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

public final class MetricsHandler implements HttpHandler {
    private static final StructuredLogger LOGGER = StructuredLogger.get(MetricsHandler.class);

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            return;
        }
        exchange.getResponseHeaders().set("Content-Type", TextFormat.CONTENT_TYPE_004);
        exchange.sendResponseHeaders(200, 0);
        try (Writer writer = new OutputStreamWriter(exchange.getResponseBody(), StandardCharsets.UTF_8)) {
            TextFormat.write004(writer, AppMetrics.registry().metricFamilySamples());
        } catch (IOException ex) {
            LOGGER.error("Failed to stream metrics", ex, null);
            throw ex;
        } finally {
            exchange.close();
        }
    }
}

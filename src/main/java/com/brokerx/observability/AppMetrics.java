package com.brokerx.observability;

import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.Counter;
import io.prometheus.client.Histogram;
import io.prometheus.client.hotspot.DefaultExports;

import java.time.Duration;
import java.util.Locale;
import java.util.regex.Pattern;

public final class AppMetrics {
    private static final Pattern UUID_PATTERN = Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
    private static final Pattern NUMBER_PATTERN = Pattern.compile("^\\d+$");

    private static final CollectorRegistry REGISTRY = CollectorRegistry.defaultRegistry;

    public static final Counter HTTP_REQUESTS = Counter.build()
            .name("brokerx_http_requests_total")
            .help("Total HTTP requests processed")
            .labelNames("path", "method", "status")
            .register(REGISTRY);

    public static final Histogram HTTP_DURATION = Histogram.build()
            .name("brokerx_http_request_duration_seconds")
            .help("HTTP request duration seconds")
            .labelNames("path", "method")
            .buckets(0.01, 0.025, 0.05, 0.1, 0.25, 0.5, 1, 2, 3, 5, 8, 10)
            .register(REGISTRY);

    public static final Counter ORDERS_TOTAL = Counter.build()
            .name("brokerx_orders_total")
            .help("Orders placed")
            .labelNames("type", "status")
            .register(REGISTRY);

    public static final Counter WALLET_DEPOSITS = Counter.build()
            .name("brokerx_wallet_deposits_total")
            .help("Wallet deposit attempts")
            .labelNames("status")
            .register(REGISTRY);

    static {
        DefaultExports.initialize();
    }

    private AppMetrics() {
    }

    public static CollectorRegistry registry() {
        return REGISTRY;
    }

    public static void observeHttp(String path, String method, int status, Duration duration) {
        String normalized = normalizePath(path);
        HTTP_REQUESTS.labels(normalized, method, String.valueOf(status)).inc();
        HTTP_DURATION.labels(normalized, method).observe(duration.toNanos() / 1_000_000_000.0);
    }

    public static void recordOrder(String type, String status) {
        ORDERS_TOTAL.labels(type, status).inc();
    }

    public static void recordDeposit(String status) {
        String normalized = status == null ? "UNKNOWN" : status.toUpperCase(Locale.ROOT);
        WALLET_DEPOSITS.labels(normalized).inc();
    }

    public static String normalizePath(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            return "/";
        }
        String[] segments = rawPath.split("/");
        StringBuilder builder = new StringBuilder();
        for (String segment : segments) {
            if (segment.isBlank()) {
                continue;
            }
            builder.append('/');
            if (UUID_PATTERN.matcher(segment).matches()) {
                builder.append(":uuid");
            } else if (NUMBER_PATTERN.matcher(segment).matches()) {
                builder.append(":id");
            } else {
                builder.append(segment);
            }
        }
        if (builder.length() == 0) {
            return "/";
        }
        return builder.toString();
    }
}

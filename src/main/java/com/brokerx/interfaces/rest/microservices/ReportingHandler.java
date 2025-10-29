package com.brokerx.interfaces.rest.microservices;

import com.brokerx.domain.order.TradeOrder;
import com.brokerx.interfaces.rest.AbstractJsonHandler;
import com.brokerx.interfaces.rest.RestException;
import com.brokerx.interfaces.rest.TokenService;
import com.brokerx.interfaces.rest.dto.OrderReportResponse;
import com.brokerx.ports.OrderRepository;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class ReportingHandler extends AbstractJsonHandler {
    private final OrderRepository orderRepository;

    public ReportingHandler(OrderRepository orderRepository, TokenService tokenService) {
        super(tokenService);
        this.orderRepository = orderRepository;
    }

    @Override
    protected void doHandle(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            throw new RestException(HttpURLConnection.HTTP_BAD_METHOD, "Method not allowed");
        }
        String contextPath = exchange.getHttpContext().getPath();
        String path = exchange.getRequestURI().getPath().substring(contextPath.length());
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        String[] segments = path.isEmpty() ? new String[0] : path.split("/");
        if (segments.length == 2 && "orders".equals(segments[0]) && "summary".equals(segments[1])) {
            handleOrdersSummary(exchange);
            return;
        }
        throw new RestException(HttpURLConnection.HTTP_NOT_FOUND, "Route not found");
    }

    private void handleOrdersSummary(HttpExchange exchange) throws IOException {
        List<TradeOrder> orders = orderRepository.findAll();
        Map<String, Long> byStatus = orders.stream()
                .collect(Collectors.groupingBy(order -> order.status().name(), Collectors.counting()));
        Map<String, Long> byType = orders.stream()
                .collect(Collectors.groupingBy(order -> order.type().name(), Collectors.counting()));
        OrderReportResponse response = new OrderReportResponse(
                orders.size(),
                byStatus,
                byType,
                Instant.now()
        );
        sendData(exchange, HttpURLConnection.HTTP_OK, response);
    }
}

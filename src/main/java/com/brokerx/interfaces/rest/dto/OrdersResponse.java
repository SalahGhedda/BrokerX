package com.brokerx.interfaces.rest.dto;

import java.util.List;

public record OrdersResponse(List<OrderResponse> orders) {
}

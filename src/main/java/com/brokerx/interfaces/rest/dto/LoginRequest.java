package com.brokerx.interfaces.rest.dto;

public record LoginRequest(
        String email,
        String password
) {
}

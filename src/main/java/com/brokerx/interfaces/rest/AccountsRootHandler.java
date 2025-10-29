package com.brokerx.interfaces.rest;

import com.brokerx.application.AuthService;
import com.brokerx.application.AuthService.SignupCommand;
import com.brokerx.application.AuthService.SignupResult;
import com.brokerx.interfaces.rest.dto.SignupRequest;
import com.brokerx.interfaces.rest.dto.SignupResponse;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.time.LocalDate;

final class AccountsRootHandler extends AbstractJsonHandler {
    private static final String BASE_PATH = "/api/v1/accounts";

    private final AuthService authService;

    AccountsRootHandler(AuthService authService) {
        this.authService = authService;
    }

    @Override
    protected void doHandle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod().toUpperCase();
        if (!BASE_PATH.equals(path)) {
            throw new RestException(HttpURLConnection.HTTP_NOT_FOUND, "Route not found");
        }
        if ("POST".equals(method)) {
            handleSignup(exchange);
        } else {
            throw new RestException(HttpURLConnection.HTTP_BAD_METHOD, "Method not allowed");
        }
    }

    private void handleSignup(HttpExchange exchange) throws IOException {
        SignupRequest request = readJson(exchange, SignupRequest.class);
        validateSignup(request);
        SignupCommand command = new SignupCommand(
                request.email(),
                request.phone(),
                request.password(),
                request.fullName(),
                request.addressLine(),
                request.dateOfBirth()
        );
        SignupResult result = authService.register(command);
        SignupResponse response = new SignupResponse(
                result.accountId(),
                result.email(),
                result.verificationCode(),
                result.expiresAt()
        );
        exchange.getResponseHeaders().add("Location", BASE_PATH + "/" + result.accountId());
        sendData(exchange, HttpURLConnection.HTTP_CREATED, response);
    }

    private void validateSignup(SignupRequest request) {
        if (request == null) {
            throw new RestException(HttpURLConnection.HTTP_BAD_REQUEST, "Payload requis");
        }
        require(request.email(), "Email requis");
        require(request.phone(), "Telephone requis");
        require(request.password(), "Mot de passe requis");
        if (request.password().length() < 6) {
            throw new RestException(HttpURLConnection.HTTP_BAD_REQUEST, "Mot de passe trop court");
        }
        require(request.fullName(), "Nom complet requis");
        require(request.addressLine(), "Adresse requise");
        LocalDate dob = request.dateOfBirth();
        if (dob == null) {
            throw new RestException(HttpURLConnection.HTTP_BAD_REQUEST, "Date de naissance requise");
        }
    }

    private void require(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new RestException(HttpURLConnection.HTTP_BAD_REQUEST, message);
        }
    }
}

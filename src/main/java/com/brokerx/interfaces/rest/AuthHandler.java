package com.brokerx.interfaces.rest;

import com.brokerx.application.AuthService;
import com.brokerx.domain.account.UserAccount;
import com.brokerx.interfaces.rest.dto.LoginRequest;
import com.brokerx.interfaces.rest.dto.LoginResponse;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.Optional;

final class AuthHandler extends AbstractJsonHandler {
    private final AuthService authService;
    private final TokenService tokenService;

    AuthHandler(AuthService authService, TokenService tokenService) {
        this.authService = authService;
        this.tokenService = tokenService;
    }

    @Override
    protected void doHandle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod().toUpperCase();
        if ("/api/v1/auth/tokens".equals(path) && "POST".equals(method)) {
            handleLogin(exchange);
            return;
        }
        if ("/api/v1/auth/tokens/current".equals(path) && "DELETE".equals(method)) {
            handleLogout(exchange);
            return;
        }
        throw new RestException(HttpURLConnection.HTTP_NOT_FOUND, "Route not found");
    }

    private void handleLogin(HttpExchange exchange) throws IOException {
        LoginRequest request = readJson(exchange, LoginRequest.class);
        if (request.email() == null || request.email().isBlank() || request.password() == null) {
            throw new RestException(HttpURLConnection.HTTP_BAD_REQUEST, "Email et mot de passe requis");
        }
        Optional<UserAccount> account = authService.authenticate(request.email().trim().toLowerCase(), request.password());
        if (account.isEmpty()) {
            throw new RestException(HttpURLConnection.HTTP_UNAUTHORIZED, "Identifiants invalides");
        }
        UserAccount user = account.get();
        TokenService.AuthToken token = tokenService.issue(user.getId());
        LoginResponse response = new LoginResponse(
                user.getId(),
                user.getEmail(),
                user.getState(),
                token.token(),
                "Bearer",
                token.issuedAt(),
                token.expireAt()
        );
        sendData(exchange, HttpURLConnection.HTTP_CREATED, response);
    }

    private void handleLogout(HttpExchange exchange) throws IOException {
        String header = exchange.getRequestHeaders().getFirst("Authorization");
        TokenPrincipal principal = tokenService.require(header);
        tokenService.revoke(principal.token());
        sendNoContent(exchange);
    }
}

package com.brokerx.interfaces.rest;

public class RestException extends RuntimeException {
    private final int status;
    private final String error;

    public RestException(int status, String message) {
        this(status, message, null);
    }

    public RestException(int status, String message, String error) {
        super(message);
        this.status = status;
        this.error = error;
    }

    public int status() {
        return status;
    }

    public String error() {
        return error;
    }
}

package com.httpserver.http;

import java.io.IOException;

/**
 * Exception thrown when an incoming HTTP request violates the HTTP/1.1 specification
 * or cannot be parsed.
 *
 * Automatically mapped to an HTTP 400 Bad Request (or other 4xx status) response.
 */
public class BadRequestException extends IOException {
    private final HttpStatus status;

    public BadRequestException(String message) {
        this(HttpStatus.BAD_REQUEST, message);
    }

    public BadRequestException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}

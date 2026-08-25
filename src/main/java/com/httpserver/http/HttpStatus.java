package com.httpserver.http;

/**
 * Standard HTTP status codes with their reason phrases.
 *
 * The reason phrase is sent on the status line of the response (e.g.
 * "HTTP/1.1 200 OK\r\n"). While HTTP/2 dropped reason phrases, they're
 * required by HTTP/1.1 (RFC 7230 §3.1.2).
 */
public enum HttpStatus {
    // 2xx Success
    OK(200, "OK"),
    NO_CONTENT(204, "No Content"),

    // 4xx Client Errors
    BAD_REQUEST(400, "Bad Request"),
    NOT_FOUND(404, "Not Found"),
    METHOD_NOT_ALLOWED(405, "Method Not Allowed"),
    REQUEST_TIMEOUT(408, "Request Timeout"),

    // 5xx Server Errors
    INTERNAL_SERVER_ERROR(500, "Internal Server Error");

    private final int code;
    private final String reasonPhrase;

    HttpStatus(int code, String reasonPhrase) {
        this.code = code;
        this.reasonPhrase = reasonPhrase;
    }

    public int getCode() {
        return code;
    }

    public String getReasonPhrase() {
        return reasonPhrase;
    }

    @Override
    public String toString() {
        return code + " " + reasonPhrase;
    }
}

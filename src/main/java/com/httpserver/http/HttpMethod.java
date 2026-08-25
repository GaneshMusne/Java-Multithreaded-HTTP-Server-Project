package com.httpserver.http;

/**
 * Supported HTTP methods.
 *
 * We only define the methods we'll actually handle. Any method not in this
 * enum will be rejected as unsupported (405 Method Not Allowed) once routing
 * is in place (Phase 3).
 */
public enum HttpMethod {
    GET,
    POST,
    PUT,
    DELETE,
    HEAD,
    OPTIONS;

    /**
     * Case-insensitive lookup. Throws IllegalArgumentException if the method
     * string doesn't match any known HTTP method.
     */
    public static HttpMethod fromString(String method) {
        try {
            return HttpMethod.valueOf(method.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unsupported HTTP method: " + method);
        }
    }
}

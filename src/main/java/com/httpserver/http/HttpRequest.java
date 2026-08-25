package com.httpserver.http;

import java.util.Collections;
import java.util.Map;

/**
 * Immutable representation of a parsed HTTP request.
 *
 * This is a plain data object — no logic, just holds the parsed components
 * of an HTTP request: method, path, version, headers, and body.
 *
 * Headers are stored with lowercase keys for case-insensitive lookup,
 * matching RFC 7230 §3.2: "Each header field consists of a case-insensitive
 * field name..."
 */
public class HttpRequest {
    private final HttpMethod method;
    private final String path;
    private final String httpVersion;
    private final Map<String, String> headers;
    private final byte[] body;

    public HttpRequest(HttpMethod method, String path, String httpVersion,
                       Map<String, String> headers, byte[] body) {
        this.method = method;
        this.path = path;
        this.httpVersion = httpVersion;
        // Wrap in unmodifiable map so the request is truly immutable
        this.headers = Collections.unmodifiableMap(headers);
        this.body = body != null ? body.clone() : new byte[0];
    }

    public HttpMethod getMethod() {
        return method;
    }

    public String getPath() {
        return path;
    }

    public String getHttpVersion() {
        return httpVersion;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    /**
     * Case-insensitive header lookup.
     * Returns null if the header is not present.
     */
    public String getHeader(String name) {
        return headers.get(name.toLowerCase());
    }

    public byte[] getBody() {
        return body.clone();
    }

    public String getBodyAsString() {
        return new String(body);
    }

    @Override
    public String toString() {
        return method + " " + path + " " + httpVersion
                + " (headers=" + headers.size() + ", body=" + body.length + " bytes)";
    }
}

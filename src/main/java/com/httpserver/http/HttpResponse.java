package com.httpserver.http;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builder for HTTP responses.
 *
 * Constructs a well-formed HTTP/1.1 response and serializes it to an
 * OutputStream. Uses a builder pattern so callers can chain:
 *
 *   new HttpResponse()
 *       .status(HttpStatus.OK)
 *       .header("Content-Type", "text/plain")
 *       .body("Hello, World!")
 *       .writeTo(outputStream);
 *
 * The writeTo() method handles the actual wire format:
 *   - Status line: "HTTP/1.1 200 OK\r\n"
 *   - Headers:     "Content-Type: text/plain\r\n"
 *   - Blank line:  "\r\n"
 *   - Body bytes
 */
public class HttpResponse {
    private HttpStatus status = HttpStatus.OK;
    // LinkedHashMap preserves insertion order — nice for debugging
    private final Map<String, String> headers = new LinkedHashMap<>();
    private byte[] body = new byte[0];

    public HttpResponse status(HttpStatus status) {
        this.status = status;
        return this;
    }

    public HttpResponse header(String name, String value) {
        headers.put(name, value);
        return this;
    }

    /**
     * Set a UTF-8 string body. Automatically sets Content-Length.
     */
    public HttpResponse body(String text) {
        this.body = text.getBytes(StandardCharsets.UTF_8);
        headers.put("Content-Length", String.valueOf(this.body.length));
        return this;
    }

    /**
     * Set a raw byte body. Automatically sets Content-Length.
     */
    public HttpResponse body(byte[] data) {
        this.body = data;
        headers.put("Content-Length", String.valueOf(data.length));
        return this;
    }

    public HttpStatus getStatus() {
        return status;
    }

    /**
     * Serialize the full HTTP response onto the wire.
     *
     * Format per RFC 7230 §3.1.2:
     *   HTTP/1.1 {status-code} {reason-phrase}\r\n
     *   {header-name}: {header-value}\r\n
     *   ...
     *   \r\n
     *   {body}
     */
    public void writeTo(OutputStream out) throws IOException {
        StringBuilder sb = new StringBuilder();

        // Status line
        sb.append("HTTP/1.1 ")
          .append(status.getCode())
          .append(" ")
          .append(status.getReasonPhrase())
          .append("\r\n");

        // Ensure Content-Length is set if there's a body
        if (body.length > 0 && !headers.containsKey("Content-Length")) {
            headers.put("Content-Length", String.valueOf(body.length));
        }

        // Headers
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            sb.append(entry.getKey())
              .append(": ")
              .append(entry.getValue())
              .append("\r\n");
        }

        // Blank line separating headers from body
        sb.append("\r\n");

        // Write the header block as UTF-8
        out.write(sb.toString().getBytes(StandardCharsets.UTF_8));

        // Write the body (may be binary, so write as raw bytes)
        if (body.length > 0) {
            out.write(body);
        }

        out.flush();
    }
}

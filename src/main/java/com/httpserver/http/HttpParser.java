package com.httpserver.http;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Parses raw HTTP/1.1 requests from an InputStream.
 *
 * This parser reads bytes off the wire and validates them per RFC 7230:
 *
 *   1. REQUEST LINE:  "GET /path HTTP/1.1\r\n"
 *   2. HEADERS:       "Host: example.com\r\n" (repeats until blank line)
 *   3. BLANK LINE:    "\r\n"
 *   4. BODY:          (optional, Content-Length bytes)
 *
 * Throws BadRequestException on protocol or syntax violations (which converts
 * directly to a 400 Bad Request HTTP response).
 */
public class HttpParser {

    // Prevent absurdly long lines from exhausting server memory (8 KB)
    private static final int MAX_LINE_LENGTH = 8192;
    // Prevent absurdly large bodies (10 MB)
    private static final int MAX_BODY_SIZE = 10 * 1024 * 1024;

    /**
     * Parse one HTTP request from the input stream.
     *
     * @param rawInput the socket's InputStream (not buffered — we wrap it)
     * @return a parsed HttpRequest, or null if the connection was closed (EOF)
     * @throws BadRequestException on protocol or syntax errors
     * @throws IOException on socket/network I/O errors
     */
    public static HttpRequest parse(InputStream rawInput) throws IOException {
        BufferedInputStream in = rawInput instanceof BufferedInputStream
                ? (BufferedInputStream) rawInput
                : new BufferedInputStream(rawInput);

        // --- 1. Read the request line ---
        String requestLine = readLine(in);
        if (requestLine == null) {
            // Client closed the connection cleanly — not an error
            return null;
        }

        // Skip any leading blank lines (RFC 7230 §3.5 allows this)
        while (requestLine.isEmpty()) {
            requestLine = readLine(in);
            if (requestLine == null) {
                return null;
            }
        }

        // Parse "METHOD /path HTTP/1.1"
        String[] parts = requestLine.split(" ");
        if (parts.length != 3) {
            throw new BadRequestException("Malformed request line: " + requestLine);
        }

        HttpMethod method;
        try {
            method = HttpMethod.fromString(parts[0]);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Unsupported HTTP method: " + parts[0]);
        }

        String path = parts[1];
        String httpVersion = parts[2];

        if (!httpVersion.startsWith("HTTP/")) {
            throw new BadRequestException("Invalid HTTP version: " + httpVersion);
        }

        // --- 2. Read headers ---
        Map<String, String> headers = new HashMap<>();
        String headerLine;
        while ((headerLine = readLine(in)) != null && !headerLine.isEmpty()) {
            // Header format: "Name: Value"
            int colonIndex = headerLine.indexOf(':');
            if (colonIndex <= 0) {
                throw new BadRequestException("Malformed header (missing colon): " + headerLine);
            }
            // Store header names in lowercase for case-insensitive lookup
            String name = headerLine.substring(0, colonIndex).trim().toLowerCase();
            String value = headerLine.substring(colonIndex + 1).trim();
            headers.put(name, value);
        }

        // Reject Transfer-Encoding (we only support Content-Length framing)
        if (headers.containsKey("transfer-encoding")) {
            throw new BadRequestException("Transfer-Encoding is not supported; use Content-Length");
        }

        // --- 3. Read body (if Content-Length is present) ---
        byte[] body = null;
        String contentLengthStr = headers.get("content-length");
        if (contentLengthStr != null) {
            int contentLength;
            try {
                contentLength = Integer.parseInt(contentLengthStr.trim());
            } catch (NumberFormatException e) {
                throw new BadRequestException("Invalid Content-Length header: " + contentLengthStr);
            }

            if (contentLength < 0) {
                throw new BadRequestException("Negative Content-Length: " + contentLength);
            }
            if (contentLength > MAX_BODY_SIZE) {
                throw new BadRequestException("Body too large: " + contentLength + " bytes (max " + MAX_BODY_SIZE + ")");
            }

            // Read exactly contentLength bytes
            body = new byte[contentLength];
            int totalRead = 0;
            while (totalRead < contentLength) {
                int bytesRead = in.read(body, totalRead, contentLength - totalRead);
                if (bytesRead == -1) {
                    throw new BadRequestException("Connection closed before body was fully read. "
                            + "Expected " + contentLength + " bytes, got " + totalRead);
                }
                totalRead += bytesRead;
            }
        }

        return new HttpRequest(method, path, httpVersion, headers, body);
    }

    /**
     * Read one line terminated by \r\n from the input stream.
     *
     * Returns the line content WITHOUT the trailing \r\n.
     * Returns null on EOF (connection closed).
     */
    private static String readLine(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        boolean prevWasCR = false;

        while (true) {
            int b = in.read();
            if (b == -1) {
                // EOF — return what we have, or null if nothing was read
                return sb.length() > 0 ? sb.toString() : null;
            }

            char c = (char) b;

            if (c == '\r') {
                prevWasCR = true;
                continue;
            }

            if (c == '\n') {
                if (prevWasCR) {
                    // Normal \r\n line ending
                    return sb.toString();
                }
                // Bare \n without \r — lenient parsing
                return sb.toString();
            }

            // If we saw \r but next char isn't \n, include the \r
            if (prevWasCR) {
                sb.append('\r');
                prevWasCR = false;
            }

            sb.append(c);

            if (sb.length() > MAX_LINE_LENGTH) {
                throw new BadRequestException("Header line exceeds maximum length of " + MAX_LINE_LENGTH + " chars");
            }
        }
    }
}

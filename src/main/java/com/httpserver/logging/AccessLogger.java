package com.httpserver.logging;

import com.httpserver.http.HttpMethod;
import com.httpserver.http.HttpStatus;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Thread-safe access logger.
 *
 * Records standard HTTP access logs with timestamp, worker thread,
 * HTTP method, request path, status code, and latency in milliseconds.
 *
 * Format:
 *   [2026-09-08 16:15:00.123] [http-worker-1] GET /index.html 200 OK - 1.25ms
 */
public class AccessLogger {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    /**
     * Log a completed HTTP request and response.
     *
     * @param threadName name of the worker thread that processed the request
     * @param method HTTP method (or null if request failed before parsing method)
     * @param path request path (or null)
     * @param status HTTP response status code
     * @param durationNanos total elapsed time in nanoseconds
     */
    public static synchronized void log(String threadName, HttpMethod method, String path,
                                        HttpStatus status, long durationNanos) {
        String timestamp = LocalDateTime.now().format(FORMATTER);
        String methodStr = method != null ? method.name() : "-";
        String pathStr = path != null ? path : "-";
        int statusCode = status != null ? status.getCode() : 500;
        String reason = status != null ? status.getReasonPhrase() : "Unknown";
        double durationMs = durationNanos / 1_000_000.0;

        String logEntry = String.format("[%s] [%s] %s %s %d %s - %.2fms",
                timestamp, threadName, methodStr, pathStr, statusCode, reason, durationMs);

        System.out.println(logEntry);
    }
}

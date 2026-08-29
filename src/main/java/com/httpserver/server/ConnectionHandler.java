package com.httpserver.server;

import com.httpserver.http.BadRequestException;
import com.httpserver.http.HttpMethod;
import com.httpserver.http.HttpParser;
import com.httpserver.http.HttpRequest;
import com.httpserver.http.HttpResponse;
import com.httpserver.http.HttpStatus;
import com.httpserver.logging.AccessLogger;
import com.httpserver.routing.Router;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;

/**
 * Handles a single TCP connection over its entire lifecycle.
 *
 * Implements persistent HTTP/1.1 connections, reads requests, delegates to Router,
 * handles malformed requests with 400 Bad Request, catches server errors with 500,
 * and records access logs with latency.
 */
public class ConnectionHandler implements Runnable {
    private static final int DEFAULT_IDLE_TIMEOUT_MS = 5000;

    private final Socket socket;
    private final int idleTimeoutMs;
    private final Router router;

    public ConnectionHandler(Socket socket, Router router) {
        this(socket, DEFAULT_IDLE_TIMEOUT_MS, router);
    }

    public ConnectionHandler(Socket socket, int idleTimeoutMs, Router router) {
        this.socket = socket;
        this.idleTimeoutMs = idleTimeoutMs;
        this.router = router;
    }

    @Override
    public void run() {
        try (socket) {
            socket.setSoTimeout(idleTimeoutMs);

            BufferedInputStream in = new BufferedInputStream(socket.getInputStream());
            OutputStream out = socket.getOutputStream();

            boolean keepAlive = true;
            int requestCount = 0;

            while (keepAlive && !socket.isClosed()) {
                long startTime = System.nanoTime();
                HttpRequest request = null;
                HttpResponse response = null;
                HttpMethod method = null;
                String path = null;

                try {
                    request = HttpParser.parse(in);

                    if (request == null) {
                        // Client closed connection cleanly (EOF)
                        break;
                    }

                    requestCount++;
                    method = request.getMethod();
                    path = request.getPath();
                    keepAlive = shouldKeepAlive(request);

                    // Route request to handler
                    response = router.route(request);

                } catch (SocketTimeoutException e) {
                    // Timeout while waiting for next request (keep-alive idle timeout)
                    // or client stalled mid-request. Break and close socket cleanly.
                    break;
                } catch (BadRequestException e) {
                    // Protocol / syntax violation -> 400 Bad Request
                    HttpStatus status = e.getStatus() != null ? e.getStatus() : HttpStatus.BAD_REQUEST;
                    keepAlive = false; // Always close connection on framing/parsing failure

                    response = new HttpResponse()
                            .status(status)
                            .header("Content-Type", "text/plain; charset=utf-8")
                            .body(status.getCode() + " " + status.getReasonPhrase() + ": " + e.getMessage() + "\n");
                } catch (Throwable t) {
                    // Uncaught server error -> 500 Internal Server Error
                    keepAlive = false;

                    response = new HttpResponse()
                            .status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .header("Content-Type", "text/plain; charset=utf-8")
                            .body("500 Internal Server Error: " + t.getMessage() + "\n");
                }

                if (response != null) {
                    response.header("Connection", keepAlive ? "keep-alive" : "close");

                    try {
                        response.writeTo(out);
                    } catch (IOException ioException) {
                        // Connection reset or closed while writing response
                        break;
                    }

                    long durationNanos = System.nanoTime() - startTime;
                    AccessLogger.log(
                            Thread.currentThread().getName(),
                            method,
                            path,
                            response.getStatus(),
                            durationNanos
                    );
                }

                if (!keepAlive) {
                    break;
                }
            }
        } catch (SocketException e) {
            // Connection reset by peer or closed abnormally
        } catch (IOException e) {
            System.err.println("[" + Thread.currentThread().getName() + "] I/O error: " + e.getMessage());
        }
    }

    /**
     * Determines whether the connection should be kept alive according to RFC 7230 §6.3:
     * - In HTTP/1.1: persistent by default unless "Connection: close" is specified.
     * - In HTTP/1.0: non-persistent by default unless "Connection: keep-alive" is specified.
     */
    private boolean shouldKeepAlive(HttpRequest request) {
        String connectionHeader = request.getHeader("connection");

        if ("HTTP/1.0".equalsIgnoreCase(request.getHttpVersion())) {
            return "keep-alive".equalsIgnoreCase(connectionHeader);
        }

        // HTTP/1.1 or higher defaults to keep-alive
        return !"close".equalsIgnoreCase(connectionHeader);
    }
}

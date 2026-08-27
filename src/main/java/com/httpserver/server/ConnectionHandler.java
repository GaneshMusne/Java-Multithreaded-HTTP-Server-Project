package com.httpserver.server;

import com.httpserver.http.HttpParser;
import com.httpserver.http.HttpRequest;
import com.httpserver.http.HttpResponse;
import com.httpserver.http.HttpStatus;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;

/**
 * Handles a single TCP connection over its entire lifecycle.
 *
 * Supports HTTP/1.1 persistent connections (keep-alive) by looping to read
 * multiple requests on the same socket until:
 *   - The client sends Connection: close (or HTTP/1.0 without keep-alive)
 *   - The client closes the connection (EOF)
 *   - An idle read timeout occurs (SocketTimeoutException)
 *   - An I/O error occurs
 */
public class ConnectionHandler implements Runnable {
    private static final int DEFAULT_IDLE_TIMEOUT_MS = 5000;

    private final Socket socket;
    private final int idleTimeoutMs;

    public ConnectionHandler(Socket socket) {
        this(socket, DEFAULT_IDLE_TIMEOUT_MS);
    }

    public ConnectionHandler(Socket socket, int idleTimeoutMs) {
        this.socket = socket;
        this.idleTimeoutMs = idleTimeoutMs;
    }

    @Override
    public void run() {
        try (socket) {
            // Set socket read timeout so idle clients don't occupy a worker thread forever
            socket.setSoTimeout(idleTimeoutMs);

            // Wrap the raw InputStream ONCE for the connection's lifetime.
            // If we wrapped it per-request, BufferedInputStream would discard prefetched bytes.
            BufferedInputStream in = new BufferedInputStream(socket.getInputStream());
            OutputStream out = socket.getOutputStream();

            boolean keepAlive = true;
            int requestCount = 0;

            while (keepAlive && !socket.isClosed()) {
                HttpRequest request;
                try {
                    request = HttpParser.parse(in);
                } catch (SocketTimeoutException e) {
                    if (requestCount > 0) {
                        System.out.println("[" + Thread.currentThread().getName()
                                + "] Keep-alive connection idle timeout (" + idleTimeoutMs + "ms) reached. Closing socket.");
                    } else {
                        System.out.println("[" + Thread.currentThread().getName()
                                + "] Read timeout before receiving request. Closing socket.");
                    }
                    break;
                }

                if (request == null) {
                    // Client closed connection cleanly (EOF)
                    break;
                }

                requestCount++;
                System.out.println("[" + Thread.currentThread().getName() + "] Request #" + requestCount
                        + ": " + request);

                // Determine if we should maintain persistent connection
                keepAlive = shouldKeepAlive(request);

                // Build and send response
                HttpResponse response = new HttpResponse()
                        .status(HttpStatus.OK)
                        .header("Content-Type", "text/plain")
                        .header("Connection", keepAlive ? "keep-alive" : "close")
                        .body("Hello, World!\n");

                response.writeTo(out);

                if (!keepAlive) {
                    break;
                }
            }
        } catch (SocketException e) {
            // Client abruptly disconnected (e.g. connection reset by peer)
            System.out.println("[" + Thread.currentThread().getName() + "] Connection reset/closed: " + e.getMessage());
        } catch (IOException e) {
            System.err.println("[" + Thread.currentThread().getName() + "] Error handling connection: " + e.getMessage());
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

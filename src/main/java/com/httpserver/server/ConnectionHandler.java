package com.httpserver.server;

import com.httpserver.http.HttpParser;
import com.httpserver.http.HttpRequest;
import com.httpserver.http.HttpResponse;
import com.httpserver.http.HttpStatus;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;

/**
 * Handles a single TCP connection.
 *
 * In Phase 1, this is simple: read one request, send a hardcoded response,
 * close the socket. Later phases will add keep-alive looping and routing.
 *
 * Implements Runnable so it can be submitted to a thread pool in Phase 2.
 */
public class ConnectionHandler implements Runnable {
    private final Socket socket;

    public ConnectionHandler(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        try (socket) { // try-with-resources auto-closes the socket
            // Parse the incoming HTTP request
            HttpRequest request = HttpParser.parse(socket.getInputStream());

            if (request == null) {
                // Client closed connection immediately (no data sent)
                return;
            }

            System.out.println("Received: " + request);

            // Build and send a hardcoded response
            OutputStream out = socket.getOutputStream();
            new HttpResponse()
                    .status(HttpStatus.OK)
                    .header("Content-Type", "text/plain")
                    .header("Connection", "close")
                    .body("Hello, World!\n")
                    .writeTo(out);

        } catch (IOException e) {
            System.err.println("Error handling connection: " + e.getMessage());
        }
    }
}

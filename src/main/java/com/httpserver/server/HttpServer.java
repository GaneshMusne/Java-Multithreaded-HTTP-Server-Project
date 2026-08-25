package com.httpserver.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * The core HTTP server.
 *
 * Opens a TCP ServerSocket and enters an accept loop: each incoming
 * connection is handed off to a ConnectionHandler.
 *
 * Phase 1: single-threaded — runs the handler directly on the accept thread.
 * Phase 2 will add an ExecutorService thread pool here.
 */
public class HttpServer {
    private final int port;

    public HttpServer(int port) {
        this.port = port;
    }

    /**
     * Start the server. This method blocks forever (accept loop).
     *
     * How the accept loop works:
     *   1. ServerSocket.accept() blocks until a client connects
     *   2. accept() returns a Socket representing that TCP connection
     *   3. We wrap the Socket in a ConnectionHandler and run it
     *   4. After the handler finishes, we go back to step 1
     *
     * Note: In Phase 1, step 3 runs synchronously on this thread,
     * meaning only ONE client can be served at a time. We fix this
     * in Phase 2 with a thread pool.
     */
    public void start() throws IOException {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Server started on port " + port);
            System.out.println("Try: curl -v http://localhost:" + port + "/");

            while (true) {
                // Block until a client connects
                Socket clientSocket = serverSocket.accept();
                System.out.println("Connection from: "
                        + clientSocket.getInetAddress().getHostAddress()
                        + ":" + clientSocket.getPort());

                // Handle the connection (synchronously for now)
                ConnectionHandler handler = new ConnectionHandler(clientSocket);
                handler.run();
            }
        }
    }
}

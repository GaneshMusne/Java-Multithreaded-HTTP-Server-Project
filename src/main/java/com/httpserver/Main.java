package com.httpserver;

import com.httpserver.server.HttpServer;

import java.io.IOException;

/**
 * Entry point for the HTTP server.
 *
 * Parses command-line arguments and starts the server.
 *
 * Usage:
 *   java com.httpserver.Main [--port 8080] [--threads 10]
 */
public class Main {

    private static final int DEFAULT_PORT = 8080;
    private static final int DEFAULT_THREADS = 10;

    public static void main(String[] args) {
        int port = DEFAULT_PORT;
        int threads = DEFAULT_THREADS;

        // CLI argument parsing
        for (int i = 0; i < args.length; i++) {
            if ("--port".equals(args[i]) && i + 1 < args.length) {
                try {
                    port = Integer.parseInt(args[++i]);
                } catch (NumberFormatException e) {
                    System.err.println("Invalid port number: " + args[i]);
                    System.exit(1);
                }
            } else if ("--threads".equals(args[i]) && i + 1 < args.length) {
                try {
                    threads = Integer.parseInt(args[++i]);
                } catch (NumberFormatException e) {
                    System.err.println("Invalid thread count: " + args[i]);
                    System.exit(1);
                }
            }
        }

        try {
            HttpServer server = new HttpServer(port, threads);
            server.start();
        } catch (IOException e) {
            System.err.println("Failed to start server: " + e.getMessage());
            System.exit(1);
        }
    }
}

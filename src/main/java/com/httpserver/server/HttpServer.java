package com.httpserver.server;

import com.httpserver.handler.EchoHandler;
import com.httpserver.handler.StaticFileHandler;
import com.httpserver.http.HttpMethod;
import com.httpserver.routing.Router;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The core HTTP server.
 *
 * Opens a TCP ServerSocket and uses a fixed thread pool (ExecutorService)
 * to handle incoming connections concurrently, dispatching requests through
 * a Router.
 */
public class HttpServer {
    private static final int DEFAULT_THREADS = 10;
    private static final int DEFAULT_IDLE_TIMEOUT_MS = 5000;

    private final int port;
    private final int threadPoolSize;
    private final int idleTimeoutMs;
    private final Router router;
    private final AtomicBoolean running = new AtomicBoolean(false);

    private ServerSocket serverSocket;
    private ExecutorService threadPool;

    public HttpServer(int port) {
        this(port, DEFAULT_THREADS, DEFAULT_IDLE_TIMEOUT_MS, createDefaultRouter());
    }

    public HttpServer(int port, int threadPoolSize) {
        this(port, threadPoolSize, DEFAULT_IDLE_TIMEOUT_MS, createDefaultRouter());
    }

    public HttpServer(int port, int threadPoolSize, int idleTimeoutMs) {
        this(port, threadPoolSize, idleTimeoutMs, createDefaultRouter());
    }

    public HttpServer(int port, int threadPoolSize, int idleTimeoutMs, Router router) {
        this.port = port;
        this.threadPoolSize = threadPoolSize;
        this.idleTimeoutMs = idleTimeoutMs;
        this.router = router != null ? router : createDefaultRouter();
    }

    /**
     * Start the server and enter the accept loop.
     */
    public void start() throws IOException {
        running.set(true);

        // Custom ThreadFactory to give worker threads meaningful names for logging/debugging
        ThreadFactory threadFactory = new ThreadFactory() {
            private final AtomicInteger counter = new AtomicInteger(1);
            @Override
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r, "http-worker-" + counter.getAndIncrement());
                thread.setDaemon(false);
                return thread;
            }
        };

        threadPool = Executors.newFixedThreadPool(threadPoolSize, threadFactory);

        // Register JVM shutdown hook for graceful termination (e.g. on Ctrl+C / SIGINT)
        Thread shutdownHook = new Thread(this::stop, "shutdown-hook");
        Runtime.getRuntime().addShutdownHook(shutdownHook);

        try (ServerSocket ss = new ServerSocket(port)) {
            this.serverSocket = ss;
            System.out.println("Server started on port " + port
                    + " with " + threadPoolSize + " worker threads (idle timeout: " + idleTimeoutMs + "ms)");
            System.out.println("Static files served from: " + (router.getStaticFileHandler() != null
                    ? router.getStaticFileHandler().getBaseDirectory() : "none"));
            System.out.println("Try: curl -v http://localhost:" + port + "/");

            while (running.get()) {
                try {
                    // Block until a client connects
                    Socket clientSocket = serverSocket.accept();
                    System.out.println("[" + Thread.currentThread().getName() + "] Accepted connection from: "
                            + clientSocket.getRemoteSocketAddress());

                    // Submit connection handling to the worker thread pool
                    threadPool.submit(new ConnectionHandler(clientSocket, idleTimeoutMs, router));
                } catch (SocketException e) {
                    if (!running.get()) {
                        // Expected when serverSocket.close() is called during shutdown
                        break;
                    }
                    System.err.println("Socket error during accept: " + e.getMessage());
                }
            }
        } finally {
            stop();
        }
    }

    /**
     * Gracefully stop the server and shut down the thread pool.
     */
    public void stop() {
        if (running.compareAndSet(true, false)) {
            System.out.println("Stopping HTTP server...");

            // 1. Close ServerSocket to stop accepting new connections
            if (serverSocket != null && !serverSocket.isClosed()) {
                try {
                    serverSocket.close();
                } catch (IOException e) {
                    System.err.println("Error closing server socket: " + e.getMessage());
                }
            }

            // 2. Shut down thread pool gracefully
            if (threadPool != null && !threadPool.isShutdown()) {
                threadPool.shutdown();
                try {
                    if (!threadPool.awaitTermination(5, TimeUnit.SECONDS)) {
                        System.out.println("Forcing thread pool shutdown...");
                        threadPool.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    threadPool.shutdownNow();
                    Thread.currentThread().interrupt();
                }
            }

            System.out.println("Server stopped cleanly.");
        }
    }


    /**
     * Creates a default router with static file serving from public/
     * and built-in echo endpoints.
     */
    public static Router createDefaultRouter() {
        Router router = new Router();
        router.setStaticFileHandler(new StaticFileHandler("public"));

        EchoHandler echoHandler = new EchoHandler();
        router.addRoute(HttpMethod.POST, "/echo", echoHandler);
        router.addRoute(HttpMethod.GET, "/echo", echoHandler);

        return router;
    }
}

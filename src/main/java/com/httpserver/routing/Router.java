package com.httpserver.routing;

import com.httpserver.handler.RequestHandler;
import com.httpserver.handler.StaticFileHandler;
import com.httpserver.http.HttpMethod;
import com.httpserver.http.HttpRequest;
import com.httpserver.http.HttpResponse;
import com.httpserver.http.HttpStatus;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Routing engine that maps HTTP method + path to request handlers.
 *
 * Provides:
 * - Direct route matching for API endpoints
 * - 405 Method Not Allowed handling with 'Allow' header when path exists
 * - Fallback to static file handler for unmapped GET/HEAD paths
 * - 404 Not Found for unmapped requests
 */
public class Router {

    private final Map<String, Map<HttpMethod, RequestHandler>> routes = new ConcurrentHashMap<>();
    private StaticFileHandler staticFileHandler;

    public Router addRoute(HttpMethod method, String path, RequestHandler handler) {
        String normalized = normalizePath(path);
        routes.computeIfAbsent(normalized, k -> new ConcurrentHashMap<>()).put(method, handler);
        return this;
    }

    public Router get(String path, RequestHandler handler) {
        return addRoute(HttpMethod.GET, path, handler);
    }

    public Router post(String path, RequestHandler handler) {
        return addRoute(HttpMethod.POST, path, handler);
    }

    public Router setStaticFileHandler(StaticFileHandler staticFileHandler) {
        this.staticFileHandler = staticFileHandler;
        return this;
    }

    public StaticFileHandler getStaticFileHandler() {
        return staticFileHandler;
    }

    /**
     * Dispatches the incoming request to the matching handler or fallback.
     */
    public HttpResponse route(HttpRequest request) {
        String path = normalizePath(request.getPath());
        Map<HttpMethod, RequestHandler> methodHandlers = routes.get(path);

        // 1. Check if an explicit route matches the path
        if (methodHandlers != null) {
            RequestHandler handler = methodHandlers.get(request.getMethod());
            if (handler != null) {
                return handler.handle(request);
            }

            // Path matched, but method is unsupported on this route
            String allowed = methodHandlers.keySet().stream()
                    .map(Enum::name)
                    .sorted()
                    .collect(Collectors.joining(", "));

            return new HttpResponse()
                    .status(HttpStatus.METHOD_NOT_ALLOWED)
                    .header("Allow", allowed)
                    .header("Content-Type", "text/plain; charset=utf-8")
                    .body("405 Method Not Allowed. Allowed: " + allowed + "\n");
        }

        // 2. Fallback to static file handler (if configured)
        if (staticFileHandler != null) {
            HttpResponse staticResponse = staticFileHandler.handle(request);
            if (staticResponse != null) {
                return staticResponse;
            }
        }

        // 3. Nothing matched -> 404 Not Found
        return new HttpResponse()
                .status(HttpStatus.NOT_FOUND)
                .header("Content-Type", "text/plain; charset=utf-8")
                .body("404 Not Found: " + request.getPath() + "\n");
    }

    private String normalizePath(String path) {
        if (path == null || path.isEmpty()) {
            return "/";
        }
        int queryIdx = path.indexOf('?');
        if (queryIdx >= 0) {
            path = path.substring(0, queryIdx);
        }
        if (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return path;
    }
}

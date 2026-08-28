package com.httpserver.handler;

import com.httpserver.http.HttpRequest;
import com.httpserver.http.HttpResponse;

/**
 * Functional interface for HTTP request handlers.
 *
 * Takes an HttpRequest and returns an HttpResponse.
 * Can be implemented as a class or passed as a lambda expression.
 */
@FunctionalInterface
public interface RequestHandler {
    HttpResponse handle(HttpRequest request);
}

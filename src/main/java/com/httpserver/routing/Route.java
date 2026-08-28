package com.httpserver.routing;

import com.httpserver.handler.RequestHandler;
import com.httpserver.http.HttpMethod;

/**
 * Represents a single registered route mapping a method and path to a handler.
 */
public record Route(HttpMethod method, String path, RequestHandler handler) {
}

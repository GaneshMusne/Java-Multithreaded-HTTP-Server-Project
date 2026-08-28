package com.httpserver.handler;

import com.httpserver.http.HttpRequest;
import com.httpserver.http.HttpResponse;
import com.httpserver.http.HttpStatus;

/**
 * Echo handler for testing request payloads and metadata.
 *
 * If a request body is present, it echoes the exact body back.
 * If empty, it returns a text description of the incoming request.
 */
public class EchoHandler implements RequestHandler {

    @Override
    public HttpResponse handle(HttpRequest request) {
        byte[] body = request.getBody();

        if (body.length > 0) {
            String contentType = request.getHeader("content-type");
            if (contentType == null) {
                contentType = "text/plain; charset=utf-8";
            }

            return new HttpResponse()
                    .status(HttpStatus.OK)
                    .header("Content-Type", contentType)
                    .body(body);
        }

        // If no body, return basic request info
        String info = "Echo response:\n"
                + "Method: " + request.getMethod() + "\n"
                + "Path: " + request.getPath() + "\n"
                + "Version: " + request.getHttpVersion() + "\n"
                + "Headers: " + request.getHeaders() + "\n";

        return new HttpResponse()
                .status(HttpStatus.OK)
                .header("Content-Type", "text/plain; charset=utf-8")
                .body(info);
    }
}

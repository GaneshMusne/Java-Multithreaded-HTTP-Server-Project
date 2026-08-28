package com.httpserver.handler;

import com.httpserver.http.HttpMethod;
import com.httpserver.http.HttpRequest;
import com.httpserver.http.HttpResponse;
import com.httpserver.http.HttpStatus;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * Serves static files from a base directory with MIME type resolution
 * and directory traversal protection (preventing path traversal attacks like ../).
 */
public class StaticFileHandler implements RequestHandler {

    private final Path baseDirectory;
    private static final Map<String, String> MIME_TYPES = new HashMap<>();

    static {
        MIME_TYPES.put("html", "text/html; charset=utf-8");
        MIME_TYPES.put("htm", "text/html; charset=utf-8");
        MIME_TYPES.put("css", "text/css; charset=utf-8");
        MIME_TYPES.put("js", "application/javascript; charset=utf-8");
        MIME_TYPES.put("mjs", "application/javascript; charset=utf-8");
        MIME_TYPES.put("json", "application/json; charset=utf-8");
        MIME_TYPES.put("png", "image/png");
        MIME_TYPES.put("jpg", "image/jpeg");
        MIME_TYPES.put("jpeg", "image/jpeg");
        MIME_TYPES.put("gif", "image/gif");
        MIME_TYPES.put("svg", "image/svg+xml");
        MIME_TYPES.put("ico", "image/x-icon");
        MIME_TYPES.put("txt", "text/plain; charset=utf-8");
        MIME_TYPES.put("pdf", "application/pdf");
    }

    public StaticFileHandler(String baseDirectoryPath) {
        this.baseDirectory = Paths.get(baseDirectoryPath).toAbsolutePath().normalize();
    }

    public StaticFileHandler(Path baseDirectory) {
        this.baseDirectory = baseDirectory.toAbsolutePath().normalize();
    }

    @Override
    public HttpResponse handle(HttpRequest request) {
        String rawPath = request.getPath();

        // Strip query parameters if present
        int queryIdx = rawPath.indexOf('?');
        if (queryIdx >= 0) {
            rawPath = rawPath.substring(0, queryIdx);
        }

        // Default root to /index.html
        if (rawPath.equals("/") || rawPath.isEmpty()) {
            rawPath = "/index.html";
        }

        // Relative path without leading slash
        String relativePath = rawPath.startsWith("/") ? rawPath.substring(1) : rawPath;

        // Resolve against base directory and normalize
        Path targetPath = baseDirectory.resolve(relativePath).normalize();

        // Security check: Directory Traversal Protection
        // Ensure the normalized path does not escape the base directory
        if (!targetPath.startsWith(baseDirectory)) {
            return new HttpResponse()
                    .status(HttpStatus.FORBIDDEN)
                    .header("Content-Type", "text/plain; charset=utf-8")
                    .body("403 Forbidden: Directory traversal attempt detected.\n");
        }

        // If path is a directory, attempt to serve index.html inside it
        if (Files.isDirectory(targetPath)) {
            targetPath = targetPath.resolve("index.html").normalize();
            if (!targetPath.startsWith(baseDirectory)) {
                return new HttpResponse()
                        .status(HttpStatus.FORBIDDEN)
                        .header("Content-Type", "text/plain; charset=utf-8")
                        .body("403 Forbidden\n");
            }
        }

        // Check if file exists and is readable
        if (!Files.exists(targetPath) || !Files.isRegularFile(targetPath)) {
            return null; // Return null to indicate no static file match (caller can 404)
        }

        // Method validation: static files only support GET and HEAD
        if (request.getMethod() != HttpMethod.GET && request.getMethod() != HttpMethod.HEAD) {
            return new HttpResponse()
                    .status(HttpStatus.METHOD_NOT_ALLOWED)
                    .header("Allow", "GET, HEAD")
                    .header("Content-Type", "text/plain; charset=utf-8")
                    .body("405 Method Not Allowed\n");
        }

        try {
            byte[] fileBytes = Files.readAllBytes(targetPath);
            String mimeType = getContentType(targetPath);

            HttpResponse response = new HttpResponse()
                    .status(HttpStatus.OK)
                    .header("Content-Type", mimeType);

            if (request.getMethod() == HttpMethod.HEAD) {
                // HEAD must return the same headers as GET without the body
                response.header("Content-Length", String.valueOf(fileBytes.length));
            } else {
                response.body(fileBytes);
            }

            return response;
        } catch (IOException e) {
            return new HttpResponse()
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .header("Content-Type", "text/plain; charset=utf-8")
                    .body("500 Internal Server Error: Failed to read static file.\n");
        }
    }

    private String getContentType(Path file) {
        String fileName = file.getFileName().toString();
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0 && dotIndex < fileName.length() - 1) {
            String extension = fileName.substring(dotIndex + 1).toLowerCase();
            return MIME_TYPES.getOrDefault(extension, "application/octet-stream");
        }
        return "application/octet-stream";
    }

    public Path getBaseDirectory() {
        return baseDirectory;
    }
}

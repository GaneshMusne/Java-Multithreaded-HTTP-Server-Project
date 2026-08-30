package com.httpserver.http;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the HTTP/1.1 request parser.
 *
 * Covers valid requests, malformed requests, and edge cases.
 * Each test constructs a raw HTTP request as bytes, feeds it to
 * HttpParser.parse(), and asserts the parsed result or exception.
 */
class HttpParserTest {

    /**
     * Helper: build an InputStream from a raw HTTP request string.
     * Uses \r\n line endings as required by HTTP/1.1.
     */
    private InputStream toStream(String raw) {
        return new ByteArrayInputStream(raw.getBytes(StandardCharsets.UTF_8));
    }

    // ─────────────────────────────────────────────────────────────────────
    // Valid Requests
    // ─────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Valid Requests")
    class ValidRequests {

        @Test
        @DisplayName("Simple GET request with headers")
        void simpleGetWithHeaders() throws IOException {
            String raw = "GET /index.html HTTP/1.1\r\n"
                    + "Host: localhost:8080\r\n"
                    + "Accept: text/html\r\n"
                    + "User-Agent: TestClient/1.0\r\n"
                    + "\r\n";

            HttpRequest request = HttpParser.parse(toStream(raw));

            assertNotNull(request);
            assertEquals(HttpMethod.GET, request.getMethod());
            assertEquals("/index.html", request.getPath());
            assertEquals("HTTP/1.1", request.getHttpVersion());
            assertEquals("localhost:8080", request.getHeader("host"));
            assertEquals("text/html", request.getHeader("accept"));
            assertEquals("TestClient/1.0", request.getHeader("user-agent"));
            assertEquals(0, request.getBody().length);
        }

        @Test
        @DisplayName("POST request with JSON body")
        void postWithJsonBody() throws IOException {
            String body = "{\"name\":\"Ganesh\",\"role\":\"developer\"}";
            String raw = "POST /api/users HTTP/1.1\r\n"
                    + "Host: localhost:8080\r\n"
                    + "Content-Type: application/json\r\n"
                    + "Content-Length: " + body.length() + "\r\n"
                    + "\r\n"
                    + body;

            HttpRequest request = HttpParser.parse(toStream(raw));

            assertNotNull(request);
            assertEquals(HttpMethod.POST, request.getMethod());
            assertEquals("/api/users", request.getPath());
            assertEquals("application/json", request.getHeader("content-type"));
            assertEquals(body, request.getBodyAsString());
        }

        @Test
        @DisplayName("GET request with no body and no Content-Length")
        void getWithNoBody() throws IOException {
            String raw = "GET / HTTP/1.1\r\n"
                    + "Host: localhost\r\n"
                    + "\r\n";

            HttpRequest request = HttpParser.parse(toStream(raw));

            assertNotNull(request);
            assertEquals(HttpMethod.GET, request.getMethod());
            assertEquals("/", request.getPath());
            assertEquals(0, request.getBody().length);
        }

        @Test
        @DisplayName("PUT request with body")
        void putWithBody() throws IOException {
            String body = "updated content";
            String raw = "PUT /resource/42 HTTP/1.1\r\n"
                    + "Host: localhost\r\n"
                    + "Content-Length: " + body.length() + "\r\n"
                    + "\r\n"
                    + body;

            HttpRequest request = HttpParser.parse(toStream(raw));

            assertNotNull(request);
            assertEquals(HttpMethod.PUT, request.getMethod());
            assertEquals("/resource/42", request.getPath());
            assertEquals(body, request.getBodyAsString());
        }

        @Test
        @DisplayName("DELETE request")
        void deleteRequest() throws IOException {
            String raw = "DELETE /resource/42 HTTP/1.1\r\n"
                    + "Host: localhost\r\n"
                    + "\r\n";

            HttpRequest request = HttpParser.parse(toStream(raw));

            assertNotNull(request);
            assertEquals(HttpMethod.DELETE, request.getMethod());
            assertEquals("/resource/42", request.getPath());
        }

        @Test
        @DisplayName("HEAD request")
        void headRequest() throws IOException {
            String raw = "HEAD /index.html HTTP/1.1\r\n"
                    + "Host: localhost\r\n"
                    + "\r\n";

            HttpRequest request = HttpParser.parse(toStream(raw));

            assertNotNull(request);
            assertEquals(HttpMethod.HEAD, request.getMethod());
        }

        @Test
        @DisplayName("OPTIONS request")
        void optionsRequest() throws IOException {
            String raw = "OPTIONS * HTTP/1.1\r\n"
                    + "Host: localhost\r\n"
                    + "\r\n";

            HttpRequest request = HttpParser.parse(toStream(raw));

            assertNotNull(request);
            assertEquals(HttpMethod.OPTIONS, request.getMethod());
            assertEquals("*", request.getPath());
        }

        @Test
        @DisplayName("POST with Content-Length: 0 (empty body)")
        void postWithZeroContentLength() throws IOException {
            String raw = "POST /submit HTTP/1.1\r\n"
                    + "Host: localhost\r\n"
                    + "Content-Length: 0\r\n"
                    + "\r\n";

            HttpRequest request = HttpParser.parse(toStream(raw));

            assertNotNull(request);
            assertEquals(HttpMethod.POST, request.getMethod());
            assertEquals(0, request.getBody().length);
        }

        @Test
        @DisplayName("HTTP/1.0 request is parsed correctly")
        void http10Request() throws IOException {
            String raw = "GET / HTTP/1.0\r\n"
                    + "Host: localhost\r\n"
                    + "\r\n";

            HttpRequest request = HttpParser.parse(toStream(raw));

            assertNotNull(request);
            assertEquals("HTTP/1.0", request.getHttpVersion());
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Header Edge Cases
    // ─────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Header Edge Cases")
    class HeaderEdgeCases {

        @Test
        @DisplayName("Header names are stored case-insensitively (lowercase)")
        void headersCaseInsensitive() throws IOException {
            String raw = "GET / HTTP/1.1\r\n"
                    + "HOST: localhost\r\n"
                    + "Content-TYPE: text/html\r\n"
                    + "X-Custom-Header: custom-value\r\n"
                    + "\r\n";

            HttpRequest request = HttpParser.parse(toStream(raw));

            assertNotNull(request);
            assertEquals("localhost", request.getHeader("host"));
            assertEquals("localhost", request.getHeader("Host"));
            assertEquals("localhost", request.getHeader("HOST"));
            assertEquals("text/html", request.getHeader("content-type"));
            assertEquals("custom-value", request.getHeader("x-custom-header"));
        }

        @Test
        @DisplayName("Header values with leading/trailing whitespace are trimmed")
        void headerValuesWithWhitespace() throws IOException {
            String raw = "GET / HTTP/1.1\r\n"
                    + "Host:   localhost   \r\n"
                    + "Accept:  text/html  \r\n"
                    + "\r\n";

            HttpRequest request = HttpParser.parse(toStream(raw));

            assertNotNull(request);
            assertEquals("localhost", request.getHeader("host"));
            assertEquals("text/html", request.getHeader("accept"));
        }

        @Test
        @DisplayName("Header value containing colons (e.g. URL)")
        void headerValueWithColons() throws IOException {
            String raw = "GET / HTTP/1.1\r\n"
                    + "Host: localhost\r\n"
                    + "Referer: http://example.com:8080/path\r\n"
                    + "\r\n";

            HttpRequest request = HttpParser.parse(toStream(raw));

            assertNotNull(request);
            assertEquals("http://example.com:8080/path", request.getHeader("referer"));
        }

        @Test
        @DisplayName("Leading blank lines before request line are skipped (RFC 7230 §3.5)")
        void leadingBlankLines() throws IOException {
            String raw = "\r\n\r\n\r\nGET / HTTP/1.1\r\n"
                    + "Host: localhost\r\n"
                    + "\r\n";

            HttpRequest request = HttpParser.parse(toStream(raw));

            assertNotNull(request);
            assertEquals(HttpMethod.GET, request.getMethod());
            assertEquals("/", request.getPath());
        }

        @Test
        @DisplayName("Bare LF (no CR) treated as line ending (lenient parsing)")
        void bareLfLineEnding() throws IOException {
            String raw = "GET / HTTP/1.1\n"
                    + "Host: localhost\n"
                    + "\n";

            HttpRequest request = HttpParser.parse(toStream(raw));

            assertNotNull(request);
            assertEquals(HttpMethod.GET, request.getMethod());
            assertEquals("localhost", request.getHeader("host"));
        }

        @Test
        @DisplayName("Request with many headers")
        void manyHeaders() throws IOException {
            StringBuilder raw = new StringBuilder("GET / HTTP/1.1\r\n");
            raw.append("Host: localhost\r\n");
            for (int i = 0; i < 50; i++) {
                raw.append("X-Header-").append(i).append(": value-").append(i).append("\r\n");
            }
            raw.append("\r\n");

            HttpRequest request = HttpParser.parse(toStream(raw.toString()));

            assertNotNull(request);
            // 51 headers: Host + 50 custom
            assertEquals(51, request.getHeaders().size());
            assertEquals("value-0", request.getHeader("x-header-0"));
            assertEquals("value-49", request.getHeader("x-header-49"));
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Malformed Requests (should throw BadRequestException)
    // ─────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Malformed Requests")
    class MalformedRequests {

        @Test
        @DisplayName("Malformed request line: missing path and version")
        void malformedRequestLineMissingParts() {
            String raw = "GET\r\n\r\n";

            BadRequestException ex = assertThrows(BadRequestException.class,
                    () -> HttpParser.parse(toStream(raw)));
            assertTrue(ex.getMessage().contains("Malformed request line"));
        }

        @Test
        @DisplayName("Malformed request line: missing HTTP version")
        void malformedRequestLineMissingVersion() {
            String raw = "GET /path\r\n\r\n";

            BadRequestException ex = assertThrows(BadRequestException.class,
                    () -> HttpParser.parse(toStream(raw)));
            assertTrue(ex.getMessage().contains("Malformed request line"));
        }

        @Test
        @DisplayName("Invalid HTTP version (not starting with HTTP/)")
        void invalidHttpVersion() {
            String raw = "GET / FTP/1.0\r\n"
                    + "Host: localhost\r\n"
                    + "\r\n";

            BadRequestException ex = assertThrows(BadRequestException.class,
                    () -> HttpParser.parse(toStream(raw)));
            assertTrue(ex.getMessage().contains("Invalid HTTP version"));
        }

        @Test
        @DisplayName("Unsupported HTTP method")
        void unsupportedMethod() {
            String raw = "PATCH /resource HTTP/1.1\r\n"
                    + "Host: localhost\r\n"
                    + "\r\n";

            BadRequestException ex = assertThrows(BadRequestException.class,
                    () -> HttpParser.parse(toStream(raw)));
            assertTrue(ex.getMessage().contains("Unsupported HTTP method"));
        }

        @Test
        @DisplayName("Header without colon separator")
        void headerWithoutColon() {
            String raw = "GET / HTTP/1.1\r\n"
                    + "MissingColon\r\n"
                    + "\r\n";

            BadRequestException ex = assertThrows(BadRequestException.class,
                    () -> HttpParser.parse(toStream(raw)));
            assertTrue(ex.getMessage().contains("Malformed header"));
        }

        @Test
        @DisplayName("Non-numeric Content-Length")
        void nonNumericContentLength() {
            String raw = "POST /data HTTP/1.1\r\n"
                    + "Host: localhost\r\n"
                    + "Content-Length: abc\r\n"
                    + "\r\n";

            BadRequestException ex = assertThrows(BadRequestException.class,
                    () -> HttpParser.parse(toStream(raw)));
            assertTrue(ex.getMessage().contains("Invalid Content-Length"));
        }

        @Test
        @DisplayName("Negative Content-Length")
        void negativeContentLength() {
            String raw = "POST /data HTTP/1.1\r\n"
                    + "Host: localhost\r\n"
                    + "Content-Length: -10\r\n"
                    + "\r\n";

            BadRequestException ex = assertThrows(BadRequestException.class,
                    () -> HttpParser.parse(toStream(raw)));
            assertTrue(ex.getMessage().contains("Negative Content-Length"));
        }

        @Test
        @DisplayName("Transfer-Encoding header is rejected")
        void transferEncodingRejected() {
            String raw = "POST /data HTTP/1.1\r\n"
                    + "Host: localhost\r\n"
                    + "Transfer-Encoding: chunked\r\n"
                    + "\r\n";

            BadRequestException ex = assertThrows(BadRequestException.class,
                    () -> HttpParser.parse(toStream(raw)));
            assertTrue(ex.getMessage().contains("Transfer-Encoding"));
        }

        @Test
        @DisplayName("Connection closed before body fully read")
        void incompleteBody() {
            // Declare Content-Length: 100 but only provide 5 bytes
            String raw = "POST /data HTTP/1.1\r\n"
                    + "Host: localhost\r\n"
                    + "Content-Length: 100\r\n"
                    + "\r\n"
                    + "short";

            BadRequestException ex = assertThrows(BadRequestException.class,
                    () -> HttpParser.parse(toStream(raw)));
            assertTrue(ex.getMessage().contains("Connection closed before body was fully read"));
        }

        @Test
        @DisplayName("Completely empty input returns null (EOF)")
        void emptyInputReturnsNull() throws IOException {
            HttpRequest request = HttpParser.parse(toStream(""));
            assertNull(request);
        }

        @Test
        @DisplayName("Only blank lines returns null (EOF)")
        void onlyBlankLinesReturnsNull() throws IOException {
            HttpRequest request = HttpParser.parse(toStream("\r\n\r\n\r\n"));
            assertNull(request);
        }

        @Test
        @DisplayName("Header line exceeds maximum length")
        void headerLineTooLong() {
            StringBuilder longValue = new StringBuilder();
            for (int i = 0; i < 9000; i++) {
                longValue.append('A');
            }
            String raw = "GET / HTTP/1.1\r\n"
                    + "X-Long: " + longValue + "\r\n"
                    + "\r\n";

            BadRequestException ex = assertThrows(BadRequestException.class,
                    () -> HttpParser.parse(toStream(raw)));
            assertTrue(ex.getMessage().contains("maximum length"));
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // HttpRequest immutability
    // ─────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("HttpRequest Immutability")
    class ImmutabilityTests {

        @Test
        @DisplayName("Headers map is unmodifiable")
        void headersAreUnmodifiable() throws IOException {
            String raw = "GET / HTTP/1.1\r\n"
                    + "Host: localhost\r\n"
                    + "\r\n";

            HttpRequest request = HttpParser.parse(toStream(raw));
            assertNotNull(request);

            assertThrows(UnsupportedOperationException.class,
                    () -> request.getHeaders().put("injected", "value"));
        }

        @Test
        @DisplayName("Body array is a defensive copy")
        void bodyIsDefensiveCopy() throws IOException {
            String body = "original";
            String raw = "POST / HTTP/1.1\r\n"
                    + "Host: localhost\r\n"
                    + "Content-Length: " + body.length() + "\r\n"
                    + "\r\n"
                    + body;

            HttpRequest request = HttpParser.parse(toStream(raw));
            assertNotNull(request);

            byte[] copy1 = request.getBody();
            copy1[0] = 'X'; // mutate the copy

            // Original should be unchanged
            assertEquals("original", request.getBodyAsString());
        }
    }
}

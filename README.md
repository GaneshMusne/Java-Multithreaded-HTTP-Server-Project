# Multithreaded HTTP Server (Java)

A fully functional HTTP/1.1 server built from scratch using **raw Java TCP sockets** — no frameworks (no Spring, no Netty, no Undertow). Demonstrates low-level networking, concurrent programming, and HTTP protocol implementation.

## Features

- **Raw TCP Sockets** — `ServerSocket` & `Socket` (no HTTP libraries)
- **RFC 7230 Compliant Parser** — Request line, headers, and body parsing with validation
- **Thread Pool Concurrency** — `ExecutorService` with configurable worker threads
- **Persistent Connections** — HTTP/1.1 Keep-Alive with idle read timeouts
- **Routing Engine** — Method + path dispatch with 404/405 handling
- **Static File Serving** — MIME type resolution with directory traversal protection
- **Error Handling** — Proper 400, 403, 404, 405, 500 responses
- **Access Logging** — Structured logs with timestamp, thread, method, path, status, latency
- **Unit Tests** — 29 JUnit 5 tests covering valid, malformed, and edge-case requests
- **Benchmarked** — 60K+ req/sec on Apple M4 Pro

## Architecture

```
                  ┌────────────────────────────┐
                  │         Main.java          │
                  │   (parse --port --threads)  │
                  └─────────────┬──────────────┘
                                │
                  ┌─────────────▼──────────────┐
                  │       HttpServer.java       │
                  │                             │
                  │  ServerSocket.accept() loop │
                  │  ┌───────────────────────┐  │
                  │  │  ExecutorService       │  │
                  │  │  (fixed thread pool)   │  │
                  │  └───────────────────────┘  │
                  └─────────────┬──────────────┘
                                │ submit(ConnectionHandler)
              ┌─────────────────┼─────────────────┐
              ▼                 ▼                  ▼
    ┌──────────────┐  ┌──────────────┐   ┌──────────────┐
    │ http-worker-1│  │ http-worker-2│   │ http-worker-N│
    │              │  │              │   │              │
    │ ConnectionHandler (per-connection keep-alive loop)│
    └──────┬───────┘  └──────┬───────┘   └──────┬───────┘
           │                 │                   │
           ▼                 ▼                   ▼
    ┌─────────────────────────────────────────────────┐
    │                  HttpParser                      │
    │  1. Read request line  (GET /path HTTP/1.1)      │
    │  2. Read headers       (Key: Value until \r\n)   │
    │  3. Read body          (Content-Length bytes)     │
    │                                                  │
    │  Throws BadRequestException on protocol errors   │
    └──────────────────────┬───────────────────────────┘
                           │ HttpRequest
                           ▼
    ┌─────────────────────────────────────────────────┐
    │                    Router                        │
    │                                                  │
    │  1. Exact route match  → RequestHandler          │
    │  2. Path exists, wrong method → 405 + Allow      │
    │  3. StaticFileHandler fallback → serve file      │
    │  4. Nothing matches → 404                        │
    └──────────────────────┬───────────────────────────┘
                           │ HttpResponse
                           ▼
    ┌─────────────────────────────────────────────────┐
    │              HttpResponse.writeTo()              │
    │  HTTP/1.1 200 OK\r\n                             │
    │  Content-Type: text/html\r\n                     │
    │  Content-Length: 1777\r\n                         │
    │  Connection: keep-alive\r\n                      │
    │  \r\n                                            │
    │  <body bytes>                                    │
    └──────────────────────┬───────────────────────────┘
                           │
                           ▼
                    AccessLogger.log()
          [timestamp] [thread] METHOD /path STATUS - latency
```

## Project Structure

```
src/main/java/com/httpserver/
├── Main.java                        # Entry point (--port, --threads)
├── server/
│   ├── HttpServer.java              # ServerSocket + thread pool + shutdown
│   └── ConnectionHandler.java       # Per-connection keep-alive loop
├── http/
│   ├── HttpParser.java              # RFC 7230 request parser
│   ├── HttpRequest.java             # Immutable parsed request
│   ├── HttpResponse.java            # Response builder (status/headers/body)
│   ├── HttpMethod.java              # GET, POST, PUT, DELETE, HEAD, OPTIONS
│   ├── HttpStatus.java              # 200, 400, 403, 404, 405, 408, 500
│   └── BadRequestException.java     # Protocol violation → 400 response
├── routing/
│   ├── Router.java                  # Method+path → handler dispatch
│   └── Route.java                   # Route record (method, path, handler)
├── handler/
│   ├── RequestHandler.java          # @FunctionalInterface
│   ├── StaticFileHandler.java       # Serve files + MIME + traversal guard
│   └── EchoHandler.java             # Echo request body/metadata
└── logging/
    └── AccessLogger.java            # Structured access log

src/test/java/com/httpserver/http/
└── HttpParserTest.java              # 29 unit tests

public/                              # Static files directory
├── index.html
├── style.css
└── hello.json

benchmark/
├── benchmark.sh                     # Automated benchmark script (wrk)
└── results.md                       # Benchmark results
```

## Quick Start

### Prerequisites

- Java 17+ (`java -version`)
- Maven 3.8+ (`mvn -version`)

### Build & Run

```bash
# Clone
git clone https://github.com/YOUR_USERNAME/multithreaded-http-server-java.git
cd multithreaded-http-server-java

# Build
mvn clean compile

# Run (default: port 8080, 10 threads)
mvn exec:java

# Run with custom settings
mvn exec:java -Dexec.args="--port 9090 --threads 20"
```

### Test

```bash
# Run unit tests (29 tests)
mvn test

# Manual testing
curl -v http://localhost:8080/                    # Serve index.html
curl -v http://localhost:8080/hello.json           # JSON with correct MIME
curl -v http://localhost:8080/style.css             # CSS with correct MIME
curl -v -X POST http://localhost:8080/echo -d '{"key":"value"}'  # Echo body
curl -v http://localhost:8080/nonexistent           # 404 Not Found
curl -v -X DELETE http://localhost:8080/echo        # 405 Method Not Allowed
```

## Running Benchmarks

### Prerequisites

```bash
brew install wrk    # macOS
# or: apt install wrk  # Linux
```

### Run

```bash
chmod +x benchmark/benchmark.sh
./benchmark/benchmark.sh
```

The script automatically:
1. Builds the project
2. Starts the server at each thread pool size (4, 10, 50)
3. Runs wrk with 100 concurrent connections for 15 seconds
4. Collects throughput, avg latency, and p99 latency
5. Saves results to `benchmark/results.md`

### Benchmark Results

Tested on Apple M4 Pro (14 cores, 24GB RAM), Java 26, macOS.

| Thread Pool Size | Requests/sec | Avg Latency | p99 Latency | Total Requests |
|:----------------:|:------------:|:-----------:|:-----------:|:--------------:|
| **4** | 26,781 | 3.72ms | 19.44ms | 401,885 |
| **10** | 55,769 | 2.16ms | 13.84ms | 837,839 |
| **50** | 61,871 | 2.00ms | 12.83ms | 928,502 |

**Key takeaways:**
- **4 → 10 threads**: Throughput **doubled** (+108%). The 4-thread pool was the bottleneck with 100 concurrent connections.
- **10 → 50 threads**: Only +11% improvement. Diminishing returns — the CPU and I/O are already saturated.
- **Sweet spot**: ~10 threads for this workload. More threads add context-switching overhead without meaningful gains.

## Tech Stack

| Component | Technology |
|-----------|-----------|
| Language | Java 17+ |
| Networking | `java.net.ServerSocket` / `Socket` (raw TCP) |
| Concurrency | `java.util.concurrent.ExecutorService` (fixed thread pool) |
| Build | Maven |
| Testing | JUnit 5 |
| Benchmarking | wrk |


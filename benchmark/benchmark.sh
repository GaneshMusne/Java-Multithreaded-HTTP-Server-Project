#!/bin/bash
#
# benchmark.sh — HTTP server throughput & latency benchmark
#
# Uses wrk to measure performance across different thread pool sizes.
# Starts the server, runs wrk, captures results, and produces a markdown table.
#
# Usage:
#   chmod +x benchmark/benchmark.sh
#   ./benchmark/benchmark.sh
#
# Prerequisites:
#   - wrk installed (brew install wrk)
#   - Maven installed and project compiled
#

set -e

# ── Configuration ────────────────────────────────────────────────────
SERVER_PORT=8080
WRK_DURATION=15s          # Duration per benchmark run
WRK_CONNECTIONS=100       # Number of concurrent TCP connections
WRK_THREADS=4             # Number of wrk worker threads
POOL_SIZES=(4 10 50)      # Server thread pool sizes to test
WARMUP_SECONDS=3          # Warmup time before measurement
RESULTS_FILE="benchmark/results.md"
PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"

cd "$PROJECT_DIR"

# ── Helper Functions ──────────────────────────────────────────────────

start_server() {
    local threads=$1
    echo "  Starting server with $threads threads..."
    mvn exec:java -Dexec.args="--port $SERVER_PORT --threads $threads" -q &
    SERVER_PID=$!
    sleep $WARMUP_SECONDS

    # Verify server is up
    if ! curl -s http://localhost:$SERVER_PORT/ > /dev/null 2>&1; then
        echo "  ERROR: Server failed to start!"
        exit 1
    fi
    echo "  Server running (PID: $SERVER_PID)"
}

stop_server() {
    if [ -n "$SERVER_PID" ] && kill -0 "$SERVER_PID" 2>/dev/null; then
        echo "  Stopping server (PID: $SERVER_PID)..."
        kill "$SERVER_PID" 2>/dev/null || true
        wait "$SERVER_PID" 2>/dev/null || true
        sleep 1
    fi
}

# Ensure server is stopped on exit
trap stop_server EXIT

# ── Pre-flight Checks ────────────────────────────────────────────────

echo "╔══════════════════════════════════════════════════════════════╗"
echo "║       HTTP Server Benchmark (wrk)                          ║"
echo "╚══════════════════════════════════════════════════════════════╝"
echo ""

# Check for wrk
if ! command -v wrk &> /dev/null; then
    echo "ERROR: wrk is not installed. Install with: brew install wrk"
    exit 1
fi

# Check port is free
if lsof -i :$SERVER_PORT -t > /dev/null 2>&1; then
    echo "ERROR: Port $SERVER_PORT is already in use."
    echo "Kill the process: kill -9 \$(lsof -i :$SERVER_PORT -t)"
    exit 1
fi

# Build the project
echo "Building project..."
mvn clean compile -q
echo ""

# ── Run Benchmarks ────────────────────────────────────────────────────

echo "Benchmark config:"
echo "  Duration:    $WRK_DURATION per run"
echo "  Connections: $WRK_CONNECTIONS concurrent"
echo "  wrk threads: $WRK_THREADS"
echo "  Pool sizes:  ${POOL_SIZES[*]}"
echo ""

# Initialize results file
mkdir -p benchmark
cat > "$RESULTS_FILE" << 'EOF'
# Benchmark Results

## Test Configuration

| Parameter | Value |
|-----------|-------|
EOF

echo "| Duration | $WRK_DURATION |" >> "$RESULTS_FILE"
echo "| Concurrent connections | $WRK_CONNECTIONS |" >> "$RESULTS_FILE"
echo "| wrk threads | $WRK_THREADS |" >> "$RESULTS_FILE"
echo "| Target endpoint | GET / (serves index.html) |" >> "$RESULTS_FILE"

cat >> "$RESULTS_FILE" << 'EOF'

## Results

| Thread Pool Size | Requests/sec | Avg Latency | p99 Latency | Total Requests | Transfer/sec |
|:----------------:|:------------:|:-----------:|:-----------:|:--------------:|:------------:|
EOF

for pool_size in "${POOL_SIZES[@]}"; do
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo "Testing thread pool size: $pool_size"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

    start_server "$pool_size"

    echo "  Running wrk ($WRK_DURATION, $WRK_CONNECTIONS connections)..."
    WRK_OUTPUT=$(wrk -t$WRK_THREADS -c$WRK_CONNECTIONS -d$WRK_DURATION --latency http://localhost:$SERVER_PORT/ 2>&1)

    echo "$WRK_OUTPUT"
    echo ""

    # Parse wrk output
    REQ_SEC=$(echo "$WRK_OUTPUT" | grep "Requests/sec:" | awk '{print $2}')
    AVG_LAT=$(echo "$WRK_OUTPUT" | grep -A1 "Latency" | tail -1 | awk '{print $2}')
    P99_LAT=$(echo "$WRK_OUTPUT" | grep "99%" | awk '{print $2}')
    TOTAL_REQ=$(echo "$WRK_OUTPUT" | grep "requests in" | awk '{print $1}')
    TRANSFER=$(echo "$WRK_OUTPUT" | grep "Transfer/sec:" | awk '{print $2}')

    # Append to results table
    echo "| $pool_size | $REQ_SEC | $AVG_LAT | $P99_LAT | $TOTAL_REQ | $TRANSFER |" >> "$RESULTS_FILE"

    stop_server
    sleep 1
done

# Add system info
cat >> "$RESULTS_FILE" << EOF

## System Info

| Component | Value |
|-----------|-------|
| OS | $(uname -s) $(uname -r) $(uname -m) |
| Java | $(java -version 2>&1 | head -1) |
| CPU | $(sysctl -n machdep.cpu.brand_string 2>/dev/null || echo "Unknown") |
| Cores | $(sysctl -n hw.ncpu 2>/dev/null || nproc 2>/dev/null || echo "Unknown") |
| RAM | $(sysctl -n hw.memsize 2>/dev/null | awk '{printf "%.0f GB", $1/1024/1024/1024}' 2>/dev/null || echo "Unknown") |
EOF

echo ""
echo "╔══════════════════════════════════════════════════════════════╗"
echo "║  Benchmark complete! Results saved to: $RESULTS_FILE       ║"
echo "╚══════════════════════════════════════════════════════════════╝"
echo ""
cat "$RESULTS_FILE"

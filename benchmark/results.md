# Benchmark Results

## Test Configuration

| Parameter | Value |
|-----------|-------|
| Duration | 15s per run |
| Concurrent connections | 100 |
| wrk threads | 4 |
| Target endpoint | `GET /` (serves index.html, 1.7KB) |

## Results

| Thread Pool Size | Requests/sec | Avg Latency | p99 Latency | Total Requests | Transfer/sec |
|:----------------:|:------------:|:-----------:|:-----------:|:--------------:|:------------:|
| 4 | 26,781 | 3.72ms | 19.44ms | 401,885 | 5.02 MB/s |
| 10 | 55,769 | 2.16ms | 13.84ms | 837,839 | 10.45 MB/s |
| 50 | 61,871 | 2.00ms | 12.83ms | 928,502 | 11.60 MB/s |

## Analysis

- **4 → 10 threads**: Throughput **doubled** (+108%) and latency dropped 42%. The 4-thread pool was the bottleneck — with 100 concurrent connections, threads were always busy.
- **10 → 50 threads**: Marginal improvement (+11%). Diminishing returns because the CPU (14 cores) and the workload (mostly I/O) are already saturated at 10 threads.
- **p99 latency**: Improved consistently as the pool grew — fewer requests queued behind busy threads.

## System Info

| Component | Value |
|-----------|-------|
| OS | macOS Darwin 26.6.2 arm64 |
| Java | Java 26.0.1 (HotSpot) |
| CPU | Apple M4 Pro |
| Cores | 14 |
| RAM | 24 GB |
| 50 | 11719.58 | 795.00us | 46.31ms | 176943 | 21.03MB |

## System Info

| Component | Value |
|-----------|-------|
| OS | Darwin 25.6.0 arm64 |
| Java | java version "26.0.1" 2026-04-21 |
| CPU | Apple A18 Pro |
| Cores | 6 |
| RAM | 8 GB |

# Load Testing - RapidLink

This directory contains load testing scripts for the RapidLink URL shortening service using **k6**.
The goal is to validate system performance, correctness, and scalability under realistic traffic conditions.

---

## What This Tests

The load tests focus on the **redirect endpoint**:

* `GET /{shortCode}` → should return **302 (redirect)** for valid URLs
* Invalid shortcodes → should return **404 (not found)**

Traffic simulation:

* **80% valid requests** (real users)
* **20% invalid requests** (bots / broken links)

---

## 📁 Folder Structure

```
load-test/
│
├── config/
│   └── base-config.js        # Load stages and thresholds
│
├── data/
│   └── shortcodes.json      # Test data (valid + invalid codes)
│
├── scripts/
│   └── redirect-test.js     # Main k6 test script
│
├── utils/
│   └── helpers.js           # Utility functions
│
└── README.md                # This file
```

---

## ️Prerequisites

Make sure the following are running:

* RapidLink backend (Spring Boot)
* Redis (for caching)
* PostgreSQL (for persistence)
* k6 installed

### Install k6

* Windows (chocolatey):

```
choco install k6
```

* Mac:

```
brew install k6
```

---

##  How to Run Load Test

```bash
k6 run -e BASE_URL=http://localhost:8080 load-test/scripts/redirect-test.js
```

---

## Load Test Configuration

Defined in `config/base-config.js`

### Stages (Traffic Pattern)

* Warm-up → gradually increase users
* Normal load → simulate real usage
* Peak load → high traffic
* Stress → push system limits
* Cool down → reduce load

Example:

```js
stages: [
  { duration: '30s', target: 50 },
  { duration: '1m', target: 200 },
  { duration: '2m', target: 500 },
  { duration: '1m', target: 800 },
  { duration: '30s', target: 0 },
]
```

---

## Thresholds (Pass/Fail Rules)

```js
thresholds: {
  http_req_duration: ['p(95)<500'],
  http_req_failed: ['rate<0.25'],
  redirect_success: ['rate>0.95'],
  redirect_failure: ['rate<0.05'],
}
```

### Meaning:

* **p95 < 500ms** → 95% requests should be fast
* **http_req_failed < 25%** → allows expected 404 traffic
* **redirect_success > 95%** → correct behavior
* **redirect_failure < 5%** → minimal incorrect responses

---

## Test Data

Located in `data/shortcodes.json`

```json
{
  "valid": ["gO", "gp", "..."],
  "invalid": ["zz1", "abc999", "..."]
}
```

---

## How the Test Works

Each virtual user (VU):

1. Picks random shortcode
2. 80% → valid
3. 20% → invalid
4. Sends GET request
5. Validates response
6. Sleeps for random time

---

## Key Metrics to Observe

### 1. Latency

* p50 → normal response time
* p95 → real user experience
* p99 → worst-case

### 2. Throughput

* Requests per second (RPS)

### 3. Error Rate

* Ignore default k6 failure for 404
* Use custom metrics instead

### 4. Cache Performance

From Prometheus:

* `cache_hit_total`
* `cache_miss_total`
* `negative_cache_hit_total`

---

## Expected Behavior

| Scenario          | Expected Response |
| ----------------- | ----------------- |
| Valid shortcode   | 302               |
| Invalid shortcode | 404               |

---

##  Notes

* k6 treats **404 as failure** by default
* In this system, 404 is expected for invalid requests
* Custom metrics (`redirect_success`) should be used for correctness

---

## What to Analyze After Test

After running load test, analyze:

* Is latency stable under load?
* Is cache hit rate high (>95%)?
* Is DB load minimal?
* Are there any latency spikes?

---

## Goal

Ensure that RapidLink:

* Handles high traffic efficiently
* Responds within low latency
* Uses Redis caching effectively
* Scales without overloading database

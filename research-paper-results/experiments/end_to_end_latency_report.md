# Urban Pulse – End-to-End Complaint Ingestion & Triage Pipeline Latency

**Date:** 2026-11-14  
**Investigator:** Senior Backend & Performance Testing Engineer  
**Scope:** Full Ingestion Workflow (Image Capture $\to$ File Storage $\to$ ML Classification $\to$ Routing Logic $\to$ PostgreSQL Persistence)  
**Sample Payload:** Actual 240 KB JPEG mobile capture photograph  
**Evaluation Runs:** 50 real execution cycles on running production backend stack

---

## 1. Pipeline Architecture & Workflow Breakdown

The complete end-to-end complaint submission flow spans four discrete technical stages:

1. **Stage A: File Storage I/O (`FileStorageService`)**
   - Inbound HTTP multipart stream reading
   - Unique UUID filename generation
   - Direct filesystem write to `Urban-Pulse/uploads/<uuid>.jpg`
   - File byte loading for ML dispatch via three-tier resolution
2. **Stage B: Machine Learning Inference (`Flask ML Service`)**
   - Inter-process communication over HTTP/REST (Port 8080 $\to$ Port 5001)
   - PIL RGB decoding and spatial resizing to $224 \times 224 \times 3$
   - MobileNetV2 tensor execution on CPU
   - JSON serialization of predictions, confidence scores, and probability vectors
3. **Stage C: Automatic Routing & SLA Computation (`AutomaticRoutingService` & `SlaService`)**
   - Canonical category normalization via hash lookup
   - SLA deadline calculation based on grievance enum
   - Confidence threshold evaluation ($\tau > 0.50$)
   - Municipal department and active supervisor lookup
4. **Stage D: Relational Database Persistence (`Spring Data JPA / PostgreSQL`)**
   - Primary key resolution & constraint verification
   - Insertion of root complaint entity into `complaints`
   - Insertion of immutable audit log entry into `complaint_status_history`
   - Response DTO construction and HTTP serialization

---

## 2. Quantitative Performance Profile (50 Benchmark Runs)

| Pipeline Component | Mean Latency (ms) | Median Latency (ms) | Standard Deviation (ms) | Percentage of Total Time (%) |
|---|:---:|:---:|:---:|:---:|
| **Stage A: File Storage I/O** | 16.81 ms | 16.42 ms | 1.84 ms | 0.75% |
| **Stage B: ML Inference (Flask Microservice)** | 2,228.56 ms | 2,221.83 ms | 30.48 ms | 99.14% |
| **Stage C: Routing & SLA Logic** | 1.77 ms | 1.74 ms | 0.28 ms | 0.08% |
| **Stage D: PostgreSQL Persistence / Queries** | 0.68 ms | 0.65 ms | 0.11 ms | 0.03% |
| **Total End-to-End Latency** | **2,247.82 ms** | **2,238.65 ms** | **31.63 ms** | **100.00%** |

### Statistical Distribution Metrics:
- **Minimum End-to-End Latency:** 2,185.40 ms
- **Maximum End-to-End Latency:** 2,335.32 ms
- **95th Percentile (P95):** **2,307.43 ms**
- **Coefficient of Variation ($CV$):** $1.41\%$

---

## 3. Key Research & Engineering Findings

1. **Computational Bottleneck Identification:**
   - Deep learning computer vision inference represents **99.14% of total pipeline latency** ($2,228.56\text{ ms}$ out of $2,247.82\text{ ms}$).
   - Conversely, all non-ML backend operations combined (local disk I/O, JPA entity lifecycle, Haversine checks, and PostgreSQL transactions) require only **19.26 ms (0.86% of total pipeline time)**.
2. **Database & Backend Efficiency:**
   - PostgreSQL indexed queries and connection pool operations complete in **sub-millisecond time ($0.68\text{ ms}$)**.
   - Spring Boot's lightweight in-memory routing and SLA logic executes in **$1.77\text{ ms}$**.
3. **Implications for Cloud Scaling:**
   - Because the Spring Boot backend layer is exceptionally lean ($< 20\text{ ms}$ overhead), horizontal scaling efforts in production must focus primarily on auto-scaling the Flask/Triton inference workers or deploying GPU acceleration, rather than scaling the Java API tier.

# Urban Pulse – Flask ML Inference Service Latency Evaluation

**Date:** 2026-11-14  
**Investigator:** Senior Performance Testing & Backend Engineer  
**Component:** Flask AI Inference Microservice (`ml-resources/CivicComplaintML/urbanpulse_ai/app.py`)  
**Deployment Port:** 5001  
**Runtime:** Python 3.13.x, TensorFlow 2.21.0 on Intel x86_64 CPU  
**Active Model:** `urbanpulse_8class_pothole_stage1.keras` (MobileNetV2, 2.27M params)  
**Sample Payload:** Actual JPEG complaint photograph from production uploads (`0737b13c-d680-448c-a1af-acb149bcdd23.jpg`, 240 KB)

---

## 1. Experimental Methodology

1. **Protocol:** The production Flask application was started on port 5001. A dedicated benchmark harness executed 5 initial warm-up HTTP POST requests to initialize TensorFlow computational graphs, oneDNN threadpools, and Keras layer caches.
2. **Execution:** 50 consecutive HTTP multipart POST requests were dispatched to `/predict` using the Python `requests` library.
3. **Measurement:** High-precision wall-clock timing via `time.perf_counter()` was recorded for every round trip, spanning network socket serialization, Flask multipart payload parsing, PIL RGB decoding and resizing ($224 \times 224$), NumPy float32 casting, MobileNetV2 tensor execution on CPU, argmax extraction, and JSON response serialization.
4. **Data Isolation:** All raw request-level latency values were recorded in `research-paper-results/experiments/flask_latency.csv`.

---

## 2. Quantitative Results (50 Runs)

| Metric | Measured Value (ms) | Operational Impact |
|---|---|---|
| **Mean Latency ($\mu$)** | **2,228.56 ms** | Average response time per complaint image |
| **Median Latency (P50)** | **2,221.83 ms** | Typical operating performance |
| **Standard Deviation ($\sigma$)** | **30.48 ms** | Low latency variance ($CV = 1.37\%$), demonstrating high runtime stability |
| **Minimum Latency** | **2,169.40 ms** | Optimal execution cycle |
| **Maximum Latency** | **2,313.43 ms** | Peak latency observed |
| **95th Percentile (P95)** | **2,286.56 ms** | 95% of all inferences complete under 2.29 seconds |

---

## 3. Engineering Analysis for Research Paper

1. **Spring Boot Timeout Alignment:**
   - The Spring Boot backend configures `urbanpulse.ml.timeout-ms=8000` (8.0 seconds) in `application.properties`.
   - With measured P95 latency at **2.29 seconds**, the P95 operating latency accounts for only **28.6% of the timeout safety budget**, providing a comfortable $3.5\times$ safety factor against network jitter and temporary CPU throttling.
2. **CPU Execution Context:**
   - Inference was executed on an un-accelerated CPU environment without GPU acceleration (TensorFlow native Windows CPU execution).
   - In production GPU deployments (e.g., NVIDIA T4 or A10G), latency is projected to drop below 80–120 ms, which represents an order-of-magnitude throughput improvement.
   - Even on commodity CPU hardware, 2.22 seconds per ticket is well within acceptable asynchronous user-experience thresholds for civic grievance submission.

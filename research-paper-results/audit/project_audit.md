# Urban Pulse – System & Repository Audit Report

**Date of Audit:** 2026-11-14  
**Auditor:** Senior ML, Backend & Testing Research Engineer  
**Project:** Urban Pulse – AI-Powered Smart Civic Intelligence Platform  
**Root Directory:** `C:\Users\HP\Urban Pulse`

---

## 1. Executive Summary

Urban Pulse is a full-stack, distributed civic issue reporting and automated governance platform. The system leverages edge-captured smartphone imagery, geolocation coordinates, and a two-stage cascaded deep learning computer vision pipeline to automate citizen complaint triage, duplicate prevention, and municipal departmental dispatch.

This audit provides a factual, ground-truth inventory of the repository structure, components, model weights, codebases, and configurations based on comprehensive filesystem inspection and code analysis.

---

## 2. Repository Component Inventory

| Component | Physical Location | Primary Language / Framework | Operational Port / Target | Status |
|---|---|---|---|---|
| **ML Research & Training Notebooks** | `ml-resources/Civic_Issue_Image_Classification.ipynb` | Python (Google Colab / Jupyter) | Offline Training | Validated (321 execution cells) |
| **Trained ML Models & Weights** | `ml-resources/CivicComplaintML/models/` | TensorFlow 2 / Keras (`.keras`) | Local Storage | 9 Keras models identified |
| **ML Inference Microservice** | `ml-resources/CivicComplaintML/urbanpulse_ai/` | Python 3.13, Flask 3.0, TF 2.21 | Port 5001 (`/predict`, `/health`) | Verified operational |
| **Backend Core Server** | `Urban-Pulse/` | Java 17, Spring Boot 3.3.4, Hibernate | Port 8080 (`/api/**`) | Verified operational |
| **Relational Database** | PostgreSQL 18 Windows Service | SQL / PostgreSQL JDBC | Port 5432 (`urbanpulse`) | Active, connected |
| **Citizen Mobile Application** | `Urban-Pulse-FrontEnd/` | React Native 0.74, TypeScript | Metro :8081 / Android Native | Active codebase |
| **Staff Web Portal** | `Urban-Pulse-FrontEnd/staff-web/` | React 18, Vite 5, Tailwind CSS | Vite :5173 | Active codebase |
| **Course & Architecture Docs** | `UrbanPulse-Backend-90-Minute-Course/` | Markdown documentation | Educational Guides | Complete (7 modules) |

---

## 3. Deep Learning & Computer Vision Subsystem

### 3.1 Training Notebooks
- **Primary Notebook:** `ml-resources/Civic_Issue_Image_Classification.ipynb`
  - Total Cells: 321 cells with complete execution logs preserved.
  - Runtime Environment: Google Colab GPU (Tesla T4 / V100), TensorFlow 2.21.0.
  - Coverage:
    - Initial 9-class MobileNetV2 training & evaluation.
    - Class imbalance mitigation via class weighting.
    - Analysis of Graffiti failure mode and subsequent drop to 8 civic classes.
    - 8-class MobileNetV2 transfer learning and fine-tuning.
    - Extra pothole dataset integration (720 images) in stage-1 fine-tuning (`urbanpulse_8class_pothole_stage1.keras`).
    - Binary civic vs non-civic gatekeeper dataset curation (clean road candidate harvesting from Kaggle `street_surface_vis`).
    - Binary relevance filter training (`urbanpulse_binary_v2_best.keras`).

### 3.2 Saved Models in Repository (`ml-resources/CivicComplaintML/models/`)
1. **`urbanpulse_binary_v2_best.keras`** (9.18 MB)
   - Input: `(None, 224, 224, 3)`, Output: `(None, 1)` (sigmoid)
   - Total Parameters: 2,259,265 (Trainable: 2 variables in classification head)
   - Architecture: MobileNetV2 (frozen) + GlobalAveragePooling2D + Dropout(0.2) + Dense(1, sigmoid)
   - Role: Stage 1 Gatekeeper in cascaded inference pipeline.
2. **`urbanpulse_binary_relevance_v2.keras`** (9.18 MB): Full final epoch checkpoint of binary v2.
3. **`urbanpulse_binary_relevance.keras`** (9.18 MB): Binary v1 model trained on CIFAR-10 negative candidates.
4. **`urbanpulse_8class_pothole_stage1.keras`** (9.29 MB)
   - Input: `(None, 224, 224, 3)`, Output: `(None, 8)` (softmax)
   - Total Parameters: 2,268,232
   - Architecture: MobileNetV2 (with built-in input preprocessing layers) + GlobalAveragePooling2D + Dropout(0.2) + Dense(8, softmax)
   - Role: Stage 2 Multi-class Civic Issue Classifier.
5. **`urbanpulse_8class_mobilenetv2.keras`** (9.28 MB): Base 8-class model prior to extra pothole fine-tuning.
6. **`best_civic_classifier.keras`** (9.29 MB): Baseline 9-class classifier (includes Graffiti).
7. **`civic_classifier_finetuned.keras`** (9.29 MB): Fine-tuned 9-class classifier with balanced class weights.
8. **`civic_classifier_finetuned_final.keras`** (9.29 MB): Final training state of 9-class model.
9. **`civic_issue_classifier.keras`** (9.29 MB): Exported 9-class deployment checkpoint.

### 3.3 Class Label Definitions
- **8-Class Model (`urbanpulse_8class_class_names.json`):**
  1. `Damaged concrete structures`
  2. `DamagedElectricalPoles`
  3. `DamagedRoadSigns`
  4. `DeadAnimalsPollution`
  5. `FallenTrees`
  6. `Garbage`
  7. `IllegalParking`
  8. `Potholes and RoadCracks`
- **9-Class Model (`class_names.json`):**
  - Includes `Graffitti` as index 6 (subsequently removed due to municipal ambiguity and cross-class confusion with wall cracks).

---

## 4. Backend Architecture (`Urban-Pulse/`)

### 4.1 Framework & Core Technologies
- **Java Version:** OpenJDK 17.0.x
- **Spring Boot:** 3.3.4
- **ORM / Persistence:** Spring Data JPA with Hibernate 6.5
- **Security:** Spring Security 6.3 with stateless `FirebaseAuthenticationFilter`
- **Identity Provider:** Firebase Admin SDK 9.4.1 (RS256 JWT ID Tokens)
- **Database Driver:** `org.postgresql:postgresql:42.7.4`
- **Image Handling:** Spring `MultipartFile` + Local `FileStorageService` (`Urban-Pulse/uploads/`)
- **HTTP Client:** Spring `RestTemplate` for microservice IPC

### 4.2 Database Configuration & Schema
- **JDBC URL:** `jdbc:postgresql://localhost:5432/urbanpulse`
- **Username / Password:** `postgres` / `root123`
- **Hibernate DDL Auto:** `update` (idempotent schema migration)
- **Entity Classes:**
  - `com.urbanpulse.entity.User` (`users` table, PK: `mobile_number` canonical format `+91 XXXXXXXXXX`)
  - `com.urbanpulse.entity.Department` (`departments` table, 8 standard municipal departments)
  - `com.urbanpulse.entity.Complaint` (`complaints` table, 32 fields including GPS, ML metadata, SLA)
  - `com.urbanpulse.entity.ComplaintStatusHistory` (`complaint_status_history` table, audit trail)
  - `com.urbanpulse.entity.ComplaintParticipant` (`complaint_participants` table, duplicate issue joiners)

### 4.3 Key Algorithms Implemented
1. **Haversine Duplicate Detection (`ComplaintService.checkDuplicates`):**
   - Great-circle distance calculation:
     $$a = \sin^2(\Delta \text{lat}/2) + \cos(\text{lat}_1)\cos(\text{lat}_2)\sin^2(\Delta \text{lon}/2)$$
     $$c = 2 \cdot \text{atan2}(\sqrt{a}, \sqrt{1-a})$$
     $$d = 6,371,000 \cdot c \text{ meters}$$
   - Configured search radius: 30 meters + 1.0 meter GPS jitter tolerance (31.0 m effective).
   - Filters out non-matching issue types and resolved/rejected tickets.
2. **Confidence-Based Automatic Routing (`AutomaticRoutingService.routeComplaint`):**
   - Confidence threshold: strictly $> 0.50$ (50%).
   - Threshold met + Department found + Supervisor active $\rightarrow$ Status: `FORWARDED`, Assignment: `AUTO_ML`.
   - Threshold not met $\rightarrow$ Status: `SUBMITTED`, Assignment: `MANUAL_REVIEW_LOW_CONFIDENCE`.
   - Stage 1 non-civic rejection $\rightarrow$ Status: `REJECTED`, Assignment: `REJECTED_NON_CIVIC_IMAGE`.
3. **Fixed SLA Tracking (`SlaService`):**
   - Immutable deadline: `createdAt + slaHours` (6h, 12h, 24h, 48h based on issue type).
   - SLA Statuses: `ON_TIME`, `DUE_SOON` (remaining $\le 20\%$ of SLA window), `OVERDUE`, `RESOLVED`.
   - Resolution compliance flag: `resolvedWithinSla = !resolvedAt.isAfter(slaDeadline)`.

---

## 5. Frontend Interfaces

### 5.1 Citizen Mobile App (`Urban-Pulse-FrontEnd/`)
- Stack: React Native, React Navigation, Firebase Auth, Geolocation API, Axios.
- Citizen Flows: Phone OTP login, camera image capture, GPS coordinate retrieval, 30m duplicate check popup, "Join Issue" button, real-time ticket tracking with status timeline.

### 5.2 Staff Web Portal (`Urban-Pulse-FrontEnd/staff-web/`)
- Stack: React 18, Vite, React Router, Lucide Icons.
- Role-based routing:
  - **Admin View:** System overview, manual review queue, department management, staff account provisioning.
  - **Supervisor View:** Departmental complaint queue, worker dispatching, SLA monitoring.
  - **Worker View:** Assigned tasks, Google Maps deep links, photo proof upload, task completion.

---

## 6. Discrepancies & Findings Between Documentation and Code

1. **Class Count (8 vs 10):**
   - Certain historical documentation referenced 10 civic classes. The actual trained deployment model (`urbanpulse_8class_pothole_stage1.keras`) operates on exactly **8 classes**. Graffiti was excluded during experimental refinement, and water leaks / drainage issues are consolidated into specific municipal pipelines.
2. **Department Naming Alignment:**
   - `DepartmentInitializer` seeds `"Roads and Infrastructure Department"` for description "Potholes and Road Cracks".
   - `CategoryDepartmentMappingService` maps `"Potholes and Road Cracks"` to `"Road Safety Department"`. Both departments exist in the database; the routing service routes to `"Road Safety Department"`.
3. **Binary Model Inverted Sigmoid Representation:**
   - In `urbanpulse_binary_v2_best.keras`, the sigmoid output corresponds to `NOT_CIVIC_ISSUE` probability (clean road / irrelevant negative candidate = 1, civic grievance = 0). The inference service properly computes `civic_prob = 1.0 - not_civic_prob` and checks `not_civic_prob < 0.50` for civic acceptance.

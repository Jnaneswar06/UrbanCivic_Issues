# 07. Backend Technical Interview Questions & Answers

> **Urban Pulse: AI-Powered Smart Civic Intelligence Platform**  
> *25 High-Yield Technical Interview Questions with Answers directly from Urban Pulse Code*

---

### Q1: What architecture does the Urban Pulse backend follow, and what are its core layers?
**Answer:**  
Urban Pulse follows an **N-Tier (Layered) Architecture** with strict Separation of Concerns:
1. **Controller Layer:** Exposes REST endpoints, validates DTOs, and extracts the authenticated principal from Spring Security.
2. **Service Layer:** Implements business logic (Haversine formula, 50% ML confidence rule, SLA calculations, transactional boundaries).
3. **Repository Layer:** Interfaces extending Spring Data JPA's `JpaRepository` for database communication.
4. **Entity Layer:** JPA models (`Complaint`, `User`, `Department`, `ComplaintParticipant`) mapped to PostgreSQL tables.

---

### Q2: How does Dependency Injection (DI) work in Spring Boot, and where is it used in Urban Pulse?
**Answer:**  
Dependency Injection is an implementation of Inversion of Control (IoC) where the Spring IoC Container creates and injects dependencies rather than classes instantiating them manually using `new`.  
In Urban Pulse, we use **Constructor-based Injection** (the industry best practice):
```java
@Service
public class ComplaintService {
    private final ComplaintRepository complaintRepository;
    private final NotificationService notificationService;

    public ComplaintService(ComplaintRepository complaintRepository,
                            NotificationService notificationService) {
        this.complaintRepository = complaintRepository;
        this.notificationService = notificationService;
    }
}
```
*Benefits:* Dependencies are immutable (`final`), pure unit testing is straightforward (mocks can be passed into the constructor), and circular dependencies are detected at startup.

---

### Q3: Why did you use Firebase Phone Number OTP instead of traditional BCrypt password authentication?
**Answer:**  
In a civic grievance application, citizens will not remember passwords when reporting a road hazard. More importantly, passwords do not prove identity. By using **Firebase Phone Number Auth**, every complaint is tied to a verified Indian mobile number (+91). This prevents spam, bot creation, and fake complaints. Firebase handles the SMS OTP infrastructure and issues an asymmetric RS256 JWT, which our Spring Boot backend verifies cryptographically.

---

### Q4: How does Spring Boot verify the Firebase JWT token on every incoming request?
**Answer:**  
We implemented a custom filter called `FirebaseAuthenticationFilter` which extends `OncePerRequestFilter`:
1. It intercepts every HTTP request and extracts `Authorization: Bearer <TOKEN>`.
2. It passes the token to `firebaseAuthService.verifyIdToken(idToken)`, which uses the Firebase Admin SDK to verify the token's cryptographic signature against Google's public keys.
3. It extracts the `phone_number` claim, loads the user record from PostgreSQL, creates a `UsernamePasswordAuthenticationToken` with authorities (`ROLE_CITIZEN`, `ROLE_WORKER`, etc.), and populates `SecurityContextHolder.getContext().setAuthentication(authentication)`.

---

### Q5: What is `SecurityContextHolder` and how does it maintain user identity per request?
**Answer:**  
`SecurityContextHolder` is where Spring Security stores the details of the currently authenticated principal. Under the hood, it uses a **`ThreadLocal`** storage strategy. Because Tomcat assigns a dedicated worker thread to process each incoming HTTP request, `ThreadLocal` ensures that the authenticated user's details are isolated to that specific thread and cannot leak to other concurrent user requests. At the end of the request filter chain, the context is cleared.

---

### Q6: How is Role-Based Access Control (RBAC) enforced in the backend?
**Answer:**  
RBAC is enforced at two levels:
1. **URL-level in `SecurityConfig`:** Using `.requestMatchers("/api/admin/**").hasRole("ADMIN")`.
2. **Method-level using `@PreAuthorize`:**
   ```java
   @PostMapping
   @PreAuthorize("hasRole('CITIZEN')")
   public ResponseEntity<ComplaintResponse> createComplaint(...) { ... }
   ```
If a citizen attempts to call an endpoint restricted to `hasRole('ADMIN')`, Spring Security's `CustomAccessDeniedHandler` intercepts the rejection and returns `HTTP 403 Forbidden`.

---

### Q7: Why do we disable CSRF in `SecurityConfig.java`?
**Answer:**  
CSRF (Cross-Site Request Forgery) attacks rely on browsers automatically attaching session cookies to unauthorized cross-origin requests. Because Urban Pulse is a **stateless REST API** that uses Bearer tokens stored in mobile device memory (not cookies), browsers do not automatically send credentials. Therefore, CSRF protection is unnecessary and safely disabled (`csrf(AbstractHttpConfigurer::disable)`).

---

### Q8: What is the difference between an Entity and a DTO, and why shouldn't Entities be returned by Controllers?
**Answer:**  
- An **Entity** (`Complaint.java`) maps directly to the database schema and is managed by the Hibernate persistence context.
- A **DTO** (`ComplaintResponse.java`) is a plain Java object defining the exact data contract sent to or received from the client.
*Why we never return Entities directly:*
1. **Prevents Circular Reference Errors:** Bidirectional relationships (e.g. User $\leftrightarrow$ Department) cause infinite loops during JSON serialization.
2. **Security:** Entities may contain sensitive fields (passwords, review notes) that should not be exposed.
3. **Data Enrichment:** DTOs can include computed fields not stored in DB (e.g., `joinCount`, `hasJoined`, `remainingMinutes`).

---

### Q9: How does the backend communicate with the Machine Learning model?
**Answer:**  
The ML model is hosted as an independent **Python Flask microservice on port 5001**. Our Spring Boot service `MlInferenceService` uses `RestTemplate` configured with strict timeouts (8000ms). When a complaint is submitted, Spring Boot reads the uploaded image bytes, packages them as a `ByteArrayResource` in a `multipart/form-data` request, and POSTs it to `http://localhost:5001/predict`. The Flask service loads `urbanpulse_8class_pothole_stage1.keras` (MobileNetV2), runs inference, and returns a JSON payload containing the predicted category and confidence score.

---

### Q10: What is the 50% Confidence Rule in automated routing?
**Answer:**  
In `AutomaticRoutingService.java`:
- If ML **`confidence > 0.50` (High Confidence):** The issue is automatically mapped to the responsible municipal department (e.g. `Potholes` $\rightarrow$ `Road Safety Department`), assigned to that department's active supervisor, its status is changed to `FORWARDED`, and its SLA is set.
- If ML **`confidence <= 0.50` (Low Confidence):** The complaint status remains `SUBMITTED`, but is flagged with `requiresManualReview = true` and `assignmentMethod = "MANUAL_REVIEW_LOW_CONFIDENCE"`, placing it in the human Admin dashboard for manual triage.

---

### Q11: What happens if the Python ML microservice crashes or goes offline?
**Answer:**  
Urban Pulse implements **Graceful Degradation and Fault Isolation**. The call to `MlInferenceService` is wrapped in a `try-catch` block. If the ML container is down or times out, the backend catches the error, sets `assignmentMethod = "MANUAL_REVIEW_ML_FAILED"`, sets `requiresManualReview = true`, and routes the complaint to the Admin dashboard. **The citizen's complaint submission always succeeds with HTTP 201 Created.** AI failure never impacts core platform availability.

---

### Q12: Why did you use the Haversine formula instead of Euclidean distance for duplicate detection?
**Answer:**  
Because the Earth is a sphere, lines of longitude converge at the poles. A degree of longitude at the equator is $\approx 111\text{ km}$, but shrinks significantly as latitude changes. The **Haversine formula** accounts for the curvature of the spherical Earth using spherical trigonometry ($R = 6,371,000\text{ meters}$) to provide centimeter-accurate surface distances between two coordinate points. Euclidean distance ($\Delta x^2 + \Delta y^2$) is flat-plane geometry and produces huge inaccuracies for GPS coordinates.

---

### Q13: What is the duplicate search radius in Urban Pulse, and why is there a 1-meter GPS tolerance?
**Answer:**  
The default duplicate radius is **30.0 meters**. However, smartphone GPS hardware experiences atmospheric jitter (drifting by 1–2 meters). We add `GPS_TOLERANCE_METERS = 1.0`, creating an effective search window of **31.0 meters**. This ensures that two citizens standing next to the same pothole whose phone sensors report slightly jittered coordinates (e.g., 30.4 meters apart) are still successfully matched.

---

### Q14: How does Urban Pulse prevent a citizen from joining the same complaint multiple times?
**Answer:**  
We protect against duplicate participation at two layers:
1. **Service Layer Check:** `complaintParticipantRepository.findByComplaintIdAndCitizenMobileNumber()` checks if a record exists and returns `alreadyJoined = true`.
2. **Database Unique Constraint:** In `ComplaintParticipant.java`:
   ```java
   @Table(name = "complaint_participants",
          uniqueConstraints = @UniqueConstraint(name = "uk_complaint_citizen", columnNames = {"complaint_id", "citizen_id"}))
   ```
   Even under extreme concurrent tapping or network race conditions, PostgreSQL rejects duplicate inserts at the database engine level by throwing `DataIntegrityViolationException`.

---

### Q15: How are images stored in Urban Pulse, and why aren't they stored directly in PostgreSQL as BLOBs?
**Answer:**  
Images are saved to the **file system disk in the `uploads/` directory** using `FileStorageService.java`, and only the relative URL string (`/uploads/<uuid>.jpg`) is stored in the PostgreSQL `image_url` column.  
*Why not database BLOBs:* Storing megabytes of binary image data inside relational database rows inflates table size, degrades cache performance, slows down backups, and consumes excessive database memory. Storing files on disk or cloud object storage (S3) is the industry standard.

---

### Q16: How does SLA (Service Level Agreement) tracking work?
**Answer:**  
In `SlaService.java`, each civic issue type has a defined resolution window (e.g., Potholes = 24h, Water Leaks = 6h, Garbage = 12h). When a complaint is created, `slaDeadline` is calculated as `createdAt.plusHours(slaHours)`.  
When a worker marks the complaint as `RESOLVED`, the backend sets `resolvedAt = LocalDateTime.now()` and records:
```java
complaint.setResolvedWithinSla(!now.isAfter(complaint.getSlaDeadline()));
```
This enables municipal performance auditing to measure which departments meet their civic SLAs.

---

### Q17: Do SLA deadlines reset when an Admin forwards a complaint to another department?
**Answer:**  
**No! SLA deadlines never reset.** In `ComplaintService.forwardComplaint()`, only the `department` and `status` fields are updated. The original `slaHours` and `slaDeadline` timestamps remain completely untouched. If deadlines reset on forwarding, municipal departments could game the system and avoid SLA penalties indefinitely by simply forwarding tickets between offices.

---

### Q18: What is the purpose of `ComplaintStatusHistory`?
**Answer:**  
It acts as an **immutable audit trail**. Whenever a complaint changes status (`SUBMITTED` $\rightarrow$ `FORWARDED` $\rightarrow$ `ASSIGNED` $\rightarrow$ `IN_PROGRESS` $\rightarrow$ `RESOLVED`), a new record is saved in `complaint_status_history` with:
- `old_status` and `new_status`
- `changed_by_mobile` (the exact worker, supervisor, or admin)
- `changed_at` timestamp
- `remarks`  
This guarantees non-repudiation and complete governance accountability.

---

### Q19: How are push notifications delivered without slowing down the HTTP response?
**Answer:**  
In `NotificationService.java`, notification methods are annotated with **`@Async`**:
```java
@Async
public void sendComplaintStatusNotification(String fcmToken, String complaintNumber, ComplaintStatus newStatus, String dept) { ... }
```
When called, Spring executes the FCM network dispatch on a separate background thread from a managed thread pool. The main HTTP thread returns immediately to the client with `200 OK` without waiting for Google Firebase servers to respond. Furthermore, any FCM delivery error is caught and logged, guaranteeing that notification failures never roll back complaint transactions.

---

### Q20: What is `@Transactional` and what happens if a database error occurs halfway through a method?
**Answer:**  
`@Transactional` marks the boundaries of an atomic database transaction. If any runtime exception occurs before the method completes, Spring automatically issues a **`ROLLBACK`** command to PostgreSQL, undoing all database modifications made during that method call. This ensures the ACID property of **Atomicity**—either all changes succeed, or none do.

---

### Q21: What is the difference between `FetchType.EAGER` and `FetchType.LAZY` in JPA?
**Answer:**  
- **`FetchType.EAGER`:** The related entity is loaded immediately along with the parent entity using an SQL `JOIN`. Used in `Complaint.java` for `citizen` and `department` because complaint responses almost always require citizen and department names.
- **`FetchType.LAZY`:** The related entity is not loaded until its getter method is explicitly called in Java. Used in `ComplaintParticipant.java` to prevent loading full complaint objects when merely counting participant rows.

---

### Q22: How does Global Exception Handling work in Urban Pulse?
**Answer:**  
We use `@RestControllerAdvice` in `GlobalExceptionHandler.java`. It catches exceptions thrown anywhere in the controller or service layers and formats them into a standardized `ErrorResponse` JSON with appropriate HTTP status codes:
- `IllegalArgumentException` $\rightarrow$ `400 BAD REQUEST`
- `FirebaseAuthException` $\rightarrow$ `401 UNAUTHORIZED`
- `AccessDeniedException` $\rightarrow$ `403 FORBIDDEN`
- `IllegalStateException` $\rightarrow$ `409 CONFLICT`
- Unhandled `Exception` $\rightarrow$ `500 INTERNAL SERVER ERROR`

---

### Q23: Why is `mobile_number` used as the Primary Key in the `User` entity instead of a synthetic `Long id`?
**Answer:**  
In India, a citizen's 10-digit mobile number (+91) is a **Natural Key**—it is inherently unique, non-null, and immutable across the lifespan of a user's account. Using `mobile_number` as `@Id`:
1. Eliminates an unnecessary secondary unique index on mobile number.
2. Allows direct lookups via `userRepository.findById(mobileNumber)` without requiring extra query joins.
3. Automatically rejects duplicate citizen registration attempts at the database engine level.

---

### Q24: What database connection pool does Urban Pulse use, and why is it important?
**Answer:**  
Urban Pulse uses **HikariCP**, which is configured automatically by Spring Boot.  
*Why it is important:* Opening a new physical database connection requires an expensive TCP three-way handshake, TLS negotiation, and authentication check ($\approx 50\text{--}100\text{ms}$). HikariCP maintains a warm pool of pre-established database connections in memory. When a repository method needs to query the database, it borrows a connection in $< 1\text{ms}$ and returns it immediately after query completion.

---

### Q25: How would you scale the Urban Pulse backend to handle 1,000,000 active citizens?
**Answer:**  
1. **Stateless Clustering:** Since authentication is stateless (Firebase Bearer tokens) and no HTTP sessions exist, we can run multiple instances of the Spring Boot JAR behind an **NGINX / AWS Application Load Balancer**.
2. **Spatial Indexing with PostGIS:** Replace the in-memory Haversine loop with **PostGIS** spatial indexing (`GEOMETRY(Point, 4326)` + `ST_DWithin()`), allowing PostgreSQL to use R-Tree spatial indexes for sub-millisecond duplicate checks over millions of rows.
3. **Cloud Object Storage:** Migrate `FileStorageService` from local disk to **Amazon S3** or **Google Cloud Storage** with a **CDN (CloudFront)** for low-latency image delivery.
4. **Asynchronous Messaging:** Introduce **Apache Kafka** or **RabbitMQ** to decouple complaint submission from ML inference and push notifications, ensuring high burst ingestion during major civic storms or events.
5. **Read Replicas:** Configure PostgreSQL read-replicas for read-heavy operations (viewing citizen complaints, supervisor dashboards).


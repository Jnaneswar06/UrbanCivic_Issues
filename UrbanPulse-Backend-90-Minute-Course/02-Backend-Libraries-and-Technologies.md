# 02. Backend Libraries and Technologies (10 Minutes)

> **Urban Pulse: AI-Powered Smart Civic Intelligence Platform**  
> *Target Study Time: 10 Minutes*

---

## 1. Framework vs. Library vs. Dependency: The Fundamental Difference

Before diving into the codebase, understand this fundamental interview distinction:

| Concept | Definition | Inversion of Control (IoC) | Urban Pulse Example |
| :--- | :--- | :--- | :--- |
| **Framework** | A comprehensive structural foundation that dictates the architecture of your application. | **The Framework calls your code.** (You write methods, the framework calls them when an HTTP request arrives). | **Spring Boot** (manages application lifecycle, routing, security, and transactions). |
| **Library** | A collection of specialized helper classes and functions you call to perform a specific task. | **Your code calls the Library.** (You retain full control over when and how it executes). | **Firebase Admin SDK** (you explicitly call `verifyIdToken()` or `send()`). |
| **Dependency** | Any external piece of software (framework, library, or driver) declared in `pom.xml` that your project needs to compile, test, or run. | Managed automatically by Maven build tool. | `postgresql` JDBC driver, `lombok`, `firebase-admin`. |

---

## 2. In-Depth Analysis of Actual Dependencies in `pom.xml`

Here is the exact `pom.xml` dependency configuration from Urban Pulse:

```xml
<properties>
    <java.version>17</java.version>
    <firebase-admin.version>9.4.1</firebase-admin.version>
</properties>

<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.3.4</version>
</parent>
```

Let us examine every library used in the backend:

---

### 1. Spring Boot Starter Web (`spring-boot-starter-web`)

* **What is it?**  
  The core starter for building web and RESTful applications using Spring MVC, packaged with an embedded Tomcat servlet container and Jackson JSON serializer.
* **Why is it needed?**  
  To expose HTTP REST endpoints, parse incoming JSON request bodies into Java DTOs, and serialize Java response objects back into JSON.
* **Where is it used in Urban Pulse?**  
  In all REST controllers (`ComplaintController`, `AdminComplaintController`, `WorkerComplaintController`, etc.).
* **Which feature depends on it?**  
  Every client-server interaction: submitting complaints, querying duplicates, fetching dashboard statistics.
* **Actual Code Snippet:**
  ```java
  // com/urbanpulse/controller/ComplaintController.java
  @RestController
  @RequestMapping("/api/complaints")
  public class ComplaintController {
      @PostMapping
      @PreAuthorize("hasRole('CITIZEN')")
      public ResponseEntity<ComplaintResponse> createComplaint(
              @Valid @RequestBody CreateComplaintRequest request,
              Authentication authentication) {
          String citizenMobile = authentication.getName();
          ComplaintResponse response = complaintService.createComplaint(request, citizenMobile);
          return ResponseEntity.status(HttpStatus.CREATED).body(response);
      }
  }
  ```

---

### 2. Spring Boot Starter Data JPA (`spring-boot-starter-data-jpa`) & Hibernate

* **What is it?**  
  An abstraction layer over Jakarta Persistence API (JPA), using **Hibernate** as the default Object-Relational Mapping (ORM) engine.
* **Why is it needed?**  
  Eliminates tedious manual JDBC code (`PreparedStatement`, `ResultSet`, connection management). It maps database rows directly to Java Entity objects and generates SQL queries at runtime.
* **Where is it used in Urban Pulse?**  
  In all repository interfaces (`ComplaintRepository`, `UserRepository`, etc.) and entity models.
* **Which feature depends on it?**  
  Data persistence: saving complaints, querying citizen records, tracking complaint participants, storing SLA deadlines.
* **Actual Code Snippet:**
  ```java
  // com/urbanpulse/repository/ComplaintRepository.java
  @Repository
  public interface ComplaintRepository extends JpaRepository<Complaint, Long> {
      // Spring Data JPA derives SQL automatically from this method name:
      List<Complaint> findByCitizenMobileNumberOrderByCreatedAtDesc(String citizenMobileNumber);
      List<Complaint> findByIssueTypeOrderByCreatedAtDesc(IssueType issueType);
      Optional<Complaint> findByComplaintNumber(String complaintNumber);
  }
  ```

---

### 3. PostgreSQL JDBC Driver (`org.postgresql:postgresql`)

* **What is it?**  
  The official Type-4 JDBC driver that allows Java applications to establish socket connections and communicate with the PostgreSQL relational database.
* **Why is it needed?**  
  Hibernate creates the SQL strings, but the PostgreSQL driver actually transmits those bytes over TCP/IP to port `5432` of the PostgreSQL server.
* **Where is it used in Urban Pulse?**  
  Configured in `application.properties` and loaded at runtime by the HikariCP connection pool:
  ```properties
  spring.datasource.url=jdbc:postgresql://localhost:5432/urbanpulse
  spring.datasource.username=postgres
  spring.datasource.password=root123
  ```
* **Which feature depends on it?**  
  Every database operation across the entire platform.

---

### 4. Spring Boot Starter Security (`spring-boot-starter-security`)

* **What is it?**  
  The de facto security framework for Spring-based applications, providing authentication, authorization, and protection against common exploits.
* **Why is it needed?**  
  To secure endpoints with stateless Bearer tokens, enforce Role-Based Access Control (RBAC), and prevent unauthorized access to Admin or Supervisor APIs.
* **Where is it used in Urban Pulse?**  
  In `SecurityConfig.java`, `FirebaseAuthenticationFilter.java`, and method-level security annotations (`@PreAuthorize`).
* **Which feature depends on it?**  
  Role isolation: ensuring Citizens can only view their own complaints, Workers can only update assigned complaints, and Admins can perform manual triage.
* **Actual Code Snippet:**
  ```java
  // com/urbanpulse/config/SecurityConfig.java
  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
      http
          .csrf(AbstractHttpConfigurer::disable)
          .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
          .authorizeHttpRequests(auth -> auth
              .requestMatchers("/api/auth/**", "/uploads/**").permitAll()
              .requestMatchers("/api/admin/complaints/**").hasRole("ADMIN")
              .requestMatchers("/api/supervisor/complaints/**").hasRole("SUPERVISOR")
              .requestMatchers("/api/worker/complaints/**").hasRole("WORKER")
              .anyRequest().authenticated()
          )
          .addFilterBefore(firebaseAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
      return http.build();
  }
  ```

---

### 5. Firebase Admin SDK (`com.google.firebase:firebase-admin:9.4.1`)

* **What is it?**  
  Google's server-side Java SDK for securely interacting with Firebase services (Authentication and Cloud Messaging) using administrative service account credentials.
* **Why is it needed?**  
  1. Cryptographically verifying asymmetric Firebase ID Tokens (RS256 JWTs) signed by Google.
  2. Sending asynchronous push notifications via Firebase Cloud Messaging (FCM).
* **Where is it used in Urban Pulse?**  
  In `FirebaseAuthService.java` and `NotificationService.java`.
* **Which feature depends on it?**  
  Passwordless phone OTP authentication and instant mobile push notifications when complaint status updates.
* **Actual Code Snippet:**
  ```java
  // com/urbanpulse/service/FirebaseAuthService.java
  public FirebaseToken verifyIdToken(String idToken) throws FirebaseAuthException {
      // Cryptographically verifies signature against Google's public keys
      return FirebaseAuth.getInstance().verifyIdToken(idToken);
  }
  ```

---

### 6. Project Lombok (`org.projectlombok:lombok`)

* **What is it?**  
  A compile-time bytecode generator and annotation processor.
* **Why is it needed?**  
  Reduces repetitive Java boilerplate: getters, setters, `equals()`, `hashCode()`, `toString()`, constructors, and builder patterns.
* **Where is it used in Urban Pulse?**  
  In all Entity classes (`Complaint`, `User`) and DTOs (`ComplaintResponse`, `DuplicateComplaintSummary`).
* **Actual Code Snippet:**
  ```java
  // com/urbanpulse/entity/Complaint.java
  @Entity
  @Table(name = "complaints")
  @Data               // Generates getters, setters, toString, equals, hashCode
  @NoArgsConstructor  // Generates no-arg constructor required by JPA
  @AllArgsConstructor // Generates full-args constructor
  @Builder            // Implements clean Fluent Builder pattern
  public class Complaint { ... }
  ```

---

### 7. Spring Boot Starter Validation (`spring-boot-starter-validation`)

* **What is it?**  
  Jakarta Bean Validation provider (powered by Hibernate Validator under the hood).
* **Why is it needed?**  
  To validate incoming user data before it reaches the service layer, preventing SQL errors or invalid coordinate inputs.
* **Where is it used in Urban Pulse?**  
  In request DTOs (`@NotNull`, `@NotBlank`, `@Min`, `@Max`) and controller parameters (`@Valid`).
* **Which feature depends on it?**  
  Input validation for coordinates, phone numbers, and complaint payloads.
* **Actual Code Snippet:**
  ```java
  // com/urbanpulse/dto/CheckDuplicateRequest.java
  public class CheckDuplicateRequest {
      @NotNull(message = "Issue type is required")
      private IssueType issueType;

      @NotNull(message = "Latitude is required")
      private Double latitude;

      @NotNull(message = "Longitude is required")
      private Double longitude;
  }
  ```

---

### 8. ML Integration & HTTP Client (`RestTemplate`)

* **What is it?**  
  Spring's synchronous HTTP client used to invoke external REST APIs.
* **Why is it needed?**  
  Urban Pulse hosts its Python ML image classification model as a separate microservice (`localhost:5001`). `RestTemplate` makes the HTTP POST call passing the image as a multipart file resource.
* **Where is it used in Urban Pulse?**  
  In `MlInferenceService.java`.
* **Which feature depends on it?**  
  Automated AI civic classification.
* **Actual Code Snippet:**
  ```java
  // com/urbanpulse/service/MlInferenceService.java
  HttpHeaders headers = new HttpHeaders();
  headers.setContentType(MediaType.MULTIPART_FORM_DATA);

  MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
  body.add("file", new ByteArrayResource(imageBytes) {
      @Override public String getFilename() { return safeFilename; }
  });

  HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
  ResponseEntity<MlPredictionResponse> response = restTemplate.postForEntity(
          mlServiceUrl + "/predict", requestEntity, MlPredictionResponse.class
  );
  ```

---

### 9. File Storage & Multipart Uploads (`MultipartFile` & `WebMvcConfigurer`)

* **What is it?**  
  Standard Spring Web APIs (`org.springframework.web.multipart.MultipartFile`) and Java NIO (`java.nio.file`) for receiving and storing physical files on disk.
* **Why is it needed?**  
  Citizens take photos of potholes, garbage, or water leaks with smartphone cameras (up to 25MB). Spring Boot receives the binary stream, assigns a unique UUID filename, saves it locally, and serves it via HTTP.
* **Where is it used in Urban Pulse?**  
  In `FileStorageService.java` and `WebMvcConfig.java`.
* **Actual Code Snippet:**
  ```java
  // com/urbanpulse/service/FileStorageService.java
  String newFilename = UUID.randomUUID().toString() + extension;
  Path targetLocation = this.uploadDir.resolve(newFilename);
  Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);
  return "/uploads/" + newFilename;
  ```
  ```java
  // com/urbanpulse/config/WebMvcConfig.java
  @Override
  public void addResourceHandlers(ResourceHandlerRegistry registry) {
      Path uploadDir = Paths.get("uploads").toAbsolutePath().normalize();
      registry.addResourceHandler("/uploads/**")
              .addResourceLocations(uploadDir.toUri().toString());
  }
  ```

---

## 3. Technology Stack Summary Table for Quick Revision

| Component | Technology | Version | Purpose in Urban Pulse |
| :--- | :--- | :--- | :--- |
| **Language** | Java (OpenJDK) | 17 LTS | Modern LTS Java runtime (Records, Pattern Matching, Sealed classes) |
| **Core Framework** | Spring Boot | 3.3.4 | Microservice framework, auto-configuration, dependency injection |
| **Security** | Spring Security | 6.x | Stateless filter chain, RBAC, method authorization |
| **Data Access** | Spring Data JPA + Hibernate | 6.x | ORM mapping, automated repository queries, schema generation |
| **Database** | PostgreSQL | 15+ | Relational persistence, unique constraints, transactional ACID compliance |
| **Authentication** | Firebase Admin SDK | 9.4.1 | Asymmetric RS256 token verification for phone OTP logins |
| **Push Notifications** | Firebase Cloud Messaging (FCM) | 9.4.1 | Non-blocking async status update alerts to mobile app |
| **Code Generation** | Project Lombok | 1.18.x | Auto-generation of getters, setters, builders, constructors |
| **ML Microservice** | Python Flask + TensorFlow | Keras 3 | Computer Vision image classification running on port 5001 |
| **Build Tool** | Apache Maven | 3.9+ | Dependency management, build lifecycle, packaging into `.jar` |


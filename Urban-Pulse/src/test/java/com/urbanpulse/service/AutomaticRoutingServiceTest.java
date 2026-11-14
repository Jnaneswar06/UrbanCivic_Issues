package com.urbanpulse.service;

import com.urbanpulse.dto.AdminManualAssignRequest;
import com.urbanpulse.dto.AdminReviewApproveRequest;
import com.urbanpulse.dto.AdminReviewRejectRequest;
import com.urbanpulse.dto.MlPredictionResponse;
import com.urbanpulse.entity.Complaint;
import com.urbanpulse.entity.ComplaintStatus;
import com.urbanpulse.entity.ComplaintStatusHistory;
import com.urbanpulse.entity.Department;
import com.urbanpulse.entity.Role;
import com.urbanpulse.entity.User;
import com.urbanpulse.repository.ComplaintRepository;
import com.urbanpulse.repository.ComplaintStatusHistoryRepository;
import com.urbanpulse.repository.DepartmentRepository;
import com.urbanpulse.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AutomaticRoutingServiceTest {

    @Mock
    private MlInferenceService mlInferenceService;

    @Spy
    private CategoryDepartmentMappingService categoryDepartmentMappingService = new CategoryDepartmentMappingService();

    @Mock
    private ComplaintRepository complaintRepository;

    @Mock
    private ComplaintStatusHistoryRepository historyRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private DepartmentRepository departmentRepository;

    @Mock
    private NotificationService notificationService;

    @Spy
    private SlaService slaService = new SlaService();

    @InjectMocks
    private AutomaticRoutingService routingService;

    private User citizen;
    private User admin;
    private User sanitationSupervisor;
    private User roadsSupervisor;
    private Department sanitationDept;
    private Department roadsDept;
    private Complaint complaint;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(routingService, "confidenceThreshold", 0.50);

        citizen = User.builder().mobileNumber("+91 9000000001").name("Test Citizen").role(Role.CITIZEN).fcmToken("token1").build();
        admin = User.builder().mobileNumber("+91 9000000002").name("Test Admin").role(Role.ADMIN).build();

        sanitationDept = Department.builder().id(1L).name("Sanitation Department").description("Garbage").build();
        roadsDept = Department.builder().id(2L).name("Road Safety Department").description("Damaged Road Signs, Potholes and Road Cracks").build();

        sanitationSupervisor = User.builder().mobileNumber("+91 9000000003").name("Sanitation Sup").role(Role.SUPERVISOR).department(sanitationDept).fcmToken("token-sup1").build();
        roadsSupervisor = User.builder().mobileNumber("+91 9000000004").name("Road Safety Sup").role(Role.SUPERVISOR).department(roadsDept).fcmToken("token-sup2").build();

        complaint = Complaint.builder()
                .id(100L)
                .complaintNumber("CMP-100")
                .status(ComplaintStatus.SUBMITTED)
                .citizen(citizen)
                .imageUrl("/uploads/test.jpg")
                .latitude(17.3850)
                .longitude(78.4867)
                .build();

        lenient().when(complaintRepository.save(any(Complaint.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("Confidence > 50% (Garbage, 87%): Automatically forwards to Sanitation Department supervisor")
    void testAutoForwardHighConfidenceGarbage() {
        MlPredictionResponse prediction = MlPredictionResponse.builder()
                .predictedClass("Garbage")
                .confidence(0.87)
                .modelVersion("urbanpulse_cascaded_v2")
                .isCivicIssue(true)
                .build();

        when(mlInferenceService.predictImage(any(), any())).thenReturn(Optional.of(prediction));
        when(departmentRepository.findByName("Sanitation Department")).thenReturn(Optional.of(sanitationDept));
        when(userRepository.findByRoleAndDepartmentId(Role.SUPERVISOR, sanitationDept.getId())).thenReturn(List.of(sanitationSupervisor));

        Complaint result = routingService.routeComplaint(complaint, new byte[]{1, 2, 3}, "test.jpg");

        assertNotNull(result);
        assertEquals(ComplaintStatus.FORWARDED, result.getStatus());
        assertEquals("AUTO_ML", result.getAssignmentMethod());
        assertFalse(result.getRequiresManualReview());
        assertEquals(sanitationDept, result.getDepartment());
        assertEquals(sanitationSupervisor, result.getAssignedSupervisor());
        assertEquals("Garbage", result.getPredictedCategory());
        assertEquals(0.87, result.getConfidenceScore());
        assertNotNull(result.getAssignedAt());

        verify(notificationService).sendComplaintStatusNotification(eq("token1"), eq("CMP-100"), eq(ComplaintStatus.FORWARDED), eq("Sanitation Department"));
        verify(notificationService).sendComplaintStatusNotification(eq("token-sup1"), eq("CMP-100"), eq(ComplaintStatus.FORWARDED), eq("Sanitation Department"));
    }

    @Test
    @DisplayName("Confidence > 50% (Potholes, 92%): Automatically forwards to Road Safety Department supervisor")
    void testAutoForwardHighConfidencePotholes() {
        MlPredictionResponse prediction = MlPredictionResponse.builder()
                .predictedClass("Potholes and RoadCracks")
                .confidence(0.92)
                .modelVersion("urbanpulse_cascaded_v2")
                .isCivicIssue(true)
                .build();

        when(mlInferenceService.predictImage(any(), any())).thenReturn(Optional.of(prediction));
        when(departmentRepository.findByName("Road Safety Department")).thenReturn(Optional.of(roadsDept));
        when(userRepository.findByRoleAndDepartmentId(Role.SUPERVISOR, roadsDept.getId())).thenReturn(List.of(roadsSupervisor));

        Complaint result = routingService.routeComplaint(complaint, new byte[]{1, 2, 3}, "test.jpg");

        assertNotNull(result);
        assertEquals(ComplaintStatus.FORWARDED, result.getStatus());
        assertEquals("AUTO_ML", result.getAssignmentMethod());
        assertEquals(roadsDept, result.getDepartment());
        assertEquals(roadsSupervisor, result.getAssignedSupervisor());
        assertEquals("Potholes and Road Cracks", result.getPredictedCategory());
    }

    @Test
    @DisplayName("Confidence <= 50% (45%): Routes to Admin for manual review (SUBMITTED)")
    void testRouteLowConfidence() {
        MlPredictionResponse prediction = MlPredictionResponse.builder()
                .predictedClass("Garbage")
                .confidence(0.45)
                .modelVersion("urbanpulse_cascaded_v2")
                .isCivicIssue(true)
                .build();

        when(mlInferenceService.predictImage(any(), any())).thenReturn(Optional.of(prediction));

        Complaint result = routingService.routeComplaint(complaint, new byte[]{1, 2, 3}, "test.jpg");

        assertNotNull(result);
        assertEquals(ComplaintStatus.SUBMITTED, result.getStatus());
        assertEquals("MANUAL_REVIEW_LOW_CONFIDENCE", result.getAssignmentMethod());
        assertTrue(result.getRequiresManualReview());
        assertNotNull(result.getAdminReviewReason());
        assertTrue(result.getAdminReviewReason().contains("45.0%"));
        assertNull(result.getDepartment());
        assertNull(result.getAssignedSupervisor());

        verifyNoInteractions(notificationService);
    }

    @Test
    @DisplayName("ML Service Failure: Fallback routes to Admin for manual review without failing transaction")
    void testMlServiceFailureFallback() {
        when(mlInferenceService.predictImage(any(), any())).thenReturn(Optional.empty());

        Complaint result = routingService.routeComplaint(complaint, new byte[]{1, 2, 3}, "test.jpg");

        assertNotNull(result);
        assertEquals(ComplaintStatus.SUBMITTED, result.getStatus());
        assertEquals("MANUAL_REVIEW_ML_FAILED", result.getAssignmentMethod());
        assertTrue(result.getRequiresManualReview());
        assertNotNull(result.getAdminReviewReason());
        assertNull(result.getDepartment());

        verifyNoInteractions(notificationService);
    }

    @Test
    @DisplayName("Mapped department has no active supervisor: Retains department and routes to Admin for review")
    void testNoSupervisorInDepartmentFallback() {
        MlPredictionResponse prediction = MlPredictionResponse.builder()
                .predictedClass("Garbage")
                .confidence(0.85)
                .modelVersion("urbanpulse_cascaded_v2")
                .isCivicIssue(true)
                .build();

        when(mlInferenceService.predictImage(any(), any())).thenReturn(Optional.of(prediction));
        when(departmentRepository.findByName("Sanitation Department")).thenReturn(Optional.of(sanitationDept));
        when(userRepository.findByRoleAndDepartmentId(Role.SUPERVISOR, sanitationDept.getId())).thenReturn(Collections.emptyList());

        Complaint result = routingService.routeComplaint(complaint, new byte[]{1, 2, 3}, "test.jpg");

        assertNotNull(result);
        assertEquals(ComplaintStatus.SUBMITTED, result.getStatus());
        assertEquals("MANUAL_REVIEW_NO_SUPERVISOR", result.getAssignmentMethod());
        assertTrue(result.getRequiresManualReview());
        assertEquals(sanitationDept, result.getDepartment());
        assertNull(result.getAssignedSupervisor());

        verifyNoInteractions(notificationService);
    }

    @Test
    @DisplayName("Stage 1 Binary Non-Civic (NOT_CIVIC_ISSUE): Saved as PENDING_ADMIN_REVIEW, forwarded to Admin, department is null")
    void testStage1BinaryRejectionNonCivicIssue() {
        MlPredictionResponse prediction = MlPredictionResponse.builder()
                .success(true)
                .isCivicIssue(false)
                .classificationStatus("REJECTED")
                .stage("BINARY_RELEVANCE")
                .binaryPrediction("NOT_CIVIC_ISSUE")
                .notCivicProbability(0.93)
                .civicProbability(0.07)
                .predictedClass(null)
                .confidence(0.93)
                .modelVersion("urbanpulse_cascaded_v2")
                .build();

        when(mlInferenceService.predictImage(any(), any())).thenReturn(Optional.of(prediction));

        Complaint result = routingService.routeComplaint(complaint, new byte[]{1, 2, 3}, "selfie.jpg");

        assertNotNull(result);
        assertEquals(ComplaintStatus.PENDING_ADMIN_REVIEW, result.getStatus());
        assertEquals("PENDING_ADMIN_REVIEW", result.getReviewStatus());
        assertFalse(result.getIsCivicIssue());
        assertEquals("NOT_CIVIC_ISSUE", result.getBinaryPrediction());
        assertEquals(0.93, result.getNotCivicProbability());
        assertEquals(0.07, result.getCivicProbability());
        assertNull(result.getPredictedCategory());
        assertTrue(result.getRequiresManualReview());
        assertEquals("PENDING_ADMIN_REVIEW", result.getAssignmentMethod());
        assertNotNull(result.getAdminReviewReason());
        assertTrue(result.getAdminReviewReason().contains("NOT_CIVIC_ISSUE"));
        assertNull(result.getDepartment());
        assertNull(result.getAssignedSupervisor());
        assertNull(result.getAssignedWorker());

        verify(historyRepository).save(any(ComplaintStatusHistory.class));
        verifyNoInteractions(notificationService);
    }

    @Test
    @DisplayName("Stage 1 Binary Pass (CIVIC_ISSUE) + Stage 2 High Confidence: Sets isCivicIssue=true and auto-forwards")
    void testStage1BinaryPassWithHighConfidenceEightClass() {
        MlPredictionResponse prediction = MlPredictionResponse.builder()
                .success(true)
                .isCivicIssue(true)
                .classificationStatus("CLASSIFIED")
                .stage("EIGHT_CLASS_CLASSIFICATION")
                .binaryPrediction("CIVIC_ISSUE")
                .notCivicProbability(0.04)
                .civicProbability(0.96)
                .predictedClass("Potholes and RoadCracks")
                .confidence(0.89)
                .modelVersion("urbanpulse_cascaded_v2")
                .build();

        when(mlInferenceService.predictImage(any(), any())).thenReturn(Optional.of(prediction));
        when(departmentRepository.findByName("Road Safety Department")).thenReturn(Optional.of(roadsDept));
        when(userRepository.findByRoleAndDepartmentId(Role.SUPERVISOR, roadsDept.getId())).thenReturn(List.of(roadsSupervisor));

        Complaint result = routingService.routeComplaint(complaint, new byte[]{1, 2, 3}, "pothole.jpg");

        assertNotNull(result);
        assertEquals(ComplaintStatus.FORWARDED, result.getStatus());
        assertTrue(result.getIsCivicIssue());
        assertEquals("CIVIC_ISSUE", result.getBinaryPrediction());
        assertEquals(0.04, result.getNotCivicProbability());
        assertEquals(0.96, result.getCivicProbability());
        assertEquals("Potholes and Road Cracks", result.getPredictedCategory());
        assertEquals(0.89, result.getConfidenceScore());
        assertEquals("AUTO_ML", result.getAssignmentMethod());
        assertFalse(result.getRequiresManualReview());
        assertEquals(roadsDept, result.getDepartment());
        assertEquals(roadsSupervisor, result.getAssignedSupervisor());
    }

    @Test
    @DisplayName("Admin manual approval: Approves non-civic complaint, categorizes, routes to department supervisor")
    void testAdminApproveNonCivicComplaint() {
        complaint.setStatus(ComplaintStatus.PENDING_ADMIN_REVIEW);
        complaint.setIsCivicIssue(false);
        complaint.setReviewStatus("PENDING_ADMIN_REVIEW");
        complaint.setRequiresManualReview(true);

        when(userRepository.findByMobileNumber("+91 9000000002")).thenReturn(Optional.of(admin));
        when(complaintRepository.findById(100L)).thenReturn(Optional.of(complaint));
        when(departmentRepository.findByName("Road Safety Department")).thenReturn(Optional.of(roadsDept));
        when(userRepository.findByRoleAndDepartmentId(Role.SUPERVISOR, roadsDept.getId())).thenReturn(List.of(roadsSupervisor));

        AdminReviewApproveRequest request = AdminReviewApproveRequest.builder()
                .category("Potholes and RoadCracks")
                .remarks("Overridden by admin: Real pothole visible")
                .build();

        Complaint approved = routingService.approveNonCivicComplaintByAdmin(100L, request, "+91 9000000002");

        assertNotNull(approved);
        assertEquals(ComplaintStatus.FORWARDED, approved.getStatus());
        assertTrue(approved.getIsCivicIssue());
        assertEquals("ADMIN_APPROVED", approved.getReviewStatus());
        assertEquals("ADMIN_VERIFIED", approved.getClassificationSource());
        assertEquals("ADMIN_MANUAL_APPROVAL", approved.getAssignmentMethod());
        assertEquals("Potholes and Road Cracks", approved.getPredictedCategory());
        assertEquals(roadsDept, approved.getDepartment());
        assertEquals(roadsSupervisor, approved.getAssignedSupervisor());
        assertFalse(approved.getRequiresManualReview());
        assertEquals("+91 9000000002", approved.getAdminReviewedBy());
        assertNotNull(approved.getAdminReviewedAt());

        verify(notificationService).sendComplaintStatusNotification(eq("token1"), eq("CMP-100"), eq(ComplaintStatus.FORWARDED), eq("Road Safety Department"));
        verify(notificationService).sendComplaintStatusNotification(eq("token-sup2"), eq("CMP-100"), eq(ComplaintStatus.FORWARDED), eq("Road Safety Department"));
    }

    @Test
    @DisplayName("Admin manual rejection: Rejects non-civic complaint with reason without department assignment")
    void testAdminRejectNonCivicComplaint() {
        complaint.setStatus(ComplaintStatus.PENDING_ADMIN_REVIEW);
        complaint.setIsCivicIssue(false);
        complaint.setReviewStatus("PENDING_ADMIN_REVIEW");
        complaint.setRequiresManualReview(true);

        when(userRepository.findByMobileNumber("+91 9000000002")).thenReturn(Optional.of(admin));
        when(complaintRepository.findById(100L)).thenReturn(Optional.of(complaint));

        AdminReviewRejectRequest request = AdminReviewRejectRequest.builder()
                .rejectionReason("Selfie/irrelevant non-civic upload")
                .remarks("Not a municipal issue")
                .build();

        Complaint rejected = routingService.rejectNonCivicComplaintByAdmin(100L, request, "+91 9000000002");

        assertNotNull(rejected);
        assertEquals(ComplaintStatus.REJECTED, rejected.getStatus());
        assertEquals("ADMIN_REJECTED", rejected.getReviewStatus());
        assertEquals("ADMIN_VERIFIED", rejected.getClassificationSource());
        assertEquals("REJECTED", rejected.getClassificationStatus());
        assertEquals("Selfie/irrelevant non-civic upload", rejected.getAdminRejectionReason());
        assertNull(rejected.getDepartment());
        assertNull(rejected.getAssignedSupervisor());
        assertNull(rejected.getAssignedWorker());
        assertFalse(rejected.getRequiresManualReview());
        assertEquals("+91 9000000002", rejected.getAdminReviewedBy());

        verify(notificationService).sendComplaintStatusNotification(eq("token1"), eq("CMP-100"), eq(ComplaintStatus.REJECTED), eq("Admin Review"));
    }

    @Test
    @DisplayName("Non-Admin user attempting approval throws AccessDeniedException")
    void testNonAdminApprovalAccessDenied() {
        when(userRepository.findByMobileNumber("+91 9000000001")).thenReturn(Optional.of(citizen));

        AdminReviewApproveRequest request = AdminReviewApproveRequest.builder()
                .category("Garbage")
                .build();

        assertThrows(AccessDeniedException.class, () ->
                routingService.approveNonCivicComplaintByAdmin(100L, request, "+91 9000000001")
        );
    }

    @Test
    @DisplayName("Admin manual assignment: Reassigns department/supervisor, clears manual review flag and sets FORWARDED")
    void testAdminManualAssign() {
        complaint.setRequiresManualReview(true);
        complaint.setAdminReviewReason("Low confidence");

        when(userRepository.findByMobileNumber("+91 9000000002")).thenReturn(Optional.of(admin));
        when(complaintRepository.findById(100L)).thenReturn(Optional.of(complaint));
        when(departmentRepository.findById(1L)).thenReturn(Optional.of(sanitationDept));
        when(userRepository.findByRoleAndDepartmentId(Role.SUPERVISOR, 1L)).thenReturn(List.of(sanitationSupervisor));

        AdminManualAssignRequest request = AdminManualAssignRequest.builder()
                .departmentId(1L)
                .category("Garbage")
                .remarks("Reviewed by admin")
                .build();

        Complaint updated = routingService.manualAssignByAdmin(100L, request, "+91 9000000002");

        assertNotNull(updated);
        assertEquals(ComplaintStatus.FORWARDED, updated.getStatus());
        assertEquals("MANUAL_ADMIN", updated.getAssignmentMethod());
        assertFalse(updated.getRequiresManualReview());
        assertNull(updated.getAdminReviewReason());
        assertEquals(sanitationDept, updated.getDepartment());
        assertEquals(sanitationSupervisor, updated.getAssignedSupervisor());
        assertNotNull(updated.getAssignedAt());
    }
}

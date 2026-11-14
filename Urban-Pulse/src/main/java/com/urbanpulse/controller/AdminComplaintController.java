package com.urbanpulse.controller;

import com.urbanpulse.dto.AdminManualAssignRequest;
import com.urbanpulse.dto.AdminReviewApproveRequest;
import com.urbanpulse.dto.AdminReviewRejectRequest;
import com.urbanpulse.dto.ComplaintResponse;
import com.urbanpulse.dto.ForwardComplaintRequest;
import com.urbanpulse.service.ComplaintService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/complaints")
public class AdminComplaintController {

    private final ComplaintService complaintService;

    public AdminComplaintController(ComplaintService complaintService) {
        this.complaintService = complaintService;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<ComplaintResponse>> getAdminComplaints() {
        List<ComplaintResponse> complaints = complaintService.getAdminComplaints();
        return ResponseEntity.ok(complaints);
    }

    @GetMapping("/pending-review")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<ComplaintResponse>> getPendingReviewComplaints() {
        List<ComplaintResponse> complaints = complaintService.getPendingReviewComplaints();
        return ResponseEntity.ok(complaints);
    }

    @PatchMapping("/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ComplaintResponse> approveComplaint(@PathVariable Long id,
                                                              @Valid @RequestBody AdminReviewApproveRequest request,
                                                              Authentication authentication) {
        String adminMobile = authentication.getName();
        ComplaintResponse response = complaintService.approveNonCivicComplaint(id, request, adminMobile);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ComplaintResponse> rejectComplaint(@PathVariable Long id,
                                                             @Valid @RequestBody AdminReviewRejectRequest request,
                                                             Authentication authentication) {
        String adminMobile = authentication.getName();
        ComplaintResponse response = complaintService.rejectNonCivicComplaint(id, request, adminMobile);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/forward")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ComplaintResponse> forwardComplaint(@PathVariable Long id,
                                                              @Valid @RequestBody ForwardComplaintRequest request,
                                                              Authentication authentication) {
        String adminMobile = authentication.getName();
        ComplaintResponse response = complaintService.forwardComplaint(id, request, adminMobile);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/manual-assign")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ComplaintResponse> manualAssignComplaint(@PathVariable Long id,
                                                                   @Valid @RequestBody AdminManualAssignRequest request,
                                                                   Authentication authentication) {
        String adminMobile = authentication.getName();
        ComplaintResponse response = complaintService.manualAssignComplaint(id, request, adminMobile);
        return ResponseEntity.ok(response);
    }
}

package com.urbanpulse.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminReviewApproveRequest {

    @NotBlank(message = "Civic category is required")
    private String category;

    private Long departmentId;

    private String supervisorMobile;

    private String remarks;
}

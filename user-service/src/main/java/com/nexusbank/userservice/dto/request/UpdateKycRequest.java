package com.nexusbank.userservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpdateKycRequest {

    @NotBlank
    private String status;   // PENDING | VERIFIED | REJECTED
}

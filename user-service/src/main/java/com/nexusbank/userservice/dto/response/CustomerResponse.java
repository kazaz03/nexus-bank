package com.nexusbank.userservice.dto.response;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class CustomerResponse {

    private Long id;
    private Long userId;
    private String email;
    private String firstName;
    private String lastName;
    private LocalDate dateOfBirth;
    private String idCardNumber;
    private String address;
    private String phone;
    private String kycStatus;
    private LocalDateTime createdAt;
    private Boolean isActive;
    private LocalDateTime updatedAt;
}

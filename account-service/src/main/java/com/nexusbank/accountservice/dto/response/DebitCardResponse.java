package com.nexusbank.accountservice.dto.response;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class DebitCardResponse {

    private Long id;
    private Long accountId;
    private String maskedCardNumber;
    private LocalDate expiryDate;
    private String status;
    private LocalDateTime issuedAt;
    private Long issuedBy;
}

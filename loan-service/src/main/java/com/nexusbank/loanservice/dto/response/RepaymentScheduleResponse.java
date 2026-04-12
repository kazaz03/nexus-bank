package com.nexusbank.loanservice.dto.response;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class RepaymentScheduleResponse {

    private Long id;
    private Long loanApplicationId;
    private Integer installmentNumber;
    private LocalDate dueDate;
    private BigDecimal amountDue;
    private BigDecimal amountPaid;
    private String status;
    private LocalDateTime paidAt;
}

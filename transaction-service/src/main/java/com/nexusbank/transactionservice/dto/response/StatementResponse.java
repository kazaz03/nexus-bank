package com.nexusbank.transactionservice.dto.response;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class StatementResponse {

    private Long accountId;
    private LocalDateTime fromDate;
    private LocalDateTime toDate;
    private BigDecimal openingBalance;
    private BigDecimal totalDeposited;
    private BigDecimal totalWithdrawn;
    private BigDecimal closingBalance;
    private List<TransactionResponse> transactions;
}

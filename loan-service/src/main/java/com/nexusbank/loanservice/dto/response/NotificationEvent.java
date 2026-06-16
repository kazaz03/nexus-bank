package com.nexusbank.loanservice.dto.response;

/**
 * Payload pushed to a client over SSE when a loan saga event completes.
 * Kept intentionally small — the frontend can re-fetch the full loan if needed.
 */
public record NotificationEvent(
        String type,      // LOAN_APPROVED | LOAN_DISBURSED | LOAN_REJECTED
        Long loanId,
        String message
) {}

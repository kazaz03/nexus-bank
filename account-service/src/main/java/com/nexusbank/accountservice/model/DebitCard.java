package com.nexusbank.accountservice.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "debit_cards")
public class DebitCard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Column(name = "masked_card_number", nullable = false)
    private String maskedCardNumber;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "cvv_reference")
    private String cvvReference;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private CardStatus status = CardStatus.PENDING;

    @Column(name = "issued_at")
    private LocalDateTime issuedAt;

    @Column(name = "issued_by")
    private Long issuedBy;

    public enum CardStatus {
        PENDING, ACTIVE, BLOCKED, CANCELLED
    }
}
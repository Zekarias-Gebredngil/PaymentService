package com.payment.nadi.application.dto.response;

import com.payment.nadi.domain.model.PaymentStatus;
import com.payment.nadi.domain.model.PaymentType;
import com.payment.nadi.domain.model.Transaction;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransactionResponse(
        UUID transactionId,
        UUID paymentId,
        String transactionReference,
        PaymentType type,
        BigDecimal amount,
        BigDecimal fee,
        String currency,
        UUID accountId,
        String description,
        PaymentStatus status,
        Instant createdAt
) {
    public static TransactionResponse from(Transaction tx) {
        return new TransactionResponse(
                tx.getId(),
                tx.getPaymentId(),
                tx.getTransactionReference(),
                tx.getType(),
                tx.getAmount().amount(),
                tx.getFee().amount(),
                tx.getAmount().getCurrencyCode(),
                tx.getAccountId(),
                tx.getDescription(),
                tx.getStatus(),
                tx.getCreatedAt()
        );
    }
}

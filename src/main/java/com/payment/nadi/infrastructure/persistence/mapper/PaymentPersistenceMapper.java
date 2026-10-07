package com.payment.nadi.infrastructure.persistence.mapper;

import com.payment.nadi.domain.model.LedgerEntry;
import com.payment.nadi.domain.model.Money;
import com.payment.nadi.domain.model.Payment;
import com.payment.nadi.domain.model.PaymentAttempt;
import com.payment.nadi.domain.model.Transaction;
import com.payment.nadi.infrastructure.persistence.entity.LedgerEntryEntity;
import com.payment.nadi.infrastructure.persistence.entity.PaymentAttemptEntity;
import com.payment.nadi.infrastructure.persistence.entity.PaymentEntity;
import com.payment.nadi.infrastructure.persistence.entity.TransactionEntity;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;

@Component
public class PaymentPersistenceMapper {

    public PaymentEntity toEntity(Payment domain) {
        if (domain == null) return null;
        return PaymentEntity.builder()
                .id(domain.getId())
                .reference(domain.getReference())
                .type(domain.getType())
                .status(domain.getStatus())
                .amount(domain.getAmount().amount())
                .currency(domain.getAmount().getCurrencyCode())
                .customerId(domain.getCustomerId())
                .sourceAccountId(domain.getSourceAccountId())
                .destinationAccountId(domain.getDestinationAccountId())
                .provider(domain.getProvider())
                .providerReference(domain.getProviderReference())
                .sourceSystem(domain.getSourceSystem())
                .description(domain.getDescription())
                .failureReason(domain.getFailureReason())
                .metadata(domain.getMetadata() != null ? new HashMap<>(domain.getMetadata()) : new HashMap<>())
                .version(domain.getVersion())
                .createdAt(domain.getCreatedAt())
                .updatedAt(domain.getUpdatedAt())
                .build();
    }

    public Payment toDomain(PaymentEntity entity) {
        if (entity == null) return null;
        Payment payment = new Payment();
        payment.setId(entity.getId());
        payment.setReference(entity.getReference());
        payment.setType(entity.getType());
        payment.setStatus(entity.getStatus());
        payment.setAmount(Money.of(entity.getAmount(), entity.getCurrency()));
        payment.setCustomerId(entity.getCustomerId());
        payment.setSourceAccountId(entity.getSourceAccountId());
        payment.setDestinationAccountId(entity.getDestinationAccountId());
        payment.setProvider(entity.getProvider());
        payment.setProviderReference(entity.getProviderReference());
        payment.setSourceSystem(entity.getSourceSystem());
        payment.setDescription(entity.getDescription());
        payment.setFailureReason(entity.getFailureReason());
        payment.setMetadata(entity.getMetadata());
        payment.setVersion(entity.getVersion());
        payment.setCreatedAt(entity.getCreatedAt());
        payment.setUpdatedAt(entity.getUpdatedAt());
        return payment;
    }

    public void updateEntity(PaymentEntity target, Payment source) {
        if (target == null || source == null) return;
        target.setReference(source.getReference());
        target.setType(source.getType());
        target.setStatus(source.getStatus());
        if (source.getAmount() != null) {
            target.setAmount(source.getAmount().amount());
            target.setCurrency(source.getAmount().getCurrencyCode());
        }
        target.setCustomerId(source.getCustomerId());
        target.setSourceAccountId(source.getSourceAccountId());
        target.setDestinationAccountId(source.getDestinationAccountId());
        target.setProvider(source.getProvider());
        target.setProviderReference(source.getProviderReference());
        target.setSourceSystem(source.getSourceSystem());
        target.setDescription(source.getDescription());
        target.setFailureReason(source.getFailureReason());
        target.setMetadata(source.getMetadata() != null ? new HashMap<>(source.getMetadata()) : new HashMap<>());
        target.setUpdatedAt(source.getUpdatedAt() != null ? source.getUpdatedAt() : Instant.now());
    }

    public TransactionEntity toEntity(Transaction domain) {
        if (domain == null) return null;
        return TransactionEntity.builder()
                .id(domain.getId())
                .paymentId(domain.getPaymentId())
                .transactionReference(domain.getTransactionReference())
                .type(domain.getType())
                .amount(domain.getAmount().amount())
                .fee(domain.getFee().amount())
                .currency(domain.getAmount().getCurrencyCode())
                .accountId(domain.getAccountId())
                .description(domain.getDescription())
                .status(domain.getStatus())
                .createdAt(domain.getCreatedAt())
                .build();
    }

    public Transaction toDomain(TransactionEntity entity) {
        if (entity == null) return null;
        Transaction tx = new Transaction();
        tx.setId(entity.getId());
        tx.setPaymentId(entity.getPaymentId());
        tx.setTransactionReference(entity.getTransactionReference());
        tx.setType(entity.getType());
        tx.setAmount(Money.of(entity.getAmount(), entity.getCurrency()));
        tx.setFee(Money.of(entity.getFee(), entity.getCurrency()));
        tx.setAccountId(entity.getAccountId());
        tx.setDescription(entity.getDescription());
        tx.setStatus(entity.getStatus());
        tx.setCreatedAt(entity.getCreatedAt());
        return tx;
    }

    public PaymentAttemptEntity toEntity(PaymentAttempt domain) {
        if (domain == null) return null;
        return PaymentAttemptEntity.builder()
                .id(domain.getId())
                .paymentId(domain.getPaymentId())
                .provider(domain.getProvider())
                .attemptNumber(domain.getAttemptNumber())
                .status(domain.getStatus())
                .requestId(domain.getRequestId())
                .providerReference(domain.getProviderReference())
                .errorCode(domain.getErrorCode())
                .errorMessage(domain.getErrorMessage())
                .createdAt(domain.getCreatedAt())
                .build();
    }

    public PaymentAttempt toDomain(PaymentAttemptEntity entity) {
        if (entity == null) return null;
        PaymentAttempt attempt = new PaymentAttempt();
        attempt.setId(entity.getId());
        attempt.setPaymentId(entity.getPaymentId());
        attempt.setProvider(entity.getProvider());
        attempt.setAttemptNumber(entity.getAttemptNumber());
        attempt.setStatus(entity.getStatus());
        attempt.setRequestId(entity.getRequestId());
        attempt.setProviderReference(entity.getProviderReference());
        attempt.setErrorCode(entity.getErrorCode());
        attempt.setErrorMessage(entity.getErrorMessage());
        attempt.setCreatedAt(entity.getCreatedAt());
        return attempt;
    }

    public LedgerEntryEntity toEntity(LedgerEntry domain) {
        if (domain == null) return null;
        return LedgerEntryEntity.builder()
                .id(domain.getId())
                .transactionId(domain.getTransactionId())
                .accountId(domain.getAccountId())
                .entryType(domain.getEntryType())
                .amount(domain.getAmount().amount())
                .currency(domain.getAmount().getCurrencyCode())
                .description(domain.getDescription())
                .createdAt(domain.getCreatedAt())
                .build();
    }

    public LedgerEntry toDomain(LedgerEntryEntity entity) {
        if (entity == null) return null;
        LedgerEntry entry = new LedgerEntry();
        entry.setId(entity.getId());
        entry.setTransactionId(entity.getTransactionId());
        entry.setAccountId(entity.getAccountId());
        entry.setEntryType(entity.getEntryType());
        entry.setAmount(Money.of(entity.getAmount(), entity.getCurrency()));
        entry.setDescription(entity.getDescription());
        entry.setCreatedAt(entity.getCreatedAt());
        return entry;
    }
}

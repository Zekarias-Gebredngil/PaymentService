package com.payment.nadi.domain.repository;

import com.payment.nadi.domain.model.Payment;
import com.payment.nadi.domain.model.PaymentStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository {

    Payment save(Payment payment);

    Optional<Payment> findById(UUID id);

    Optional<Payment> findByReference(String reference);

    Optional<Payment> findByProviderReference(String providerReference);

    List<Payment> findByCustomerId(UUID customerId, int limit, int offset);

    List<Payment> findByStatus(PaymentStatus status);

    boolean existsByReference(String reference);
}

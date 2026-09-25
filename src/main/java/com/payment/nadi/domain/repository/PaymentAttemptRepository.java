package com.payment.nadi.domain.repository;

import com.payment.nadi.domain.model.PaymentAttempt;

import java.util.List;
import java.util.UUID;

public interface PaymentAttemptRepository {

    PaymentAttempt save(PaymentAttempt attempt);

    List<PaymentAttempt> findByPaymentIdOrderByAttemptNumberAsc(UUID paymentId);

    int countByPaymentId(UUID paymentId);
}

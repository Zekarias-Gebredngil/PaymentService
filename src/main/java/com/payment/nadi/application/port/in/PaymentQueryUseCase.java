package com.payment.nadi.application.port.in;

import com.payment.nadi.application.dto.response.PageResponse;
import com.payment.nadi.application.dto.response.PaymentResponse;
import com.payment.nadi.application.dto.response.TransactionResponse;
import com.payment.nadi.domain.model.Money;

import java.util.List;
import java.util.UUID;

public interface PaymentQueryUseCase {

    PaymentResponse getPaymentById(UUID paymentId);

    PaymentResponse getPaymentByReference(String reference);

    PageResponse<PaymentResponse> getCustomerPayments(UUID customerId, int page, int size);

    List<TransactionResponse> getPaymentTransactions(UUID paymentId);

    PaymentResponse refreshPaymentStatus(UUID paymentId);

    Money getAccountBalance(UUID accountId, String currency);
}

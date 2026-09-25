package com.payment.nadi.application.port.in;

import com.payment.nadi.application.dto.request.TransferRequest;
import com.payment.nadi.application.dto.response.PaymentResponse;

public interface TransferUseCase {

    PaymentResponse initiateTransfer(TransferRequest request, String idempotencyKey);
}

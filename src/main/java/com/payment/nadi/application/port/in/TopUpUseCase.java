package com.payment.nadi.application.port.in;

import com.payment.nadi.application.dto.request.TopUpRequest;
import com.payment.nadi.application.dto.response.PaymentResponse;

public interface TopUpUseCase {

    PaymentResponse initiateTopUp(TopUpRequest request, String idempotencyKey);
}

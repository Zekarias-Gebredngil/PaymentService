package com.payment.nadi.application.port.in;

import com.payment.nadi.application.dto.request.WithdrawRequest;
import com.payment.nadi.application.dto.response.PaymentResponse;

public interface WithdrawUseCase {

    PaymentResponse initiateWithdraw(WithdrawRequest request, String idempotencyKey);
}

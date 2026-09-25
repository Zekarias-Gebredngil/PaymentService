package com.payment.nadi.domain.exception;

import com.payment.nadi.domain.model.PaymentStatus;

public class InvalidStateTransitionException extends PaymentException {
    public InvalidStateTransitionException(PaymentStatus current, PaymentStatus target) {
        super("INVALID_STATE_TRANSITION", String.format("Cannot transition payment status from %s to %s", current, target));
    }
}

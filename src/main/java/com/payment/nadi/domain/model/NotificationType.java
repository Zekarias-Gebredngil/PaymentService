package com.payment.nadi.domain.model;

public enum NotificationType {
    TRANSACTION_DEBIT,
    TRANSACTION_CREDIT,
    TOP_UP_SUCCESS,
    WITHDRAWAL_SUCCESS,
    PAYMENT_FAILED,
    SECURITY_ALERT,
    OTP_ALERT,
    ACCOUNT_STATUS
}

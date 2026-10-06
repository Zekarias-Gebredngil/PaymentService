package com.payment.nadi.application.port.out;

import com.payment.nadi.domain.model.NotificationType;

import java.util.UUID;

public interface SmsAlertPort {
    boolean sendSms(UUID userId, String phoneNumber, String message, NotificationType type, String referenceId);
}

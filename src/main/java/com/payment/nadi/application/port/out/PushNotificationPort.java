package com.payment.nadi.application.port.out;

import com.payment.nadi.domain.model.NotificationType;

import java.util.Map;
import java.util.UUID;

public interface PushNotificationPort {
    boolean sendPush(UUID userId, String pushToken, String title, String body, NotificationType type, String referenceId, Map<String, String> data);
}

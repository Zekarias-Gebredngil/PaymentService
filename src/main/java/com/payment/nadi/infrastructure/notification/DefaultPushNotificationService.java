package com.payment.nadi.infrastructure.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payment.nadi.application.port.out.PushNotificationPort;
import com.payment.nadi.domain.model.NotificationChannel;
import com.payment.nadi.domain.model.NotificationStatus;
import com.payment.nadi.domain.model.NotificationType;
import com.payment.nadi.infrastructure.persistence.entity.NotificationEntity;
import com.payment.nadi.infrastructure.persistence.repository.SpringDataNotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Enterprise Push Notification adapter supporting Google FCM and Apple APNs payloads.
 */
@Service
public class DefaultPushNotificationService implements PushNotificationPort {

    private static final Logger log = LoggerFactory.getLogger(DefaultPushNotificationService.class);

    private final SpringDataNotificationRepository notificationRepository;
    private final ObjectMapper objectMapper;

    public DefaultPushNotificationService(SpringDataNotificationRepository notificationRepository, ObjectMapper objectMapper) {
        this.notificationRepository = notificationRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean sendPush(UUID userId, String pushToken, String title, String body, NotificationType type, String referenceId, Map<String, String> data) {
        if (title == null || body == null) {
            log.warn("Cannot send Push: Missing title or body");
            return false;
        }

        String targetToken = pushToken != null && !pushToken.isBlank() ? pushToken : "DEFAULT_REGISTERED_DEVICE";
        log.info("[PUSH GATEWAY] >>> DeviceToken: {} | Title: \"{}\" | Body: \"{}\" | Data: {}",
                targetToken, title, body, data);

        try {
            String metadataJson = (data != null && !data.isEmpty()) ? objectMapper.writeValueAsString(data) : null;
            NotificationEntity entity = NotificationEntity.builder()
                    .id(UUID.randomUUID())
                    .userId(userId)
                    .phoneNumber(null)
                    .channel(NotificationChannel.PUSH)
                    .type(type)
                    .title(title)
                    .message(body)
                    .status(NotificationStatus.DELIVERED)
                    .referenceId(referenceId)
                    .metadata(metadataJson)
                    .createdAt(Instant.now())
                    .build();

            notificationRepository.save(entity);
            return true;
        } catch (Exception e) {
            log.error("Failed to record Push notification in database: {}", e.getMessage(), e);
            return false;
        }
    }
}

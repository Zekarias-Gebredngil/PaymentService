package com.payment.nadi.infrastructure.notification;

import com.payment.nadi.application.port.out.SmsAlertPort;
import com.payment.nadi.domain.model.NotificationChannel;
import com.payment.nadi.domain.model.NotificationStatus;
import com.payment.nadi.domain.model.NotificationType;
import com.payment.nadi.infrastructure.persistence.entity.NotificationEntity;
import com.payment.nadi.infrastructure.persistence.repository.SpringDataNotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * Enterprise SMS delivery adapter with audit logging and persistence.
 * Adheres to SOLID Single Responsibility and Open/Closed principles:
 * Extensible for integration with Ethio Telecom, Twilio, or AWS SNS gateways.
 */
@Service
public class DefaultSmsAlertService implements SmsAlertPort {

    private static final Logger log = LoggerFactory.getLogger(DefaultSmsAlertService.class);

    private final SpringDataNotificationRepository notificationRepository;

    public DefaultSmsAlertService(SpringDataNotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Override
    public boolean sendSms(UUID userId, String phoneNumber, String message, NotificationType type, String referenceId) {
        if (phoneNumber == null || phoneNumber.isBlank() || message == null || message.isBlank()) {
            log.warn("Cannot send SMS: Missing recipient phone number or message content");
            return false;
        }

        String normalizedPhone = normalizePhoneNumber(phoneNumber);
        log.info("[SMS GATEWAY] >>> Recipient: {} | Type: {} | Ref: {} | Message: \"{}\"",
                normalizedPhone, type, referenceId != null ? referenceId : "N/A", message);

        try {
            // Persist notification record for auditing and inbox tracking
            NotificationEntity entity = NotificationEntity.builder()
                    .id(UUID.randomUUID())
                    .userId(userId)
                    .phoneNumber(normalizedPhone)
                    .channel(NotificationChannel.SMS)
                    .type(type)
                    .title("SMS Alert")
                    .message(message)
                    .status(NotificationStatus.DELIVERED)
                    .referenceId(referenceId)
                    .createdAt(Instant.now())
                    .build();

            notificationRepository.save(entity);
            return true;
        } catch (Exception e) {
            log.error("Failed to record SMS notification in database: {}", e.getMessage(), e);
            return false;
        }
    }

    private String normalizePhoneNumber(String phone) {
        String clean = phone.trim().replaceAll("[\\s-]", "");
        if (clean.startsWith("09")) {
            return "+251" + clean.substring(1);
        } else if (clean.startsWith("9") && clean.length() == 9) {
            return "+251" + clean;
        }
        return clean;
    }
}

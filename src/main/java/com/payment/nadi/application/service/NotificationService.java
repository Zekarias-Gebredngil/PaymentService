package com.payment.nadi.application.service;

import com.payment.nadi.application.dto.request.DeviceTokenRequest;
import com.payment.nadi.application.dto.request.SendNotificationRequest;
import com.payment.nadi.application.dto.response.NotificationDto;
import com.payment.nadi.application.dto.response.PageResponse;
import com.payment.nadi.application.port.in.NotificationUseCase;
import com.payment.nadi.application.port.out.PushNotificationPort;
import com.payment.nadi.application.port.out.SmsAlertPort;
import com.payment.nadi.domain.exception.InvalidPaymentException;
import com.payment.nadi.domain.model.NotificationType;
import com.payment.nadi.infrastructure.persistence.entity.NotificationEntity;
import com.payment.nadi.infrastructure.persistence.entity.UserAccountEntity;
import com.payment.nadi.infrastructure.persistence.repository.SpringDataNotificationRepository;
import com.payment.nadi.infrastructure.persistence.repository.SpringDataUserAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class NotificationService implements NotificationUseCase {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final SpringDataNotificationRepository notificationRepository;
    private final SpringDataUserAccountRepository userRepository;
    private final SmsAlertPort smsAlertPort;
    private final PushNotificationPort pushNotificationPort;

    public NotificationService(
            SpringDataNotificationRepository notificationRepository,
            SpringDataUserAccountRepository userRepository,
            SmsAlertPort smsAlertPort,
            PushNotificationPort pushNotificationPort
    ) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.smsAlertPort = smsAlertPort;
        this.pushNotificationPort = pushNotificationPort;
    }

    private Optional<UserAccountEntity> findUser(String identifier) {
        if (identifier == null || identifier.isBlank()) return Optional.empty();
        String clean = identifier.trim();
        try {
            UUID id = UUID.fromString(clean);
            Optional<UserAccountEntity> byId = userRepository.findById(id);
            if (byId.isPresent()) return byId;
        } catch (IllegalArgumentException ignored) {}

        Optional<UserAccountEntity> byPhone = userRepository.findByPhoneNumber(clean);
        if (byPhone.isPresent()) return byPhone;

        Optional<UserAccountEntity> byAcc = userRepository.findByAccountNumber(clean);
        if (byAcc.isPresent()) return byAcc;

        return userRepository.findByEmail(clean);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<NotificationDto> getUserNotifications(String identifier, int page, int size) {
        int pageIndex = Math.max(0, page);
        int pageSize = size > 0 ? size : 20;
        Pageable pageable = PageRequest.of(pageIndex, pageSize);

        Optional<UserAccountEntity> userOpt = findUser(identifier);
        Page<NotificationEntity> pageResult;

        if (userOpt.isPresent()) {
            UserAccountEntity user = userOpt.get();
            pageResult = notificationRepository.findByUserIdOrderByCreatedAtDesc(user.getId(), pageable);
            if (pageResult.isEmpty() && user.getPhoneNumber() != null) {
                pageResult = notificationRepository.findByPhoneNumberOrderByCreatedAtDesc(user.getPhoneNumber(), pageable);
            }
        } else {
            pageResult = notificationRepository.findByPhoneNumberOrderByCreatedAtDesc(identifier.trim(), pageable);
        }

        List<NotificationDto> dtos = pageResult.getContent().stream()
                .map(com.payment.nadi.infrastructure.persistence.mapper.NotificationPersistenceMapper::toDto)
                .toList();

        return PageResponse.of(dtos, pageResult.getNumber(), pageResult.getSize(), pageResult.getTotalElements());
    }

    @Override
    @Transactional(readOnly = true)
    public long getUnreadCount(String identifier) {
        Optional<UserAccountEntity> userOpt = findUser(identifier);
        if (userOpt.isPresent()) {
            return notificationRepository.countByUserIdAndReadAtIsNull(userOpt.get().getId());
        }
        return notificationRepository.countByPhoneNumberAndReadAtIsNull(identifier.trim());
    }

    @Override
    @Transactional
    public boolean markAsRead(UUID notificationId) {
        Optional<NotificationEntity> notifOpt = notificationRepository.findById(notificationId);
        if (notifOpt.isPresent()) {
            NotificationEntity entity = notifOpt.get();
            entity.setReadAt(Instant.now());
            notificationRepository.save(entity);
            return true;
        }
        return false;
    }

    @Override
    @Transactional
    public int markAllAsRead(String identifier) {
        Instant now = Instant.now();
        Optional<UserAccountEntity> userOpt = findUser(identifier);
        if (userOpt.isPresent()) {
            return notificationRepository.markAllAsReadForUser(userOpt.get().getId(), now);
        }
        return notificationRepository.markAllAsReadForPhone(identifier.trim(), now);
    }

    @Override
    @Transactional
    public boolean registerDevice(DeviceTokenRequest request) {
        UserAccountEntity user = findUser(request.identifier())
                .orElseThrow(() -> new InvalidPaymentException("User account not found: " + request.identifier()));

        user.setPushToken(request.pushToken().trim());
        if (request.pushEnabled() != null) {
            user.setPushNotificationsEnabled(request.pushEnabled());
        }
        if (request.smsEnabled() != null) {
            user.setSmsNotificationsEnabled(request.smsEnabled());
        }
        userRepository.save(user);

        log.info("Registered device push token for user {}", user.getAccountNumber());
        return true;
    }

    @Override
    @Transactional
    public NotificationDto sendNotification(SendNotificationRequest request) {
        NotificationType type = NotificationType.ACCOUNT_STATUS;
        if (request.type() != null) {
            try {
                type = NotificationType.valueOf(request.type().toUpperCase());
            } catch (IllegalArgumentException ignored) {}
        }

        if ("SMS".equalsIgnoreCase(request.channel())) {
            smsAlertPort.sendSms(request.userId(), request.phoneNumber(), request.message(), type, request.referenceId());
        } else {
            pushNotificationPort.sendPush(request.userId(), request.phoneNumber(), request.title(), request.message(),
                    type, request.referenceId(), null);
        }

        return new NotificationDto(
                UUID.randomUUID(),
                request.userId(),
                request.phoneNumber(),
                request.channel().toUpperCase(),
                type.name(),
                request.title(),
                request.message(),
                "DELIVERED",
                request.referenceId(),
                Instant.now(),
                null
        );
    }
}

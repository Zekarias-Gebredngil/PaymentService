package com.payment.nadi.application.dto.response;

import java.time.Instant;
import java.util.UUID;

public record UserAccountDto(
        UUID id,
        String fullName,
        String phoneNumber,
        String email,
        String role,
        String accountNumber,
        UUID customerId,
        UUID sourceAccountId,
        String status,
        Instant createdAt,
        Instant approvedAt,
        String approvedBy,
        Boolean twoFactorEnabled,
        String twoFactorType,
        Boolean biometricEnabled,
        Boolean smsNotificationsEnabled,
        Boolean pushNotificationsEnabled
) {}

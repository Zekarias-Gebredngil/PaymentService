package com.payment.nadi.infrastructure.persistence.mapper;

import com.payment.nadi.application.dto.response.UserAccountDto;
import com.payment.nadi.infrastructure.persistence.entity.UserAccountEntity;

public final class UserAccountPersistenceMapper {

    private UserAccountPersistenceMapper() {}

    public static UserAccountDto toDto(UserAccountEntity e) {
        if (e == null) return null;
        return new UserAccountDto(
                e.getId(),
                e.getFullName(),
                e.getPhoneNumber(),
                e.getEmail(),
                e.getRole(),
                e.getAccountNumber(),
                e.getCustomerId(),
                e.getSourceAccountId(),
                e.getStatus(),
                e.getCreatedAt(),
                e.getApprovedAt(),
                e.getApprovedBy(),
                e.getTwoFactorEnabled(),
                e.getTwoFactorType(),
                e.getBiometricEnabled(),
                e.getSmsNotificationsEnabled(),
                e.getPushNotificationsEnabled()
        );
    }
}

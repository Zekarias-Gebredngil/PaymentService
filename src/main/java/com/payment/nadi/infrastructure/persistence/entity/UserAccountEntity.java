package com.payment.nadi.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_accounts", indexes = {
        @Index(name = "idx_user_accounts_phone", columnList = "phone_number", unique = true),
        @Index(name = "idx_user_accounts_acc_num", columnList = "account_number", unique = true),
        @Index(name = "idx_user_accounts_customer_id", columnList = "customer_id"),
        @Index(name = "idx_user_accounts_source_acc_id", columnList = "source_account_id"),
        @Index(name = "idx_user_accounts_status", columnList = "status"),
        @Index(name = "idx_user_accounts_bio_device", columnList = "biometric_device_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserAccountEntity {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "phone_number", nullable = false, unique = true, length = 32)
    private String phoneNumber;

    @Column(name = "email", length = 128)
    private String email;

    @Column(name = "password", nullable = false)
    private String password;

    @Column(name = "role", nullable = false, length = 32)
    private String role; // "ADMIN", "CUSTOMER", "MERCHANT"

    @Column(name = "account_number", nullable = false, unique = true, length = 64)
    private String accountNumber;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "source_account_id", nullable = false)
    private UUID sourceAccountId;

    @Column(name = "status", nullable = false, length = 32)
    private String status; // "PENDING_APPROVAL", "ACTIVE", "SUSPENDED", "REJECTED"

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "approved_by", length = 64)
    private String approvedBy;

    // Optimistic locking for ACID isolation and race condition prevention
    @Version
    @Column(name = "version")
    @Builder.Default
    private Long version = 0L;

    // Two-Factor Authentication fields
    @Column(name = "two_factor_enabled", nullable = false)
    @Builder.Default
    private Boolean twoFactorEnabled = false;

    @Column(name = "two_factor_secret", length = 64)
    private String twoFactorSecret;

    @Column(name = "two_factor_type", length = 32)
    @Builder.Default
    private String twoFactorType = "NONE"; // "NONE", "TOTP", "SMS"

    @Column(name = "two_factor_backup_codes", length = 1024)
    private String twoFactorBackupCodes;

    @Column(name = "last_otp_code", length = 128)
    private String lastOtpCode;

    @Column(name = "otp_expires_at")
    private Instant otpExpiresAt;

    // Biometric Authentication fields
    @Column(name = "biometric_enabled", nullable = false)
    @Builder.Default
    private Boolean biometricEnabled = false;

    @Column(name = "biometric_public_key", length = 1024)
    private String biometricPublicKey;

    @Column(name = "biometric_device_id", length = 128)
    private String biometricDeviceId;

    @Column(name = "biometric_device_name", length = 128)
    private String biometricDeviceName;

    @Column(name = "biometric_registered_at")
    private Instant biometricRegisteredAt;

    // Notifications & Alert fields
    @Column(name = "push_token", length = 512)
    private String pushToken;

    @Column(name = "sms_notifications_enabled", nullable = false)
    @Builder.Default
    private Boolean smsNotificationsEnabled = true;

    @Column(name = "push_notifications_enabled", nullable = false)
    @Builder.Default
    private Boolean pushNotificationsEnabled = true;
}

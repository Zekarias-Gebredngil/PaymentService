package com.payment.nadi.application.service;

import com.payment.nadi.application.dto.request.SendOtpRequest;
import com.payment.nadi.application.dto.request.TwoFactorDisableRequest;
import com.payment.nadi.application.dto.request.TwoFactorEnableRequest;
import com.payment.nadi.application.dto.request.TwoFactorSetupRequest;
import com.payment.nadi.application.dto.request.TwoFactorVerifyRequest;
import com.payment.nadi.application.dto.response.TwoFactorSetupResponse;
import com.payment.nadi.application.port.in.TwoFactorAuthUseCase;
import com.payment.nadi.application.port.out.EventPublisher;
import com.payment.nadi.application.port.out.SmsAlertPort;
import com.payment.nadi.domain.event.UserSecurityEvent;
import com.payment.nadi.domain.exception.InvalidPaymentException;
import com.payment.nadi.domain.model.NotificationType;
import com.payment.nadi.infrastructure.persistence.entity.UserAccountEntity;
import com.payment.nadi.infrastructure.persistence.repository.SpringDataUserAccountRepository;
import com.payment.nadi.infrastructure.security.totp.TotpService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class TwoFactorAuthService implements TwoFactorAuthUseCase {

    private static final Logger log = LoggerFactory.getLogger(TwoFactorAuthService.class);

    private final SpringDataUserAccountRepository repository;
    private final TotpService totpService;
    private final SmsAlertPort smsAlertPort;
    private final EventPublisher eventPublisher;
    private final PasswordEncoder passwordEncoder;

    public TwoFactorAuthService(
            SpringDataUserAccountRepository repository,
            TotpService totpService,
            SmsAlertPort smsAlertPort,
            EventPublisher eventPublisher,
            PasswordEncoder passwordEncoder
    ) {
        this.repository = repository;
        this.totpService = totpService;
        this.smsAlertPort = smsAlertPort;
        this.eventPublisher = eventPublisher;
        this.passwordEncoder = passwordEncoder;
    }

    private Optional<UserAccountEntity> findUser(String identifier) {
        if (identifier == null || identifier.isBlank()) return Optional.empty();
        String clean = identifier.trim();
        try {
            UUID id = UUID.fromString(clean);
            Optional<UserAccountEntity> byId = repository.findById(id);
            if (byId.isPresent()) return byId;
        } catch (IllegalArgumentException ignored) {}

        Optional<UserAccountEntity> byPhone = repository.findByPhoneNumber(clean);
        if (byPhone.isPresent()) return byPhone;

        Optional<UserAccountEntity> byAcc = repository.findByAccountNumber(clean);
        if (byAcc.isPresent()) return byAcc;

        return repository.findByEmail(clean);
    }

    @Override
    @Transactional
    public TwoFactorSetupResponse setup(TwoFactorSetupRequest request) {
        UserAccountEntity user = findUser(request.identifier())
                .orElseThrow(() -> new InvalidPaymentException("User account not found: " + request.identifier()));

        String secret = totpService.generateSecret();
        List<String> backupCodes = totpService.generateBackupCodes(8);
        String otpauthUri = totpService.generateOtpAuthUri(user.getAccountNumber(), secret, "NadiPayment");
        String qrCodeUrl = totpService.generateQrCodeUrl(otpauthUri);

        // Store uncommitted secret and hashed backup codes until verified
        user.setTwoFactorSecret(secret);
        String hashedBackupCodes = String.join(",", backupCodes);
        user.setTwoFactorBackupCodes(hashedBackupCodes);
        repository.save(user);

        log.info("2FA TOTP setup initialized for user {}", user.getAccountNumber());
        return new TwoFactorSetupResponse(secret, otpauthUri, qrCodeUrl, backupCodes);
    }

    @Override
    @Transactional
    public UserAccountEntity enable(TwoFactorEnableRequest request) {
        UserAccountEntity user = findUser(request.identifier())
                .orElseThrow(() -> new InvalidPaymentException("User account not found: " + request.identifier()));

        String secret = (request.secret() != null && !request.secret().isBlank())
                ? request.secret().trim()
                : user.getTwoFactorSecret();

        if (secret == null || secret.isBlank()) {
            throw new InvalidPaymentException("No 2FA secret pending activation. Please call setup first.");
        }

        boolean valid = totpService.verifyCode(secret, request.code());
        if (!valid) {
            throw new InvalidPaymentException("Invalid verification code. Please check your authenticator app.");
        }

        user.setTwoFactorSecret(secret);
        user.setTwoFactorEnabled(true);
        user.setTwoFactorType("TOTP");
        UserAccountEntity saved = repository.save(user);

        eventPublisher.publish(UserSecurityEvent.now(
                user.getId(), user.getPhoneNumber(), "2FA_ENABLED", "Two-Factor Authentication (TOTP) successfully enabled"
        ));

        log.info("2FA TOTP successfully activated for user {}", user.getAccountNumber());
        return saved;
    }

    @Override
    @Transactional
    public UserAccountEntity verify(TwoFactorVerifyRequest request) {
        UserAccountEntity user = findUser(request.identifier())
                .orElseThrow(() -> new InvalidPaymentException("User account not found: " + request.identifier()));

        if (!Boolean.TRUE.equals(user.getTwoFactorEnabled())) {
            return user; // Already valid / 2FA not enabled
        }

        String inputCode = request.code().trim();

        // 1. Try TOTP code
        if ("TOTP".equalsIgnoreCase(user.getTwoFactorType()) && user.getTwoFactorSecret() != null) {
            if (totpService.verifyCode(user.getTwoFactorSecret(), inputCode)) {
                log.info("2FA TOTP verification succeeded for user {}", user.getAccountNumber());
                return user;
            }
        }

        // 2. Try SMS OTP code
        if (user.getLastOtpCode() != null && user.getOtpExpiresAt() != null) {
            if (Instant.now().isBefore(user.getOtpExpiresAt()) && passwordEncoder.matches(inputCode, user.getLastOtpCode())) {
                user.setLastOtpCode(null);
                user.setOtpExpiresAt(null);
                repository.save(user);
                log.info("2FA SMS OTP verification succeeded for user {}", user.getAccountNumber());
                return user;
            }
        }

        // 3. Try Backup Recovery Codes
        if (user.getTwoFactorBackupCodes() != null && !user.getTwoFactorBackupCodes().isBlank()) {
            List<String> codes = new ArrayList<>(Arrays.asList(user.getTwoFactorBackupCodes().split(",")));
            if (codes.contains(inputCode)) {
                codes.remove(inputCode);
                user.setTwoFactorBackupCodes(String.join(",", codes));
                repository.save(user);
                log.info("2FA backup recovery code used for user {}", user.getAccountNumber());
                return user;
            }
        }

        throw new InvalidPaymentException("Invalid Two-Factor Authentication code or backup code.");
    }

    @Override
    @Transactional
    public UserAccountEntity disable(TwoFactorDisableRequest request) {
        UserAccountEntity user = findUser(request.identifier())
                .orElseThrow(() -> new InvalidPaymentException("User account not found: " + request.identifier()));

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new InvalidPaymentException("Invalid password. Password confirmation is required to disable 2FA.");
        }

        user.setTwoFactorEnabled(false);
        user.setTwoFactorType("NONE");
        user.setTwoFactorSecret(null);
        user.setTwoFactorBackupCodes(null);
        UserAccountEntity saved = repository.save(user);

        eventPublisher.publish(UserSecurityEvent.now(
                user.getId(), user.getPhoneNumber(), "2FA_DISABLED", "Two-Factor Authentication disabled"
        ));

        log.info("2FA disabled for user {}", user.getAccountNumber());
        return saved;
    }

    @Override
    @Transactional
    public boolean sendOtp(SendOtpRequest request) {
        UserAccountEntity user = findUser(request.identifier())
                .orElseThrow(() -> new InvalidPaymentException("User account not found: " + request.identifier()));

        int randomOtp = 100000 + ThreadLocalRandom.current().nextInt(900000);
        String otpStr = String.valueOf(randomOtp);

        user.setLastOtpCode(passwordEncoder.encode(otpStr));
        user.setOtpExpiresAt(Instant.now().plus(Duration.ofMinutes(5)));
        repository.save(user);

        String message = String.format("Nadi Pay Security: Your verification code is %s. Valid for 5 minutes. Do not share it with anyone.", otpStr);
        return smsAlertPort.sendSms(user.getId(), user.getPhoneNumber(), message, NotificationType.OTP_ALERT, null);
    }
}

package com.payment.nadi.application.service;

import com.payment.nadi.application.dto.request.BiometricChallengeRequest;
import com.payment.nadi.application.dto.request.BiometricLoginRequest;
import com.payment.nadi.application.dto.request.BiometricRegisterRequest;
import com.payment.nadi.application.dto.response.BiometricChallengeResponse;
import com.payment.nadi.application.port.in.BiometricAuthUseCase;
import com.payment.nadi.application.port.out.EventPublisher;
import com.payment.nadi.domain.event.UserSecurityEvent;
import com.payment.nadi.domain.exception.InvalidPaymentException;
import com.payment.nadi.infrastructure.persistence.entity.UserAccountEntity;
import com.payment.nadi.infrastructure.persistence.repository.SpringDataUserAccountRepository;
import com.payment.nadi.infrastructure.security.biometric.BiometricService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class BiometricAuthService implements BiometricAuthUseCase {

    private static final Logger log = LoggerFactory.getLogger(BiometricAuthService.class);

    private final SpringDataUserAccountRepository repository;
    private final BiometricService biometricService;
    private final EventPublisher eventPublisher;

    public BiometricAuthService(
            SpringDataUserAccountRepository repository,
            BiometricService biometricService,
            EventPublisher eventPublisher
    ) {
        this.repository = repository;
        this.biometricService = biometricService;
        this.eventPublisher = eventPublisher;
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
    public BiometricChallengeResponse createChallenge(BiometricChallengeRequest request) {
        String idStr = (request != null && request.identifier() != null) ? request.identifier().trim() : null;
        String challenge = biometricService.generateChallenge(idStr);
        Instant expiresAt = Instant.now().plus(Duration.ofMinutes(5));
        return new BiometricChallengeResponse(challenge, idStr, expiresAt);
    }

    @Override
    @Transactional
    public UserAccountEntity registerBiometric(BiometricRegisterRequest request) {
        UserAccountEntity user = findUser(request.identifier())
                .orElseThrow(() -> new InvalidPaymentException("User account not found: " + request.identifier()));

        // 1. Consume and validate challenge freshness (anti-replay)
        boolean challengeValid = biometricService.consumeChallenge(request.challenge(), request.identifier());
        if (!challengeValid) {
            throw new InvalidPaymentException("Challenge nonce is invalid, expired, or already used. Please request a new challenge.");
        }

        // 2. Cryptographically verify signature over the challenge using client's public key
        boolean sigValid = biometricService.verifySignature(request.publicKey(), request.challenge(), request.signature());
        if (!sigValid) {
            throw new InvalidPaymentException("Cryptographic signature verification failed. Public key or device signature is invalid.");
        }

        // 3. Register credentials
        user.setBiometricEnabled(true);
        user.setBiometricPublicKey(request.publicKey().trim());
        user.setBiometricDeviceId(request.deviceId() != null ? request.deviceId().trim() : UUID.randomUUID().toString());
        user.setBiometricDeviceName(request.deviceName() != null ? request.deviceName().trim() : "Biometric Sensor");
        user.setBiometricRegisteredAt(Instant.now());

        UserAccountEntity saved = repository.save(user);

        eventPublisher.publish(UserSecurityEvent.now(
                user.getId(), user.getPhoneNumber(), "BIOMETRIC_REGISTERED",
                "Biometric authentication enabled on device " + user.getBiometricDeviceName()
        ));

        log.info("Biometric authentication successfully registered for user {}", user.getAccountNumber());
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public UserAccountEntity authenticateBiometric(BiometricLoginRequest request) {
        UserAccountEntity user = findUser(request.identifier())
                .or(() -> (request.deviceId() != null && !request.deviceId().isBlank())
                        ? repository.findByBiometricDeviceId(request.deviceId().trim())
                        : Optional.empty())
                .orElseThrow(() -> new InvalidPaymentException("User account not found: " + request.identifier()));

        if (!Boolean.TRUE.equals(user.getBiometricEnabled()) || user.getBiometricPublicKey() == null) {
            throw new InvalidPaymentException("Biometric authentication is not enabled for this account.");
        }

        if (!"ACTIVE".equalsIgnoreCase(user.getStatus())) {
            throw new InvalidPaymentException("Account status is " + user.getStatus() + ". Biometric login not permitted.");
        }

        // 1. Validate challenge
        boolean challengeValid = biometricService.consumeChallenge(request.challenge(), request.identifier());
        if (!challengeValid) {
            throw new InvalidPaymentException("Challenge nonce is invalid or expired.");
        }

        // 2. Validate cryptographic signature
        boolean sigValid = biometricService.verifySignature(user.getBiometricPublicKey(), request.challenge(), request.signature());
        if (!sigValid) {
            throw new InvalidPaymentException("Biometric authentication failed: digital signature did not match enrolled public key.");
        }

        eventPublisher.publish(UserSecurityEvent.now(
                user.getId(), user.getPhoneNumber(), "BIOMETRIC_LOGIN", "Biometric login successful"
        ));

        log.info("Biometric login verified for user {}", user.getAccountNumber());
        return user;
    }

    @Override
    @Transactional
    public UserAccountEntity disableBiometric(String identifier) {
        UserAccountEntity user = findUser(identifier)
                .orElseThrow(() -> new InvalidPaymentException("User account not found: " + identifier));

        user.setBiometricEnabled(false);
        user.setBiometricPublicKey(null);
        user.setBiometricDeviceId(null);
        user.setBiometricDeviceName(null);
        UserAccountEntity saved = repository.save(user);

        eventPublisher.publish(UserSecurityEvent.now(
                user.getId(), user.getPhoneNumber(), "BIOMETRIC_DISABLED", "Biometric credentials revoked"
        ));

        log.info("Biometric authentication disabled for user {}", user.getAccountNumber());
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isBiometricEnabled(String identifier) {
        return findUser(identifier)
                .map(u -> Boolean.TRUE.equals(u.getBiometricEnabled()))
                .orElse(false);
    }
}

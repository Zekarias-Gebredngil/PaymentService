package com.payment.nadi.application.service;

import com.payment.nadi.application.dto.request.BiometricChallengeRequest;
import com.payment.nadi.application.dto.request.BiometricLoginRequest;
import com.payment.nadi.application.dto.request.BiometricRegisterRequest;
import com.payment.nadi.application.dto.response.BiometricChallengeResponse;
import com.payment.nadi.application.dto.response.UserAccountDto;
import com.payment.nadi.application.port.out.EventPublisher;
import com.payment.nadi.infrastructure.persistence.entity.UserAccountEntity;
import com.payment.nadi.infrastructure.persistence.repository.SpringDataUserAccountRepository;
import com.payment.nadi.infrastructure.security.biometric.BiometricService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class BiometricAuthServiceTest {

    private SpringDataUserAccountRepository repository;
    private BiometricService biometricService;
    private EventPublisher eventPublisher;
    private BiometricAuthService service;

    private UserAccountEntity mockUser;
    private KeyPair keyPair;
    private String publicKeyBase64;

    @BeforeEach
    void setUp() throws Exception {
        repository = mock(SpringDataUserAccountRepository.class);
        biometricService = new BiometricService();
        eventPublisher = mock(EventPublisher.class);
        service = new BiometricAuthService(repository, biometricService, eventPublisher);

        KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
        kpg.initialize(256);
        keyPair = kpg.generateKeyPair();
        publicKeyBase64 = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());

        mockUser = UserAccountEntity.builder()
                .id(UUID.randomUUID())
                .fullName("Sara Tesfaye")
                .phoneNumber("+251922334455")
                .accountNumber("00789123")
                .status("ACTIVE")
                .biometricEnabled(false)
                .createdAt(Instant.now())
                .build();

        when(repository.findByPhoneNumber("+251922334455")).thenReturn(Optional.of(mockUser));
        when(repository.findByAccountNumber("00789123")).thenReturn(Optional.of(mockUser));
        when(repository.save(any(UserAccountEntity.class))).thenAnswer(i -> i.getArgument(0));
    }

    private String signChallenge(String challenge) throws Exception {
        Signature signer = Signature.getInstance("SHA256withECDSA");
        signer.initSign(keyPair.getPrivate());
        signer.update(challenge.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(signer.sign());
    }

    @Test
    @DisplayName("Should generate biometric challenge nonce")
    void shouldCreateChallenge() {
        BiometricChallengeResponse response = service.createChallenge(new BiometricChallengeRequest("+251922334455"));
        assertNotNull(response);
        assertNotNull(response.challenge());
        assertEquals("+251922334455", response.identifier());
    }

    @Test
    @DisplayName("Should register biometric credentials with valid cryptographic proof")
    void shouldRegisterBiometricCredentials() throws Exception {
        BiometricChallengeResponse challengeResp = service.createChallenge(new BiometricChallengeRequest("+251922334455"));
        String signature = signChallenge(challengeResp.challenge());

        BiometricRegisterRequest regReq = new BiometricRegisterRequest(
                "+251922334455",
                challengeResp.challenge(),
                publicKeyBase64,
                signature,
                "DEV-12345",
                "iPhone 15 Pro"
        );

        UserAccountDto registered = service.registerBiometric(regReq);
        assertTrue(registered.biometricEnabled());
        verify(eventPublisher).publish(any());
    }

    @Test
    @DisplayName("Should authenticate user via biometric signature")
    void shouldAuthenticateViaBiometrics() throws Exception {
        mockUser.setBiometricEnabled(true);
        mockUser.setBiometricPublicKey(publicKeyBase64);

        BiometricChallengeResponse challengeResp = service.createChallenge(new BiometricChallengeRequest("+251922334455"));
        String signature = signChallenge(challengeResp.challenge());

        BiometricLoginRequest loginReq = new BiometricLoginRequest(
                "+251922334455",
                challengeResp.challenge(),
                signature,
                "DEV-12345"
        );

        UserAccountDto loggedIn = service.authenticateBiometric(loginReq);
        assertNotNull(loggedIn);
        assertEquals(mockUser.getId(), loggedIn.id());
        verify(eventPublisher).publish(any());
    }
}

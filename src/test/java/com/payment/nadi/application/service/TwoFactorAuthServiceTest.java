package com.payment.nadi.application.service;

import com.payment.nadi.application.dto.request.TwoFactorDisableRequest;
import com.payment.nadi.application.dto.request.TwoFactorEnableRequest;
import com.payment.nadi.application.dto.request.TwoFactorSetupRequest;
import com.payment.nadi.application.dto.request.TwoFactorVerifyRequest;
import com.payment.nadi.application.dto.response.TwoFactorSetupResponse;
import com.payment.nadi.application.port.out.EventPublisher;
import com.payment.nadi.application.port.out.SmsAlertPort;
import com.payment.nadi.infrastructure.persistence.entity.UserAccountEntity;
import com.payment.nadi.infrastructure.persistence.repository.SpringDataUserAccountRepository;
import com.payment.nadi.infrastructure.security.totp.TotpService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TwoFactorAuthServiceTest {

    private SpringDataUserAccountRepository repository;
    private TotpService totpService;
    private SmsAlertPort smsAlertPort;
    private EventPublisher eventPublisher;
    private PasswordEncoder passwordEncoder;
    private TwoFactorAuthService service;

    private UserAccountEntity mockUser;

    @BeforeEach
    void setUp() {
        repository = mock(SpringDataUserAccountRepository.class);
        totpService = new TotpService();
        smsAlertPort = mock(SmsAlertPort.class);
        eventPublisher = mock(EventPublisher.class);
        passwordEncoder = mock(PasswordEncoder.class);

        service = new TwoFactorAuthService(repository, totpService, smsAlertPort, eventPublisher, passwordEncoder);

        mockUser = UserAccountEntity.builder()
                .id(UUID.randomUUID())
                .fullName("Abebe Kebede")
                .phoneNumber("+251911223344")
                .accountNumber("00564829")
                .password("hashed_pwd")
                .role("CUSTOMER")
                .status("ACTIVE")
                .twoFactorEnabled(false)
                .twoFactorType("NONE")
                .createdAt(Instant.now())
                .build();

        when(repository.findByPhoneNumber("+251911223344")).thenReturn(Optional.of(mockUser));
        when(repository.findByAccountNumber("00564829")).thenReturn(Optional.of(mockUser));
        when(repository.save(any(UserAccountEntity.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    @DisplayName("Should successfully setup 2FA TOTP returning secret and backup codes")
    void shouldSetupTwoFactor() {
        TwoFactorSetupResponse response = service.setup(new TwoFactorSetupRequest("+251911223344"));

        assertNotNull(response);
        assertNotNull(response.secret());
        assertEquals(32, response.secret().length());
        assertNotNull(response.otpauthUri());
        assertNotNull(response.qrCodeUrl());
        assertEquals(8, response.backupCodes().size());

        verify(repository).save(mockUser);
        assertNotNull(mockUser.getTwoFactorSecret());
    }

    @Test
    @DisplayName("Should enable 2FA TOTP when valid code is submitted")
    void shouldEnableTwoFactor() {
        String secret = totpService.generateSecret();
        mockUser.setTwoFactorSecret(secret);
        long currentWindow = System.currentTimeMillis() / 1000 / 30;
        String validCode = totpService.generateCodeForWindow(secret, currentWindow);

        UserAccountEntity enabledUser = service.enable(new TwoFactorEnableRequest("+251911223344", validCode, secret));

        assertTrue(enabledUser.getTwoFactorEnabled());
        assertEquals("TOTP", enabledUser.getTwoFactorType());
        verify(eventPublisher).publish(any());
    }

    @Test
    @DisplayName("Should verify valid 2FA code during step-up login")
    void shouldVerifyTwoFactorCode() {
        String secret = totpService.generateSecret();
        mockUser.setTwoFactorSecret(secret);
        mockUser.setTwoFactorEnabled(true);
        mockUser.setTwoFactorType("TOTP");

        long currentWindow = System.currentTimeMillis() / 1000 / 30;
        String validCode = totpService.generateCodeForWindow(secret, currentWindow);

        UserAccountEntity verified = service.verify(new TwoFactorVerifyRequest("+251911223344", validCode));
        assertNotNull(verified);
        assertEquals(mockUser.getId(), verified.getId());
    }

    @Test
    @DisplayName("Should disable 2FA when valid password is confirmed")
    void shouldDisableTwoFactor() {
        mockUser.setTwoFactorEnabled(true);
        mockUser.setTwoFactorType("TOTP");
        mockUser.setTwoFactorSecret("SOME_SECRET");

        when(passwordEncoder.matches("myPassword123", "hashed_pwd")).thenReturn(true);

        UserAccountEntity disabled = service.disable(new TwoFactorDisableRequest("+251911223344", "myPassword123", null));

        assertFalse(disabled.getTwoFactorEnabled());
        assertEquals("NONE", disabled.getTwoFactorType());
        assertNull(disabled.getTwoFactorSecret());
        verify(eventPublisher).publish(any());
    }
}

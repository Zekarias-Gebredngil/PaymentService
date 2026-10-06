package com.payment.nadi.infrastructure.security.totp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TotpServiceTest {

    private TotpService totpService;

    @BeforeEach
    void setUp() {
        totpService = new TotpService();
    }

    @Test
    @DisplayName("Should generate valid Base32 secret key of 32 characters")
    void shouldGenerateValidBase32Secret() {
        String secret = totpService.generateSecret();
        assertNotNull(secret);
        assertEquals(32, secret.length());
        assertTrue(secret.matches("^[A-Z2-7]{32}$"), "Secret must only contain RFC 4648 Base32 characters");
    }

    @Test
    @DisplayName("Should generate valid otpauth URI with standard formatting")
    void shouldGenerateValidOtpAuthUri() {
        String secret = "JBSWY3DPEHPK3PXP";
        String uri = totpService.generateOtpAuthUri("00564829", secret, "NadiPayment");
        assertNotNull(uri);
        assertTrue(uri.startsWith("otpauth://totp/"));
        assertTrue(uri.contains("secret=" + secret));
        assertTrue(uri.contains("digits=6"));
        assertTrue(uri.contains("period=30"));
    }

    @Test
    @DisplayName("Should generate and verify 6-digit TOTP code for current time window")
    void shouldGenerateAndVerifyCode() {
        String secret = totpService.generateSecret();
        long currentWindow = System.currentTimeMillis() / 1000 / 30;

        String code = totpService.generateCodeForWindow(secret, currentWindow);
        assertNotNull(code);
        assertEquals(6, code.length());
        assertTrue(code.matches("^\\d{6}$"), "Code must be exactly 6 numeric digits");

        boolean verified = totpService.verifyCode(secret, code);
        assertTrue(verified, "Current window code must be accepted");
    }

    @Test
    @DisplayName("Should accept code with clock drift of +/- 1 window (30 seconds)")
    void shouldAcceptCodeWithClockDrift() {
        String secret = totpService.generateSecret();
        long currentWindow = System.currentTimeMillis() / 1000 / 30;

        String pastCode = totpService.generateCodeForWindow(secret, currentWindow - 1);
        assertTrue(totpService.verifyCode(secret, pastCode), "Past window (-1) code must be accepted for clock drift");

        String futureCode = totpService.generateCodeForWindow(secret, currentWindow + 1);
        assertTrue(totpService.verifyCode(secret, futureCode), "Future window (+1) code must be accepted for clock drift");

        String farPastCode = totpService.generateCodeForWindow(secret, currentWindow - 3);
        assertFalse(totpService.verifyCode(secret, farPastCode), "Out-of-range window code must be rejected");
    }

    @Test
    @DisplayName("Should reject invalid or altered code")
    void shouldRejectInvalidCode() {
        String secret = totpService.generateSecret();
        assertFalse(totpService.verifyCode(secret, "000000"));
        assertFalse(totpService.verifyCode(secret, "999999"));
        assertFalse(totpService.verifyCode(secret, null));
        assertFalse(totpService.verifyCode(secret, "123"));
    }

    @Test
    @DisplayName("Should generate specified count of single-use backup recovery codes")
    void shouldGenerateBackupCodes() {
        List<String> codes = totpService.generateBackupCodes(8);
        assertNotNull(codes);
        assertEquals(8, codes.size());
        for (String c : codes) {
            assertEquals(9, c.length()); // 4 chars + '-' + 4 chars
            assertTrue(c.contains("-"));
        }
    }
}

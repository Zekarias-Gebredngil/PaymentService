package com.payment.nadi.infrastructure.provider.telebirr;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TelebirrCryptoServiceTest {

    private TelebirrCryptoService cryptoService;

    @BeforeEach
    void setUp() {
        cryptoService = new TelebirrCryptoService();
    }

    @Test
    @DisplayName("Should sort parameters alphabetically excluding 'sign' and null values")
    void shouldBuildSortedSignString() {
        Map<String, Object> params = new HashMap<>();
        params.put("totalAmount", "100.00");
        params.put("appId", "APP123");
        params.put("shortCode", "10011");
        params.put("sign", "SHOULD_BE_EXCLUDED");
        params.put("emptyField", "");
        params.put("nullField", null);

        String signString = cryptoService.buildSignString(params);

        assertEquals("appId=APP123&shortCode=10011&totalAmount=100.00", signString);
    }

    @Test
    @DisplayName("Should generate dev signature and verify when test key is provided")
    void shouldSignAndVerifyInDevMode() {
        String data = "appId=APP123&shortCode=10011&totalAmount=100.00";
        String sign = cryptoService.sign(data, "mock_test_key");

        assertNotNull(sign);
        assertFalse(sign.isBlank());

        boolean verified = cryptoService.verify(data, sign, "mock_public_key");
        assertTrue(verified);
    }

    @Test
    @DisplayName("Should reject verification on empty or null signature")
    void shouldRejectEmptySignature() {
        assertFalse(cryptoService.verify("data", null, "key"));
        assertFalse(cryptoService.verify("data", "", "key"));
    }
}

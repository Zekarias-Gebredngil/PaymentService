package com.payment.nadi.infrastructure.configuration;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "payment")
public class PaymentProperties {

    private String defaultCurrency = "ETB";
    private IdempotencyProperties idempotency = new IdempotencyProperties();
    private ProvidersProperties providers = new ProvidersProperties();

    @Getter
    @Setter
    public static class IdempotencyProperties {
        private long ttlMinutes = 1440; // 24 hours default
    }

    @Getter
    @Setter
    public static class ProvidersProperties {
        private TelebirrProperties telebirr = new TelebirrProperties();
    }

    @Getter
    @Setter
    public static class TelebirrProperties {
        private boolean enabled = true;
        private String baseUrl = "https://app.ethiomobilemoney.et:2121";
        private String appId;
        private String appKey;
        private String shortCode;
        private String publicKey;
        private String privateKey;
        private String callbackUrl;
        private String returnUrl;
        private int timeoutSeconds = 15;
    }
}

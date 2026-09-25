package com.payment.nadi.infrastructure.provider.telebirr.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Telebirr C2B Order creation request payload.
 */
public record TelebirrC2BOrderRequest(
        @JsonProperty("appId") String appId,
        @JsonProperty("notifyUrl") String notifyUrl,
        @JsonProperty("outTradeNo") String outTradeNo,
        @JsonProperty("receiveName") String receiveName,
        @JsonProperty("returnUrl") String returnUrl,
        @JsonProperty("shortCode") String shortCode,
        @JsonProperty("subject") String subject,
        @JsonProperty("timeoutExpress") String timeoutExpress,
        @JsonProperty("totalAmount") String totalAmount,
        @JsonProperty("nonce") String nonce,
        @JsonProperty("timestamp") String timestamp,
        @JsonProperty("sign") String sign
) {}

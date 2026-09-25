package com.payment.nadi.infrastructure.provider.telebirr.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response from Telebirr C2B order creation API.
 */
public record TelebirrC2BOrderResponse(
        @JsonProperty("code") int code,
        @JsonProperty("msg") String msg,
        @JsonProperty("data") TelebirrOrderData data
) {
    public record TelebirrOrderData(
            @JsonProperty("toPayUrl") String toPayUrl,
            @JsonProperty("prepayId") String prepayId,
            @JsonProperty("outTradeNo") String outTradeNo
    ) {}

    public boolean isSuccessful() {
        return code == 200 || code == 0;
    }
}

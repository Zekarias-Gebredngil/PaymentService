package com.payment.nadi.infrastructure.provider.telebirr.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TelebirrB2CDisbursementResponse(
        @JsonProperty("code") int code,
        @JsonProperty("msg") String msg,
        @JsonProperty("data") TelebirrB2CData data
) {
    public record TelebirrB2CData(
            @JsonProperty("outTradeNo") String outTradeNo,
            @JsonProperty("transactionNo") String transactionNo,
            @JsonProperty("status") String status
    ) {}

    public boolean isSuccessful() {
        return code == 200 || code == 0;
    }
}

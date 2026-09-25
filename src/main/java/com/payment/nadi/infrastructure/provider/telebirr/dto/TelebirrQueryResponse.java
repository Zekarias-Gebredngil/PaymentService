package com.payment.nadi.infrastructure.provider.telebirr.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TelebirrQueryResponse(
        @JsonProperty("code") int code,
        @JsonProperty("msg") String msg,
        @JsonProperty("data") TelebirrQueryData data
) {
    public record TelebirrQueryData(
            @JsonProperty("outTradeNo") String outTradeNo,
            @JsonProperty("tradeNo") String tradeNo,
            @JsonProperty("tradeStatus") String tradeStatus,
            @JsonProperty("totalAmount") String totalAmount
    ) {}

    public boolean isSuccessful() {
        return code == 200 || code == 0;
    }
}

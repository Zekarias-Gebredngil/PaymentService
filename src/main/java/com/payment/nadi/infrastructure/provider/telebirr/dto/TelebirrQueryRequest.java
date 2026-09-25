package com.payment.nadi.infrastructure.provider.telebirr.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TelebirrQueryRequest(
        @JsonProperty("appId") String appId,
        @JsonProperty("outTradeNo") String outTradeNo,
        @JsonProperty("timestamp") String timestamp,
        @JsonProperty("sign") String sign
) {}

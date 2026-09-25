package com.payment.nadi.infrastructure.provider.telebirr.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.HashMap;
import java.util.Map;

/**
 * Payload sent by Telebirr callback webhook when transaction status updates.
 */
public record TelebirrCallbackPayload(
        @JsonProperty("outTradeNo") String outTradeNo,
        @JsonProperty("totalAmount") String totalAmount,
        @JsonProperty("tradeDate") String tradeDate,
        @JsonProperty("tradeNo") String tradeNo,
        @JsonProperty("tradeStatus") String tradeStatus,
        @JsonProperty("msisdn") String msisdn,
        @JsonProperty("sign") String sign
) {
    public Map<String, Object> toSignMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("outTradeNo", outTradeNo);
        map.put("totalAmount", totalAmount);
        map.put("tradeDate", tradeDate);
        map.put("tradeNo", tradeNo);
        map.put("tradeStatus", tradeStatus);
        map.put("msisdn", msisdn);
        return map;
    }

    public boolean isPaymentSuccessful() {
        if (tradeStatus == null) return false;
        return tradeStatus.equalsIgnoreCase("Completed")
                || tradeStatus.equalsIgnoreCase("SUCCESS")
                || tradeStatus.equalsIgnoreCase("2")
                || tradeStatus.equalsIgnoreCase("SUCCESSFUL");
    }
}

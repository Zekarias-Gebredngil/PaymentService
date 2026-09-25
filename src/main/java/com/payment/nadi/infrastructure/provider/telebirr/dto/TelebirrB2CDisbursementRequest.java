package com.payment.nadi.infrastructure.provider.telebirr.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Telebirr B2C / Disbursement request (used for customer withdrawals to mobile number).
 */
public record TelebirrB2CDisbursementRequest(
        @JsonProperty("appId") String appId,
        @JsonProperty("outTradeNo") String outTradeNo,
        @JsonProperty("receiverMsisdn") String receiverMsisdn,
        @JsonProperty("amount") String amount,
        @JsonProperty("shortCode") String shortCode,
        @JsonProperty("remark") String remark,
        @JsonProperty("timestamp") String timestamp,
        @JsonProperty("sign") String sign
) {}

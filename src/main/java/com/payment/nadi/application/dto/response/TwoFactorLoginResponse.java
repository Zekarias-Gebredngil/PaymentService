package com.payment.nadi.application.dto.response;

public record TwoFactorLoginResponse(
        boolean requires2Fa,
        String twoFactorType,
        String tempToken,
        Object userAccount
) {}

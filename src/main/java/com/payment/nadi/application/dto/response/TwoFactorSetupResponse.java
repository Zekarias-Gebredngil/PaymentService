package com.payment.nadi.application.dto.response;

import java.util.List;

public record TwoFactorSetupResponse(
        String secret,
        String otpauthUri,
        String qrCodeUrl,
        List<String> backupCodes
) {}

package com.payment.nadi.application.dto.request;

import jakarta.validation.constraints.NotBlank;

public record BiometricLoginRequest(
        @NotBlank(message = "Identifier (phone or account number) is required")
        String identifier,

        @NotBlank(message = "Challenge nonce is required")
        String challenge,

        @NotBlank(message = "Cryptographic signature is required")
        String signature,

        String deviceId
) {}

package com.payment.nadi.application.dto.response;

import java.time.Instant;

public record BiometricChallengeResponse(
        String challenge,
        String identifier,
        Instant expiresAt
) {}

package com.payment.nadi.application.dto.request;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "Identifier (phone or account number) is required") String identifier,
        @NotBlank(message = "Password is required") String password
) {}

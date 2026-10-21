package com.payment.nadi.application.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record ChangePasswordRequest(
        UUID userId,
        String identifier,
        @NotBlank(message = "Current password is required") String oldPassword,
        @NotBlank(message = "New password is required") @Size(min = 6, message = "New password must be at least 6 characters") String newPassword
) {}

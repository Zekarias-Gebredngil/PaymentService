package com.payment.nadi.application.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminCreateAccountRequest(
        @NotBlank(message = "Full name is required") String fullName,
        @NotBlank(message = "Phone number is required") String phoneNumber,
        String email,
        @NotBlank(message = "Password is required") @Size(min = 6, message = "Password must be at least 6 characters") String password,
        String role,
        Boolean autoActivate
) {}

package com.payment.nadi.application.dto.request;

import com.payment.nadi.domain.model.PaymentProviderType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PaymentMethodDto(
        @NotNull(message = "Payment method type is required")
        PaymentProviderType type,

        @NotBlank(message = "Identifier (e.g. phone number or account number) is required")
        String identifier
) {}

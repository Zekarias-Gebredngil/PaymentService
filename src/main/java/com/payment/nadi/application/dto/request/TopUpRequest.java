package com.payment.nadi.application.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

public record TopUpRequest(
        @NotNull(message = "Amount is required")
        @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
        BigDecimal amount,

        @NotBlank(message = "Currency is required")
        String currency,

        @NotNull(message = "Customer ID is required")
        UUID customerId,

        UUID destinationAccountId,

        @NotNull(message = "Payment method is required")
        @Valid
        PaymentMethodDto paymentMethod,

        String sourceSystem,

        String description,

        String returnUrl,

        Map<String, String> metadata
) {}

package com.payment.nadi.interfaces.rest;

import com.payment.nadi.application.dto.request.TopUpRequest;
import com.payment.nadi.application.dto.response.PaymentResponse;
import com.payment.nadi.application.port.in.TopUpUseCase;
import com.payment.nadi.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments/topups")
@Tag(name = "Top-Up Operations", description = "Inbound funds collection (C2B / Pay-In) via mobile money providers like Telebirr")
public class TopUpController {

    private final TopUpUseCase topUpUseCase;

    public TopUpController(TopUpUseCase topUpUseCase) {
        this.topUpUseCase = topUpUseCase;
    }

    @PostMapping
    @Operation(
            summary = "Initiate a Wallet Top-Up",
            description = "Creates a top-up transaction and returns a checkout/redirect URL or USSD prompt identifier. Protected by two-phase idempotency."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "202",
                    description = "Top-up initiated successfully and pending provider settlement",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Invalid request payload or currency mismatch"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "Concurrent idempotent request already in progress"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "502",
                    description = "Upstream payment provider communication failure"
            )
    })
    public ResponseEntity<ApiResponse<PaymentResponse>> initiateTopUp(
            @Valid @RequestBody TopUpRequest request,
            @Parameter(description = "Unique UUID or client key guaranteeing at-most-once execution", example = "topup-usr123-20260925-001")
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey
    ) {
        PaymentResponse response = topUpUseCase.initiateTopUp(request, idempotencyKey);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.ok("Top-up initiated successfully", response));
    }
}

package com.payment.nadi.interfaces.rest;

import com.payment.nadi.application.dto.request.WithdrawRequest;
import com.payment.nadi.application.dto.response.PaymentResponse;
import com.payment.nadi.application.port.in.WithdrawUseCase;
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
@RequestMapping("/api/v1/payments/withdrawals")
@Tag(name = "Withdrawal Operations", description = "Outbound disbursements (B2C / Payout) from user wallets to mobile money accounts")
public class WithdrawController {

    private final WithdrawUseCase withdrawUseCase;

    public WithdrawController(WithdrawUseCase withdrawUseCase) {
        this.withdrawUseCase = withdrawUseCase;
    }

    @PostMapping
    @Operation(
            summary = "Initiate a Withdrawal / Cash-Out",
            description = "Validates available wallet balance, places a debit hold, and dispatches a B2C disbursement to the recipient provider wallet. Automatically reverts funds upon provider rejection."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "202",
                    description = "Withdrawal initiated or settled successfully",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Invalid payload or insufficient account balance"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "Concurrent idempotent request already in progress"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "502",
                    description = "Provider disbursement communication failure"
            )
    })
    public ResponseEntity<ApiResponse<PaymentResponse>> initiateWithdrawal(
            @Valid @RequestBody WithdrawRequest request,
            @Parameter(description = "Unique UUID or client key guaranteeing at-most-once execution", example = "wth-usr123-20260925-001")
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey
    ) {
        PaymentResponse response = withdrawUseCase.initiateWithdraw(request, idempotencyKey);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.ok("Withdrawal processed successfully", response));
    }
}

package com.payment.nadi.interfaces.rest;

import com.payment.nadi.application.dto.request.TransferRequest;
import com.payment.nadi.application.dto.response.PaymentResponse;
import com.payment.nadi.application.port.in.TransferUseCase;
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
@RequestMapping("/api/v1/payments/transfers")
@Tag(name = "Transfer Operations", description = "Peer-to-peer (P2P) atomic account transfers within the internal ledger")
public class TransferController {

    private final TransferUseCase transferUseCase;

    public TransferController(TransferUseCase transferUseCase) {
        this.transferUseCase = transferUseCase;
    }

    @PostMapping
    @Operation(
            summary = "Initiate an Internal Account Transfer",
            description = "Transfers monetary value between two distinct accounts atomically. Posts balanced debit and credit entries to the ledger."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Transfer settled successfully",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Invalid accounts or source equals destination account"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "Concurrent idempotent request already in progress"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "422",
                    description = "Insufficient funds in source account"
            )
    })
    public ResponseEntity<ApiResponse<PaymentResponse>> initiateTransfer(
            @Valid @RequestBody TransferRequest request,
            @Parameter(description = "Unique UUID or client key guaranteeing at-most-once execution", example = "tx-usr123-20260925-001")
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey
    ) {
        PaymentResponse response = transferUseCase.initiateTransfer(request, idempotencyKey);
        return ResponseEntity.status(HttpStatus.OK)
                .body(ApiResponse.ok("Transfer completed successfully", response));
    }
}

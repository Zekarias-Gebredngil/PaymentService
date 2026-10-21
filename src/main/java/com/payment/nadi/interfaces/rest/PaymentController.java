package com.payment.nadi.interfaces.rest;

import com.payment.nadi.application.dto.response.PageResponse;
import com.payment.nadi.application.dto.response.PaymentResponse;
import com.payment.nadi.application.dto.response.TransactionResponse;
import com.payment.nadi.application.port.in.PaymentQueryUseCase;
import com.payment.nadi.common.response.ApiResponse;
import com.payment.nadi.domain.model.Money;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
@Tag(name = "Payment Queries & Status", description = "Query payment details, transaction ledger items, and refresh provider statuses")
public class PaymentController {

    private final PaymentQueryUseCase paymentQueryUseCase;

    public PaymentController(PaymentQueryUseCase paymentQueryUseCase) {
        this.paymentQueryUseCase = paymentQueryUseCase;
    }

    @GetMapping("/accounts/{accountId}/balance")
    @Operation(summary = "Get Real-Time Database Balance", description = "Calculates and returns the live double-entry ledger balance directly from PostgreSQL database.")
    public ResponseEntity<ApiResponse<Money>> getAccountBalance(
            @Parameter(description = "Account UUID")
            @PathVariable UUID accountId,
            @RequestParam(defaultValue = "ETB") String currency
    ) {
        Money balance = paymentQueryUseCase.getAccountBalance(accountId, currency);
        return ResponseEntity.ok(ApiResponse.ok(balance));
    }

    @GetMapping("/{paymentId}")
    @Operation(summary = "Get Payment by ID", description = "Retrieves current payment status and financial metadata by primary UUID.")
    public ResponseEntity<ApiResponse<PaymentResponse>> getPaymentById(
            @Parameter(description = "Primary UUID of the payment record", example = "e4a2f8b1-38f1-460d-9b7e-908bc43d1a8e")
            @PathVariable UUID paymentId
    ) {
        PaymentResponse response = paymentQueryUseCase.getPaymentById(paymentId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping("/reference/{reference}")
    @Operation(summary = "Get Payment by Reference", description = "Retrieves payment details by unique tracking reference.")
    public ResponseEntity<ApiResponse<PaymentResponse>> getPaymentByReference(
            @Parameter(description = "Payment reference code", example = "TOP-20260925-9B7E90")
            @PathVariable String reference
    ) {
        PaymentResponse response = paymentQueryUseCase.getPaymentByReference(reference);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping
    @Operation(summary = "List Customer Payments", description = "Returns a paginated list of payment transactions from the database for a given customer ID.")
    public ResponseEntity<ApiResponse<PageResponse<PaymentResponse>>> getCustomerPayments(
            @Parameter(description = "Customer UUID filter", required = true)
            @RequestParam UUID customerId,
            @Parameter(description = "Zero-indexed page number")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Items per page (max 100)")
            @RequestParam(defaultValue = "20") int size
    ) {
        PageResponse<PaymentResponse> response = paymentQueryUseCase.getCustomerPayments(customerId, page, size);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping("/{paymentId}/transactions")
    @Operation(summary = "Get Payment Ledger Transactions", description = "Retrieves all financial ledger bookings and fees associated with a payment.")
    public ResponseEntity<ApiResponse<List<TransactionResponse>>> getPaymentTransactions(
            @Parameter(description = "Payment UUID")
            @PathVariable UUID paymentId
    ) {
        List<TransactionResponse> transactions = paymentQueryUseCase.getPaymentTransactions(paymentId);
        return ResponseEntity.ok(ApiResponse.ok(transactions));
    }

    @PostMapping("/{paymentId}/refresh")
    @Operation(summary = "Refresh Payment Status from Gateway", description = "Directly queries the external provider and updates payment status if changed.")
    public ResponseEntity<ApiResponse<PaymentResponse>> refreshPaymentStatus(
            @Parameter(description = "Payment UUID to synchronize")
            @PathVariable UUID paymentId
    ) {
        PaymentResponse response = paymentQueryUseCase.refreshPaymentStatus(paymentId);
        return ResponseEntity.ok(ApiResponse.ok("Status refreshed", response));
    }
}
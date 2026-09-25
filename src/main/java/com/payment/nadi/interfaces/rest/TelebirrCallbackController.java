package com.payment.nadi.interfaces.rest;

import com.payment.nadi.application.port.in.PaymentCallbackUseCase;
import com.payment.nadi.domain.model.Money;
import com.payment.nadi.domain.model.PaymentProviderType;
import com.payment.nadi.domain.model.PaymentStatus;
import com.payment.nadi.infrastructure.configuration.PaymentProperties;
import com.payment.nadi.infrastructure.provider.telebirr.TelebirrCryptoService;
import com.payment.nadi.infrastructure.provider.telebirr.dto.TelebirrCallbackPayload;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/payment-providers/telebirr")
@Tag(name = "Payment Provider Webhooks", description = "Asynchronous webhook callbacks from payment gateways (Telebirr C2B/B2C)")
public class TelebirrCallbackController {

    private static final Logger log = LoggerFactory.getLogger(TelebirrCallbackController.class);

    private final PaymentCallbackUseCase callbackUseCase;
    private final TelebirrCryptoService cryptoService;
    private final PaymentProperties paymentProperties;

    public TelebirrCallbackController(
            PaymentCallbackUseCase callbackUseCase,
            TelebirrCryptoService cryptoService,
            PaymentProperties paymentProperties
    ) {
        this.callbackUseCase = callbackUseCase;
        this.cryptoService = cryptoService;
        this.paymentProperties = paymentProperties;
    }

    @PostMapping("/callback")
    @Operation(
            summary = "Telebirr Asynchronous Payment Callback",
            description = "Receives signed JSON webhook from Ethio Telecom Telebirr gateway after customer enters PIN or order clears. Cryptographically verifies RSA-SHA256 signature before settling transaction."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Callback accepted and processed successfully",
                    content = @Content(schema = @Schema(example = "{\"code\": 0, \"message\": \"success\"}"))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Invalid signature, unknown reference, or amount mismatch",
                    content = @Content(schema = @Schema(example = "{\"code\": 1, \"message\": \"Invalid signature\"}"))
            )
    })
    public ResponseEntity<Map<String, Object>> handleTelebirrCallback(@RequestBody TelebirrCallbackPayload payload) {
        log.info("Received Telebirr callback for tradeNo: {}, outTradeNo: {}, tradeStatus: {}",
                payload.tradeNo(), payload.outTradeNo(), payload.tradeStatus());

        // 1. Verify Telebirr signature
        String signData = cryptoService.buildSignString(payload.toSignMap());
        String publicKey = paymentProperties.getProviders().getTelebirr().getPublicKey();
        boolean verified = cryptoService.verify(signData, payload.sign(), publicKey);

        // 2. Parse reported amount
        Money reportedAmount = null;
        if (payload.totalAmount() != null) {
            try {
                reportedAmount = Money.of(new BigDecimal(payload.totalAmount()), "ETB");
            } catch (Exception ex) {
                log.warn("Could not parse reported amount in Telebirr callback: {}", payload.totalAmount());
            }
        }

        PaymentStatus reportedStatus = payload.isPaymentSuccessful() ? PaymentStatus.SUCCESS : PaymentStatus.FAILED;

        // 3. Process idempotent callback
        PaymentCallbackUseCase.CallbackResult result = callbackUseCase.processCallback(
                PaymentProviderType.TELEBIRR,
                payload.outTradeNo(),
                payload.tradeNo(),
                reportedStatus,
                reportedAmount,
                payload.toString(),
                verified
        );

        if (result.accepted()) {
            return ResponseEntity.ok(Map.of(
                    "code", 0,
                    "message", "success"
            ));
        } else {
            return ResponseEntity.badRequest().body(Map.of(
                    "code", 1,
                    "message", result.message()
            ));
        }
    }
}

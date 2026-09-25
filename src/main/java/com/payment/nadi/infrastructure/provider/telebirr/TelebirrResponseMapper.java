package com.payment.nadi.infrastructure.provider.telebirr;

import com.payment.nadi.application.port.out.dto.PaymentInitiationResult;
import com.payment.nadi.application.port.out.dto.PaymentStatusResult;
import com.payment.nadi.domain.model.Money;
import com.payment.nadi.domain.model.PaymentStatus;
import com.payment.nadi.infrastructure.provider.telebirr.dto.TelebirrB2CDisbursementResponse;
import com.payment.nadi.infrastructure.provider.telebirr.dto.TelebirrC2BOrderResponse;
import com.payment.nadi.infrastructure.provider.telebirr.dto.TelebirrQueryResponse;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

@Component
public class TelebirrResponseMapper {

    public PaymentInitiationResult toInitiationResult(TelebirrC2BOrderResponse response) {
        if (response == null) {
            return PaymentInitiationResult.failed("TELEBIRR_NULL_RESPONSE", "Null response from Telebirr gateway", null);
        }

        if (response.isSuccessful() && response.data() != null) {
            return new PaymentInitiationResult(
                    true,
                    PaymentStatus.PENDING,
                    response.data().prepayId(),
                    response.data().toPayUrl(),
                    response.toString(),
                    null,
                    null,
                    Map.of(
                            "prepayId", response.data().prepayId() != null ? response.data().prepayId() : "",
                            "toPayUrl", response.data().toPayUrl() != null ? response.data().toPayUrl() : ""
                    )
            );
        }

        return PaymentInitiationResult.failed(
                String.valueOf(response.code()),
                response.msg(),
                response.toString()
        );
    }

    public PaymentInitiationResult toInitiationResult(TelebirrB2CDisbursementResponse response) {
        if (response == null) {
            return PaymentInitiationResult.failed("TELEBIRR_NULL_RESPONSE", "Null response from Telebirr disbursement", null);
        }

        if (response.isSuccessful() && response.data() != null) {
            return new PaymentInitiationResult(
                    true,
                    PaymentStatus.SUCCESS,
                    response.data().transactionNo(),
                    null,
                    response.toString(),
                    null,
                    null,
                    Map.of("transactionNo", response.data().transactionNo() != null ? response.data().transactionNo() : "")
            );
        }

        return PaymentInitiationResult.failed(
                String.valueOf(response.code()),
                response.msg(),
                response.toString()
        );
    }

    public PaymentStatusResult toStatusResult(TelebirrQueryResponse response, String currencyCode) {
        if (response == null || !response.isSuccessful() || response.data() == null) {
            return PaymentStatusResult.failed(
                    null,
                    response != null ? String.valueOf(response.code()) : "UNKNOWN",
                    response != null ? response.msg() : "No response",
                    response != null ? response.toString() : null
            );
        }

        String tradeStatus = response.data().tradeStatus();
        PaymentStatus status = switch (tradeStatus != null ? tradeStatus.toUpperCase() : "") {
            case "COMPLETED", "SUCCESS", "2" -> PaymentStatus.SUCCESS;
            case "PENDING", "1", "PROCESSING" -> PaymentStatus.PENDING;
            case "CLOSED", "EXPIRED", "3" -> PaymentStatus.EXPIRED;
            default -> PaymentStatus.FAILED;
        };

        BigDecimal amount = BigDecimal.ZERO;
        if (response.data().totalAmount() != null) {
            try {
                amount = new BigDecimal(response.data().totalAmount());
            } catch (NumberFormatException ignored) {}
        }

        return new PaymentStatusResult(
                status,
                response.data().tradeNo(),
                Money.of(amount, currencyCode),
                null,
                null,
                response.toString()
        );
    }
}

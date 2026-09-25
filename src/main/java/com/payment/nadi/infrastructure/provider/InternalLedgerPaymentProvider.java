package com.payment.nadi.infrastructure.provider;

import com.payment.nadi.application.port.out.PaymentProvider;
import com.payment.nadi.application.port.out.dto.PaymentInitiationResult;
import com.payment.nadi.application.port.out.dto.PaymentProviderRequest;
import com.payment.nadi.application.port.out.dto.PaymentStatusResult;
import com.payment.nadi.domain.model.PaymentProviderType;
import com.payment.nadi.domain.model.PaymentStatus;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Provider adapter handling internal ledger and direct balance settlements.
 */
@Component
public class InternalLedgerPaymentProvider implements PaymentProvider {

    @Override
    public PaymentProviderType getProvider() {
        return PaymentProviderType.INTERNAL_LEDGER;
    }

    @Override
    public PaymentInitiationResult initiateTopUp(PaymentProviderRequest request) {
        String ref = "LEDGER_" + UUID.randomUUID();
        return PaymentInitiationResult.success(ref, "{\"status\":\"COMPLETED\"}");
    }

    @Override
    public PaymentInitiationResult initiateWithdraw(PaymentProviderRequest request) {
        String ref = "LEDGER_" + UUID.randomUUID();
        return PaymentInitiationResult.success(ref, "{\"status\":\"COMPLETED\"}");
    }

    @Override
    public PaymentInitiationResult initiateTransfer(PaymentProviderRequest request) {
        String ref = "LEDGER_" + UUID.randomUUID();
        return PaymentInitiationResult.success(ref, "{\"status\":\"COMPLETED\"}");
    }

    @Override
    public PaymentStatusResult checkStatus(String providerReference) {
        return PaymentStatusResult.success(providerReference, null, "{\"status\":\"SUCCESS\"}");
    }
}

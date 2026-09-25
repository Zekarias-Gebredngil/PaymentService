package com.payment.nadi.infrastructure.provider.telebirr;

import com.payment.nadi.application.port.out.PaymentProvider;
import com.payment.nadi.application.port.out.dto.PaymentInitiationResult;
import com.payment.nadi.application.port.out.dto.PaymentProviderRequest;
import com.payment.nadi.application.port.out.dto.PaymentStatusResult;
import com.payment.nadi.domain.model.PaymentProviderType;
import com.payment.nadi.infrastructure.provider.telebirr.dto.TelebirrB2CDisbursementRequest;
import com.payment.nadi.infrastructure.provider.telebirr.dto.TelebirrB2CDisbursementResponse;
import com.payment.nadi.infrastructure.provider.telebirr.dto.TelebirrC2BOrderRequest;
import com.payment.nadi.infrastructure.provider.telebirr.dto.TelebirrC2BOrderResponse;
import com.payment.nadi.infrastructure.provider.telebirr.dto.TelebirrQueryRequest;
import com.payment.nadi.infrastructure.provider.telebirr.dto.TelebirrQueryResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Adapter implementing generic PaymentProvider for Telebirr.
 * Keeps all Telebirr specifics encapsulated within this package.
 */
@Component
public class TelebirrPaymentProvider implements PaymentProvider {

    private static final Logger log = LoggerFactory.getLogger(TelebirrPaymentProvider.class);

    private final TelebirrClient client;
    private final TelebirrRequestMapper requestMapper;
    private final TelebirrResponseMapper responseMapper;

    public TelebirrPaymentProvider(
            TelebirrClient client,
            TelebirrRequestMapper requestMapper,
            TelebirrResponseMapper responseMapper
    ) {
        this.client = client;
        this.requestMapper = requestMapper;
        this.responseMapper = responseMapper;
    }

    @Override
    public PaymentProviderType getProvider() {
        return PaymentProviderType.TELEBIRR;
    }

    @Override
    public PaymentInitiationResult initiateTopUp(PaymentProviderRequest request) {
        log.info("Initiating Telebirr C2B top-up for payment ref: {}", request.reference());
        TelebirrC2BOrderRequest orderRequest = requestMapper.toC2BOrderRequest(request);
        TelebirrC2BOrderResponse response = client.createC2BOrder(orderRequest);
        return responseMapper.toInitiationResult(response);
    }

    @Override
    public PaymentInitiationResult initiateWithdraw(PaymentProviderRequest request) {
        log.info("Initiating Telebirr B2C withdrawal for payment ref: {} to phone: {}",
                request.reference(), request.customerPhone());
        TelebirrB2CDisbursementRequest b2cRequest = requestMapper.toB2CDisbursementRequest(request);
        TelebirrB2CDisbursementResponse response = client.sendB2CDisbursement(b2cRequest);
        return responseMapper.toInitiationResult(response);
    }

    @Override
    public PaymentInitiationResult initiateTransfer(PaymentProviderRequest request) {
        // Telebirr peer-to-peer transfer via B2C disbursement to recipient mobile number
        log.info("Initiating Telebirr P2P transfer for payment ref: {} to phone: {}",
                request.reference(), request.customerPhone());
        TelebirrB2CDisbursementRequest b2cRequest = requestMapper.toB2CDisbursementRequest(request);
        TelebirrB2CDisbursementResponse response = client.sendB2CDisbursement(b2cRequest);
        return responseMapper.toInitiationResult(response);
    }

    @Override
    public PaymentStatusResult checkStatus(String providerReference) {
        log.info("Checking Telebirr transaction status for reference: {}", providerReference);
        TelebirrQueryRequest queryRequest = requestMapper.toQueryRequest(providerReference);
        TelebirrQueryResponse response = client.queryOrderStatus(queryRequest);
        return responseMapper.toStatusResult(response, "ETB");
    }
}

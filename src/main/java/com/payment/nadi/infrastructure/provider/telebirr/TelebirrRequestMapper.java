package com.payment.nadi.infrastructure.provider.telebirr;

import com.payment.nadi.application.port.out.dto.PaymentProviderRequest;
import com.payment.nadi.infrastructure.configuration.PaymentProperties;
import com.payment.nadi.infrastructure.provider.telebirr.dto.TelebirrB2CDisbursementRequest;
import com.payment.nadi.infrastructure.provider.telebirr.dto.TelebirrC2BOrderRequest;
import com.payment.nadi.infrastructure.provider.telebirr.dto.TelebirrQueryRequest;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class TelebirrRequestMapper {

    private final PaymentProperties.TelebirrProperties properties;
    private final TelebirrCryptoService cryptoService;

    public TelebirrRequestMapper(PaymentProperties paymentProperties, TelebirrCryptoService cryptoService) {
        this.properties = paymentProperties.getProviders().getTelebirr();
        this.cryptoService = cryptoService;
    }

    public TelebirrC2BOrderRequest toC2BOrderRequest(PaymentProviderRequest request) {
        String timestamp = String.valueOf(Instant.now().toEpochMilli());
        String nonce = UUID.randomUUID().toString().replace("-", "");
        String amountStr = request.amount().amount().toPlainString();
        String notifyUrl = request.callbackUrl() != null ? request.callbackUrl() : properties.getCallbackUrl();
        String returnUrl = request.returnUrl() != null ? request.returnUrl() : properties.getReturnUrl();
        String subject = request.description() != null ? request.description() : "Payment";

        Map<String, Object> params = new HashMap<>();
        params.put("appId", properties.getAppId());
        params.put("notifyUrl", notifyUrl);
        params.put("outTradeNo", request.reference());
        params.put("receiveName", "Merchant");
        params.put("returnUrl", returnUrl);
        params.put("shortCode", properties.getShortCode());
        params.put("subject", subject);
        params.put("timeoutExpress", "30m");
        params.put("totalAmount", amountStr);
        params.put("nonce", nonce);
        params.put("timestamp", timestamp);

        String signData = cryptoService.buildSignString(params);
        String signature = cryptoService.sign(signData, properties.getPrivateKey());

        return new TelebirrC2BOrderRequest(
                properties.getAppId(),
                notifyUrl,
                request.reference(),
                "Merchant",
                returnUrl,
                properties.getShortCode(),
                subject,
                "30m",
                amountStr,
                nonce,
                timestamp,
                signature
        );
    }

    public TelebirrB2CDisbursementRequest toB2CDisbursementRequest(PaymentProviderRequest request) {
        String timestamp = String.valueOf(Instant.now().toEpochMilli());
        String amountStr = request.amount().amount().toPlainString();
        String msisdn = request.customerPhone();

        Map<String, Object> params = new HashMap<>();
        params.put("appId", properties.getAppId());
        params.put("outTradeNo", request.reference());
        params.put("receiverMsisdn", msisdn);
        params.put("amount", amountStr);
        params.put("shortCode", properties.getShortCode());
        params.put("timestamp", timestamp);

        String signData = cryptoService.buildSignString(params);
        String signature = cryptoService.sign(signData, properties.getPrivateKey());

        return new TelebirrB2CDisbursementRequest(
                properties.getAppId(),
                request.reference(),
                msisdn,
                amountStr,
                properties.getShortCode(),
                request.description(),
                timestamp,
                signature
        );
    }

    public TelebirrQueryRequest toQueryRequest(String outTradeNo) {
        String timestamp = String.valueOf(Instant.now().toEpochMilli());

        Map<String, Object> params = new HashMap<>();
        params.put("appId", properties.getAppId());
        params.put("outTradeNo", outTradeNo);
        params.put("timestamp", timestamp);

        String signData = cryptoService.buildSignString(params);
        String signature = cryptoService.sign(signData, properties.getPrivateKey());

        return new TelebirrQueryRequest(
                properties.getAppId(),
                outTradeNo,
                timestamp,
                signature
        );
    }
}

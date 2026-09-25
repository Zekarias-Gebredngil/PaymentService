package com.payment.nadi.infrastructure.provider;

import com.payment.nadi.application.port.out.PaymentProvider;
import com.payment.nadi.application.port.out.PaymentProviderResolver;
import com.payment.nadi.domain.exception.PaymentProviderException;
import com.payment.nadi.domain.model.PaymentProviderType;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DefaultPaymentProviderResolver implements PaymentProviderResolver {

    private final List<PaymentProvider> providers;

    public DefaultPaymentProviderResolver(List<PaymentProvider> providers) {
        this.providers = providers;
    }

    @Override
    public PaymentProvider resolve(PaymentProviderType type) {
        return providers.stream()
                .filter(provider -> provider.getProvider() == type)
                .findFirst()
                .orElseThrow(() -> new PaymentProviderException(
                        type != null ? type.name() : "UNKNOWN",
                        "PROVIDER_NOT_SUPPORTED",
                        "No payment provider registered for: " + type
                ));
    }
}

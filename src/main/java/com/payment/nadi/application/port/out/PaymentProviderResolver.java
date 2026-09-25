package com.payment.nadi.application.port.out;

import com.payment.nadi.domain.model.PaymentProviderType;

/**
 * Port for dynamically resolving payment provider strategy without hardcoding if/else blocks.
 */
public interface PaymentProviderResolver {

    PaymentProvider resolve(PaymentProviderType provider);
}

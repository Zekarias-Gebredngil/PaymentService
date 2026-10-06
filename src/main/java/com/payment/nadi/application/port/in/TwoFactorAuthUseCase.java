package com.payment.nadi.application.port.in;

import com.payment.nadi.application.dto.request.SendOtpRequest;
import com.payment.nadi.application.dto.request.TwoFactorDisableRequest;
import com.payment.nadi.application.dto.request.TwoFactorEnableRequest;
import com.payment.nadi.application.dto.request.TwoFactorSetupRequest;
import com.payment.nadi.application.dto.request.TwoFactorVerifyRequest;
import com.payment.nadi.application.dto.response.TwoFactorSetupResponse;
import com.payment.nadi.infrastructure.persistence.entity.UserAccountEntity;

public interface TwoFactorAuthUseCase {

    TwoFactorSetupResponse setup(TwoFactorSetupRequest request);

    UserAccountEntity enable(TwoFactorEnableRequest request);

    UserAccountEntity verify(TwoFactorVerifyRequest request);

    UserAccountEntity disable(TwoFactorDisableRequest request);

    boolean sendOtp(SendOtpRequest request);
}

package com.payment.nadi.application.port.in;

import com.payment.nadi.application.dto.request.SendOtpRequest;
import com.payment.nadi.application.dto.request.TwoFactorDisableRequest;
import com.payment.nadi.application.dto.request.TwoFactorEnableRequest;
import com.payment.nadi.application.dto.request.TwoFactorSetupRequest;
import com.payment.nadi.application.dto.request.TwoFactorVerifyRequest;
import com.payment.nadi.application.dto.response.TwoFactorSetupResponse;
import com.payment.nadi.application.dto.response.UserAccountDto;

public interface TwoFactorAuthUseCase {

    TwoFactorSetupResponse setup(TwoFactorSetupRequest request);

    UserAccountDto enable(TwoFactorEnableRequest request);

    UserAccountDto verify(TwoFactorVerifyRequest request);

    UserAccountDto disable(TwoFactorDisableRequest request);

    boolean sendOtp(SendOtpRequest request);
}

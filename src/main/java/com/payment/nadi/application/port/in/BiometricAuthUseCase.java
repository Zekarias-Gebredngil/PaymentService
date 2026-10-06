package com.payment.nadi.application.port.in;

import com.payment.nadi.application.dto.request.BiometricChallengeRequest;
import com.payment.nadi.application.dto.request.BiometricLoginRequest;
import com.payment.nadi.application.dto.request.BiometricRegisterRequest;
import com.payment.nadi.application.dto.response.BiometricChallengeResponse;
import com.payment.nadi.infrastructure.persistence.entity.UserAccountEntity;

public interface BiometricAuthUseCase {

    BiometricChallengeResponse createChallenge(BiometricChallengeRequest request);

    UserAccountEntity registerBiometric(BiometricRegisterRequest request);

    UserAccountEntity authenticateBiometric(BiometricLoginRequest request);

    UserAccountEntity disableBiometric(String identifier);

    boolean isBiometricEnabled(String identifier);
}

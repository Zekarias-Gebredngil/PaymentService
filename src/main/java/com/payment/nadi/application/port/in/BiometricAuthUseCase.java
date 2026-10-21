package com.payment.nadi.application.port.in;

import com.payment.nadi.application.dto.request.BiometricChallengeRequest;
import com.payment.nadi.application.dto.request.BiometricLoginRequest;
import com.payment.nadi.application.dto.request.BiometricRegisterRequest;
import com.payment.nadi.application.dto.response.BiometricChallengeResponse;
import com.payment.nadi.application.dto.response.UserAccountDto;

import java.util.Optional;

public interface BiometricAuthUseCase {

    BiometricChallengeResponse createChallenge(BiometricChallengeRequest request);

    UserAccountDto registerBiometric(BiometricRegisterRequest request);

    UserAccountDto authenticateBiometric(BiometricLoginRequest request);

    UserAccountDto disableBiometric(String identifier);

    boolean isBiometricEnabled(String identifier);

    Optional<UserAccountDto> getUserStatusForBiometric(String identifier);
}

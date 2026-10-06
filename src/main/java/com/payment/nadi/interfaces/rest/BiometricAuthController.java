package com.payment.nadi.interfaces.rest;

import com.payment.nadi.application.dto.request.BiometricChallengeRequest;
import com.payment.nadi.application.dto.request.BiometricLoginRequest;
import com.payment.nadi.application.dto.request.BiometricRegisterRequest;
import com.payment.nadi.application.dto.response.BiometricChallengeResponse;
import com.payment.nadi.application.port.in.BiometricAuthUseCase;
import com.payment.nadi.common.response.ApiResponse;
import com.payment.nadi.infrastructure.persistence.entity.UserAccountEntity;
import com.payment.nadi.interfaces.rest.AccountController.UserAccountDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth/biometric")
@Tag(name = "Biometric Authentication", description = "Hardware-backed Biometric Authentication (WebAuthn, TouchID, FaceID, Fingerprint)")
public class BiometricAuthController {

    private final BiometricAuthUseCase biometricAuthUseCase;

    public BiometricAuthController(BiometricAuthUseCase biometricAuthUseCase) {
        this.biometricAuthUseCase = biometricAuthUseCase;
    }

    @PostMapping("/challenge")
    @Operation(summary = "Generate Biometric Challenge", description = "Generates a cryptographically random anti-replay challenge nonce valid for 5 minutes.")
    public ResponseEntity<ApiResponse<BiometricChallengeResponse>> challenge(@RequestBody(required = false) BiometricChallengeRequest request) {
        BiometricChallengeResponse response = biometricAuthUseCase.createChallenge(request);
        return ResponseEntity.ok(ApiResponse.ok("Biometric challenge generated", response));
    }

    @PostMapping("/register")
    @Operation(summary = "Register Biometric Credential", description = "Enrolls device public key by cryptographically verifying client signature over the challenge.")
    public ResponseEntity<ApiResponse<UserAccountDto>> register(@Valid @RequestBody BiometricRegisterRequest request) {
        UserAccountEntity user = biometricAuthUseCase.registerBiometric(request);
        return ResponseEntity.ok(ApiResponse.ok("Biometric credential successfully registered and enrolled!", UserAccountDto.fromEntity(user)));
    }

    @PostMapping("/login")
    @Operation(summary = "Biometric Login", description = "Authenticates user using digital signature verification over the server challenge.")
    public ResponseEntity<ApiResponse<UserAccountDto>> login(@Valid @RequestBody BiometricLoginRequest request) {
        UserAccountEntity user = biometricAuthUseCase.authenticateBiometric(request);
        return ResponseEntity.ok(ApiResponse.ok("Biometric authentication successful!", UserAccountDto.fromEntity(user)));
    }

    @PostMapping("/disable")
    @Operation(summary = "Disable Biometric Authentication", description = "Revokes enrolled biometric credentials and deactivates biometric login.")
    public ResponseEntity<ApiResponse<UserAccountDto>> disable(@RequestParam String identifier) {
        UserAccountEntity user = biometricAuthUseCase.disableBiometric(identifier);
        return ResponseEntity.ok(ApiResponse.ok("Biometric authentication disabled", UserAccountDto.fromEntity(user)));
    }

    @GetMapping("/status")
    @Operation(summary = "Check Biometric Status", description = "Returns whether the specified account has biometric authentication enrolled.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> status(@RequestParam String identifier) {
        boolean enabled = biometricAuthUseCase.isBiometricEnabled(identifier);
        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "identifier", identifier,
                "biometricEnabled", enabled
        )));
    }
}

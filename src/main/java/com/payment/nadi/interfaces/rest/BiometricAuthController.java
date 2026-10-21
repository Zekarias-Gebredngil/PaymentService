package com.payment.nadi.interfaces.rest;

import com.payment.nadi.application.dto.request.BiometricChallengeRequest;
import com.payment.nadi.application.dto.request.BiometricLoginRequest;
import com.payment.nadi.application.dto.request.BiometricRegisterRequest;
import com.payment.nadi.application.dto.response.BiometricChallengeResponse;
import com.payment.nadi.application.dto.response.UserAccountDto;
import com.payment.nadi.application.port.in.BiometricAuthUseCase;
import com.payment.nadi.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

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
        UserAccountDto user = biometricAuthUseCase.registerBiometric(request);
        return ResponseEntity.ok(ApiResponse.ok("Biometric credential successfully registered and enrolled!", user));
    }

    @PostMapping("/login")
    @Operation(summary = "Biometric Login", description = "Authenticates user using digital signature verification over the server challenge.")
    public ResponseEntity<ApiResponse<UserAccountDto>> login(@Valid @RequestBody BiometricLoginRequest request) {
        UserAccountDto user = biometricAuthUseCase.authenticateBiometric(request);
        return ResponseEntity.ok(ApiResponse.ok("Biometric authentication successful!", user));
    }

    @PostMapping("/disable")
    @Operation(summary = "Disable Biometric Authentication", description = "Revokes enrolled biometric credentials and deactivates biometric login.")
    public ResponseEntity<ApiResponse<UserAccountDto>> disable(@RequestParam String identifier) {
        UserAccountDto user = biometricAuthUseCase.disableBiometric(identifier);
        return ResponseEntity.ok(ApiResponse.ok("Biometric authentication disabled", user));
    }

    @GetMapping("/status")
    @Operation(summary = "Check Biometric Status", description = "Returns whether the specified account has biometric authentication enrolled and account status.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> status(@RequestParam String identifier) {
        Optional<UserAccountDto> userOpt = biometricAuthUseCase.getUserStatusForBiometric(identifier);
        if (userOpt.isEmpty()) {
            return ResponseEntity.ok(ApiResponse.ok(Map.of(
                    "identifier", identifier,
                    "biometricEnabled", false,
                    "accountStatus", "NOT_FOUND",
                    "isBlocked", true
            )));
        }
        UserAccountDto user = userOpt.get();
        boolean enabled = Boolean.TRUE.equals(user.biometricEnabled());
        String status = user.status() != null ? user.status() : "ACTIVE";
        boolean blocked = !"ACTIVE".equalsIgnoreCase(status);
        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "identifier", identifier,
                "fullName", user.fullName() != null ? user.fullName() : "",
                "accountNumber", user.accountNumber() != null ? user.accountNumber() : "",
                "phoneNumber", user.phoneNumber() != null ? user.phoneNumber() : "",
                "biometricEnabled", enabled,
                "accountStatus", status,
                "isBlocked", blocked
        )));
    }
}

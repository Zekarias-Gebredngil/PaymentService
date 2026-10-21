package com.payment.nadi.interfaces.rest;

import com.payment.nadi.application.dto.request.SendOtpRequest;
import com.payment.nadi.application.dto.request.TwoFactorDisableRequest;
import com.payment.nadi.application.dto.request.TwoFactorEnableRequest;
import com.payment.nadi.application.dto.request.TwoFactorSetupRequest;
import com.payment.nadi.application.dto.request.TwoFactorVerifyRequest;
import com.payment.nadi.application.dto.response.TwoFactorSetupResponse;
import com.payment.nadi.application.dto.response.UserAccountDto;
import com.payment.nadi.application.port.in.TwoFactorAuthUseCase;
import com.payment.nadi.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth/2fa")
@Tag(name = "Two-Factor Authentication", description = "RFC 6238 TOTP, SMS OTP, and Backup Recovery Codes")
public class TwoFactorAuthController {

    private final TwoFactorAuthUseCase twoFactorAuthUseCase;

    public TwoFactorAuthController(TwoFactorAuthUseCase twoFactorAuthUseCase) {
        this.twoFactorAuthUseCase = twoFactorAuthUseCase;
    }

    @PostMapping("/setup")
    @Operation(summary = "Setup 2FA TOTP", description = "Generates a 160-bit Base32 TOTP secret, standard otpauth:// URI, QR code URL, and 8 recovery backup codes.")
    public ResponseEntity<ApiResponse<TwoFactorSetupResponse>> setup(@Valid @RequestBody TwoFactorSetupRequest request) {
        TwoFactorSetupResponse response = twoFactorAuthUseCase.setup(request);
        return ResponseEntity.ok(ApiResponse.ok("2FA setup initialized. Scan the QR code with Google Authenticator or Microsoft Authenticator, then call /enable with a generated code.", response));
    }

    @PostMapping("/enable")
    @Operation(summary = "Enable 2FA TOTP", description = "Validates the first TOTP code from user authenticator app and locks 2FA activation on the account.")
    public ResponseEntity<ApiResponse<UserAccountDto>> enable(@Valid @RequestBody TwoFactorEnableRequest request) {
        UserAccountDto user = twoFactorAuthUseCase.enable(request);
        return ResponseEntity.ok(ApiResponse.ok("Two-Factor Authentication successfully enabled on your account!", user));
    }

    @PostMapping("/verify")
    @Operation(summary = "Verify 2FA Code / Login Step-up", description = "Verifies a 6-digit TOTP code, SMS OTP code, or single-use backup recovery code.")
    public ResponseEntity<ApiResponse<UserAccountDto>> verify(@Valid @RequestBody TwoFactorVerifyRequest request) {
        UserAccountDto user = twoFactorAuthUseCase.verify(request);
        return ResponseEntity.ok(ApiResponse.ok("Two-Factor verification successful!", user));
    }

    @PostMapping("/disable")
    @Operation(summary = "Disable 2FA", description = "Requires account password verification to securely deactivate 2FA on the account.")
    public ResponseEntity<ApiResponse<UserAccountDto>> disable(@Valid @RequestBody TwoFactorDisableRequest request) {
        UserAccountDto user = twoFactorAuthUseCase.disable(request);
        return ResponseEntity.ok(ApiResponse.ok("Two-Factor Authentication disabled.", user));
    }

    @PostMapping("/send-otp")
    @Operation(summary = "Send 2FA SMS OTP", description = "Dispatches a secure 6-digit one-time passcode to user registered phone number via SMS alert.")
    public ResponseEntity<ApiResponse<String>> sendOtp(@Valid @RequestBody SendOtpRequest request) {
        boolean sent = twoFactorAuthUseCase.sendOtp(request);
        if (sent) {
            return ResponseEntity.ok(ApiResponse.ok("Verification code sent via SMS. Valid for 5 minutes.", "OTP_SENT"));
        }
        return ResponseEntity.badRequest().body(ApiResponse.error("OTP_SEND_FAILED", "Failed to send SMS verification code"));
    }
}

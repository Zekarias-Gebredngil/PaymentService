package com.payment.nadi.interfaces.rest;

import com.payment.nadi.application.dto.request.AdminCreateAccountRequest;
import com.payment.nadi.application.dto.request.ChangePasswordRequest;
import com.payment.nadi.application.dto.request.LoginRequest;
import com.payment.nadi.application.dto.request.RegisterRequest;
import com.payment.nadi.application.dto.response.PageResponse;
import com.payment.nadi.application.dto.response.UserAccountDto;
import com.payment.nadi.application.port.in.AccountUseCase;
import com.payment.nadi.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Account & User Management", description = "Dynamic User Registration, Admin Approval, and Authentication")
public class AccountController {

    private final AccountUseCase accountUseCase;

    public AccountController(AccountUseCase accountUseCase) {
        this.accountUseCase = accountUseCase;
    }

    @PostMapping("/auth/register")
    @Operation(summary = "Register User Account", description = "Registers a new user with salted hashed credentials and an 8-digit numeric account number starting with 0.")
    public ResponseEntity<ApiResponse<UserAccountDto>> register(@Valid @RequestBody RegisterRequest request) {
        UserAccountDto saved = accountUseCase.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Registration successful! Your account is PENDING ADMIN APPROVAL. An administrator must approve your account before you can log in.", saved));
    }

    @PostMapping("/auth/login")
    @Operation(summary = "User Login", description = "Authenticates user using BCrypt salted hash verification. Returns 403 Forbidden if account is PENDING_APPROVAL or SUSPENDED.")
    public ResponseEntity<ApiResponse<UserAccountDto>> login(@Valid @RequestBody LoginRequest request) {
        ApiResponse<UserAccountDto> response = accountUseCase.login(request);
        if (!response.success()) {
            if ("PENDING_APPROVAL".equalsIgnoreCase(response.errorCode())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
            }
            if ("ACCOUNT_LOCKED".equalsIgnoreCase(response.errorCode())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
            }
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }
        return ResponseEntity.ok(response);
    }

    @PostMapping("/auth/change-password")
    @Operation(summary = "Change Password", description = "Securely changes user password by verifying the current password and saving the new password as a salted BCrypt hash.")
    public ResponseEntity<ApiResponse<UserAccountDto>> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        UserAccountDto saved = accountUseCase.changePassword(request);
        return ResponseEntity.ok(ApiResponse.ok("Password successfully updated and securely hashed.", saved));
    }

    @PostMapping("/accounts")
    @Operation(summary = "Admin Register User Account", description = "Allows an administrator to register and provision a new account directly.")
    public ResponseEntity<ApiResponse<UserAccountDto>> adminRegisterAccount(@Valid @RequestBody AdminCreateAccountRequest request) {
        UserAccountDto saved = accountUseCase.adminRegisterAccount(request);
        boolean shouldActivate = request.autoActivate() == null || request.autoActivate();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("User account successfully registered" + (shouldActivate ? " and activated!" : "!"), saved));
    }

    @GetMapping("/accounts")
    @Operation(summary = "List All Accounts (Admin)", description = "Returns a paginated list of registered accounts for administrative monitoring and approval.")
    public ResponseEntity<ApiResponse<PageResponse<UserAccountDto>>> listAccounts(
            @RequestParam(required = false, defaultValue = "ALL") String status,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String query,
            @RequestParam(required = false, defaultValue = "createdAt") String sortBy,
            @RequestParam(required = false, defaultValue = "desc") String direction
    ) {
        PageResponse<UserAccountDto> response = accountUseCase.listAccounts(status, page, size, limit, query, sortBy, direction);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping("/accounts/stats")
    @Operation(summary = "Get Account Metrics (Admin)", description = "Returns summary counts of registered, pending, active, and suspended accounts.")
    public ResponseEntity<ApiResponse<Map<String, Long>>> getAccountStats() {
        Map<String, Long> stats = accountUseCase.getAccountStats();
        return ResponseEntity.ok(ApiResponse.ok(stats));
    }

    @GetMapping("/accounts/lookup")
    @Cacheable(value = "account-lookups", key = "#query")
    @Operation(summary = "Lookup Active Account", description = "Looks up an active recipient account by phone number or account number for transfers.")
    public ResponseEntity<ApiResponse<UserAccountDto>> lookupAccount(@RequestParam String query) {
        UserAccountDto dto = accountUseCase.lookupAccount(query);
        return ResponseEntity.ok(ApiResponse.ok(dto));
    }

    @PatchMapping("/accounts/{id}/approve")
    @CacheEvict(value = "account-lookups", allEntries = true)
    @Operation(summary = "Admin Approve Account", description = "Approves a pending account and activates it for transactions.")
    public ResponseEntity<ApiResponse<UserAccountDto>> approveAccount(@PathVariable UUID id) {
        UserAccountDto saved = accountUseCase.approveAccount(id);
        return ResponseEntity.ok(ApiResponse.ok("Account successfully approved and activated!", saved));
    }

    @PatchMapping("/accounts/{id}/suspend")
    @CacheEvict(value = "account-lookups", allEntries = true)
    @Operation(summary = "Admin Suspend Account", description = "Suspends an active account.")
    public ResponseEntity<ApiResponse<UserAccountDto>> suspendAccount(@PathVariable UUID id) {
        UserAccountDto saved = accountUseCase.suspendAccount(id);
        return ResponseEntity.ok(ApiResponse.ok("Account suspended", saved));
    }

    @PatchMapping("/accounts/{id}/activate")
    @CacheEvict(value = "account-lookups", allEntries = true)
    @Operation(summary = "Admin Activate Account", description = "Reactivates a suspended account.")
    public ResponseEntity<ApiResponse<UserAccountDto>> activateAccount(@PathVariable UUID id) {
        UserAccountDto saved = accountUseCase.activateAccount(id);
        return ResponseEntity.ok(ApiResponse.ok("Account activated", saved));
    }
}

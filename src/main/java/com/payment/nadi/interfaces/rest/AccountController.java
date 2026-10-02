package com.payment.nadi.interfaces.rest;

import com.payment.nadi.common.response.ApiResponse;
import com.payment.nadi.infrastructure.persistence.entity.UserAccountEntity;
import com.payment.nadi.infrastructure.persistence.repository.SpringDataUserAccountRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.PostConstruct;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Account & User Management", description = "Dynamic User Registration, Admin Approval, and Authentication")
public class AccountController {

    private final SpringDataUserAccountRepository repository;

    public AccountController(SpringDataUserAccountRepository repository) {
        this.repository = repository;
    }

    public record RegisterRequest(
            @NotBlank String fullName,
            @NotBlank String phoneNumber,
            String email,
            @NotBlank String password,
            String role
    ) {}

    public record LoginRequest(
            @NotBlank String identifier,
            @NotBlank String password
    ) {}

    public record UserAccountDto(
            UUID id,
            String fullName,
            String phoneNumber,
            String email,
            String role,
            String accountNumber,
            UUID customerId,
            UUID sourceAccountId,
            String status,
            Instant createdAt,
            Instant approvedAt,
            String approvedBy
    ) {
        public static UserAccountDto fromEntity(UserAccountEntity e) {
            return new UserAccountDto(
                    e.getId(),
                    e.getFullName(),
                    e.getPhoneNumber(),
                    e.getEmail(),
                    e.getRole(),
                    e.getAccountNumber(),
                    e.getCustomerId(),
                    e.getSourceAccountId(),
                    e.getStatus(),
                    e.getCreatedAt(),
                    e.getApprovedAt(),
                    e.getApprovedBy()
            );
        }
    }

    private Optional<UserAccountEntity> findUserByIdentifier(String identifier) {
        if (identifier == null || identifier.isBlank()) return Optional.empty();
        String clean = identifier.trim();
        Optional<UserAccountEntity> byPhone = repository.findByPhoneNumber(clean);
        if (byPhone.isPresent()) return byPhone;
        Optional<UserAccountEntity> byAcc = repository.findByAccountNumber(clean);
        if (byAcc.isPresent()) return byAcc;
        return repository.findByEmail(clean);
    }

    @PostConstruct
    public void seedInitialAccounts() {
        // 1. System Admin
        if (!repository.existsByPhoneNumber("0900000000")) {
            repository.save(UserAccountEntity.builder()
                    .id(UUID.fromString("00000000-0000-0000-0000-000000000001"))
                    .fullName("System Administrator")
                    .phoneNumber("0900000000")
                    .email("admin@nadipay.et")
                    .password("admin123")
                    .role("ADMIN")
                    .accountNumber("ADM-000001")
                    .customerId(UUID.fromString("00000000-0000-0000-0000-000000000001"))
                    .sourceAccountId(UUID.fromString("00000000-0000-0000-0000-000000000002"))
                    .status("ACTIVE")
                    .createdAt(Instant.now())
                    .approvedAt(Instant.now())
                    .approvedBy("SYSTEM")
                    .build());
        }

        // 2. Abebe Bikila
        if (!repository.existsByPhoneNumber("0911223344")) {
            repository.save(UserAccountEntity.builder()
                    .id(UUID.fromString("a0000000-0000-0000-0000-000000000001"))
                    .fullName("Abebe Bikila")
                    .phoneNumber("0911223344")
                    .email("abebe@example.com")
                    .password("123456")
                    .role("CUSTOMER")
                    .accountNumber("ACC-0911223344")
                    .customerId(UUID.fromString("a1000000-0000-0000-0000-000000000001"))
                    .sourceAccountId(UUID.fromString("a2000000-0000-0000-0000-000000000001"))
                    .status("ACTIVE")
                    .createdAt(Instant.now())
                    .approvedAt(Instant.now())
                    .approvedBy("ADMIN")
                    .build());
        }

        // 3. Brhane Wolde
        if (!repository.existsByPhoneNumber("0922334455")) {
            repository.save(UserAccountEntity.builder()
                    .id(UUID.fromString("b0000000-0000-0000-0000-000000000002"))
                    .fullName("Brhane Wolde")
                    .phoneNumber("0922334455")
                    .email("brhane@example.com")
                    .password("123456")
                    .role("CUSTOMER")
                    .accountNumber("ACC-0922334455")
                    .customerId(UUID.fromString("b1000000-0000-0000-0000-000000000002"))
                    .sourceAccountId(UUID.fromString("b2000000-0000-0000-0000-000000000002"))
                    .status("ACTIVE")
                    .createdAt(Instant.now())
                    .approvedAt(Instant.now())
                    .approvedBy("ADMIN")
                    .build());
        }

        // 4. Merchant Store
        if (!repository.existsByPhoneNumber("0933445566")) {
            repository.save(UserAccountEntity.builder()
                    .id(UUID.fromString("c0000000-0000-0000-0000-000000000003"))
                    .fullName("Merchant Store")
                    .phoneNumber("0933445566")
                    .email("merchant@store.et")
                    .password("123456")
                    .role("MERCHANT")
                    .accountNumber("ACC-0933445566")
                    .customerId(UUID.fromString("c1000000-0000-0000-0000-000000000003"))
                    .sourceAccountId(UUID.fromString("c2000000-0000-0000-0000-000000000003"))
                    .status("ACTIVE")
                    .createdAt(Instant.now())
                    .approvedAt(Instant.now())
                    .approvedBy("ADMIN")
                    .build());
        }
    }

    @PostMapping("/auth/register")
    @Operation(summary = "Register User Account", description = "Registers a new user. The account is initialized in PENDING_APPROVAL status and must be approved by an administrator before transactions are allowed.")
    public ResponseEntity<ApiResponse<UserAccountDto>> register(@RequestBody RegisterRequest request) {
        String cleanPhone = request.phoneNumber().trim();
        if (repository.existsByPhoneNumber(cleanPhone)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.error("PHONE_EXISTS", "An account with this phone number already exists"));
        }

        UUID userId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID sourceAccountId = UUID.randomUUID();
        String accNum = "ACC-" + (cleanPhone.length() >= 6 ? cleanPhone.substring(cleanPhone.length() - 6) : String.format("%06d", (int)(Math.random() * 900000) + 100000));

        UserAccountEntity entity = UserAccountEntity.builder()
                .id(userId)
                .fullName(request.fullName().trim())
                .phoneNumber(cleanPhone)
                .email(request.email() != null ? request.email().trim() : "")
                .password(request.password())
                .role(request.role() != null && !request.role().isBlank() ? request.role().toUpperCase() : "CUSTOMER")
                .accountNumber(accNum)
                .customerId(customerId)
                .sourceAccountId(sourceAccountId)
                .status("PENDING_APPROVAL")
                .createdAt(Instant.now())
                .build();

        UserAccountEntity saved = repository.save(entity);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Registration successful! Your account is PENDING ADMIN APPROVAL. An administrator must approve your account before you can log in.", UserAccountDto.fromEntity(saved)));
    }

    @PostMapping("/auth/login")
    @Operation(summary = "User Login", description = "Authenticates user. Returns 403 Forbidden if account is PENDING_APPROVAL or SUSPENDED.")
    public ResponseEntity<ApiResponse<UserAccountDto>> login(@RequestBody LoginRequest request) {
        String idStr = request.identifier().trim();
        Optional<UserAccountEntity> userOpt = findUserByIdentifier(idStr);

        if (userOpt.isEmpty() || !userOpt.get().getPassword().equals(request.password())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("INVALID_CREDENTIALS", "Invalid phone number, account number, or password"));
        }

        UserAccountEntity user = userOpt.get();

        if ("PENDING_APPROVAL".equalsIgnoreCase(user.getStatus())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ApiResponse<>(
                            false,
                            "Account is PENDING ADMIN APPROVAL. You cannot log in or perform transactions until an administrator approves your account.",
                            UserAccountDto.fromEntity(user),
                            "PENDING_APPROVAL",
                            Instant.now()
                    ));
        }

        if ("SUSPENDED".equalsIgnoreCase(user.getStatus()) || "REJECTED".equalsIgnoreCase(user.getStatus())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("ACCOUNT_LOCKED", "Your account has been suspended or rejected. Please contact administrator."));
        }

        return ResponseEntity.ok(ApiResponse.ok("Login successful", UserAccountDto.fromEntity(user)));
    }

    @GetMapping("/accounts")
    @Operation(summary = "List All Accounts (Admin)", description = "Returns all registered accounts for administrative monitoring and approval.")
    public ResponseEntity<ApiResponse<List<UserAccountDto>>> listAccounts(
            @RequestParam(required = false, defaultValue = "ALL") String status
    ) {
        List<UserAccountEntity> list;
        if ("ALL".equalsIgnoreCase(status)) {
            list = repository.findAllByOrderByCreatedAtDesc();
        } else {
            list = repository.findByStatusOrderByCreatedAtDesc(status.toUpperCase());
        }
        List<UserAccountDto> dtos = list.stream().map(UserAccountDto::fromEntity).toList();
        return ResponseEntity.ok(ApiResponse.ok(dtos));
    }

    @GetMapping("/accounts/lookup")
    @Operation(summary = "Lookup Active Account", description = "Looks up an active recipient account by phone number or account number for transfers.")
    public ResponseEntity<ApiResponse<UserAccountDto>> lookupAccount(@RequestParam String query) {
        Optional<UserAccountEntity> userOpt = findUserByIdentifier(query.trim());
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("ACCOUNT_NOT_FOUND", "No account found matching: " + query));
        }
        UserAccountEntity user = userOpt.get();
        if (!"ACTIVE".equalsIgnoreCase(user.getStatus())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("ACCOUNT_NOT_ACTIVE", "Recipient account is currently " + user.getStatus() + " and cannot receive funds."));
        }
        return ResponseEntity.ok(ApiResponse.ok(UserAccountDto.fromEntity(user)));
    }

    @PatchMapping("/accounts/{id}/approve")
    @Operation(summary = "Admin Approve Account", description = "Approves a pending account and activates it for transactions.")
    public ResponseEntity<ApiResponse<UserAccountDto>> approveAccount(@PathVariable UUID id) {
        Optional<UserAccountEntity> userOpt = repository.findById(id);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("NOT_FOUND", "Account not found"));
        }
        UserAccountEntity user = userOpt.get();
        user.setStatus("ACTIVE");
        user.setApprovedAt(Instant.now());
        user.setApprovedBy("admin");
        UserAccountEntity saved = repository.save(user);
        return ResponseEntity.ok(ApiResponse.ok("Account successfully approved and activated!", UserAccountDto.fromEntity(saved)));
    }

    @PatchMapping("/accounts/{id}/suspend")
    @Operation(summary = "Admin Suspend Account", description = "Suspends an active account.")
    public ResponseEntity<ApiResponse<UserAccountDto>> suspendAccount(@PathVariable UUID id) {
        Optional<UserAccountEntity> userOpt = repository.findById(id);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("NOT_FOUND", "Account not found"));
        }
        UserAccountEntity user = userOpt.get();
        user.setStatus("SUSPENDED");
        UserAccountEntity saved = repository.save(user);
        return ResponseEntity.ok(ApiResponse.ok("Account suspended", UserAccountDto.fromEntity(saved)));
    }

    @PatchMapping("/accounts/{id}/activate")
    @Operation(summary = "Admin Activate Account", description = "Reactivates a suspended account.")
    public ResponseEntity<ApiResponse<UserAccountDto>> activateAccount(@PathVariable UUID id) {
        Optional<UserAccountEntity> userOpt = repository.findById(id);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("NOT_FOUND", "Account not found"));
        }
        UserAccountEntity user = userOpt.get();
        user.setStatus("ACTIVE");
        UserAccountEntity saved = repository.save(user);
        return ResponseEntity.ok(ApiResponse.ok("Account activated", UserAccountDto.fromEntity(saved)));
    }
}

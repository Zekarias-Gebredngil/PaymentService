package com.payment.nadi.interfaces.rest;

import com.payment.nadi.application.dto.response.PageResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import java.util.Map;

import com.payment.nadi.common.response.ApiResponse;
import com.payment.nadi.infrastructure.persistence.entity.UserAccountEntity;
import com.payment.nadi.infrastructure.persistence.repository.SpringDataUserAccountRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.PostConstruct;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import com.payment.nadi.application.port.out.EventPublisher;
import com.payment.nadi.domain.event.UserSecurityEvent;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Account & User Management", description = "Dynamic User Registration, Admin Approval, and Authentication")
public class AccountController {

    private final SpringDataUserAccountRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final EventPublisher eventPublisher;

    @Value("${app.seed.admin.phone:${ADMIN_PHONE:}}")
    private String seedAdminPhone;

    @Value("${app.seed.admin.password:${ADMIN_PASSWORD:}}")
    private String seedAdminPassword;

    @Value("${app.seed.admin.name:${ADMIN_NAME:Administrator}}")
    private String seedAdminName;

    @Value("${app.seed.admin.email:${ADMIN_EMAIL:}}")
    private String seedAdminEmail;

    public AccountController(SpringDataUserAccountRepository repository, PasswordEncoder passwordEncoder, EventPublisher eventPublisher) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.eventPublisher = eventPublisher;
    }

    public record AdminCreateAccountRequest(
            @NotBlank(message = "Full name is required") String fullName,
            @NotBlank(message = "Phone number is required") String phoneNumber,
            String email,
            @NotBlank(message = "Password is required") @Size(min = 6, message = "Password must be at least 6 characters") String password,
            String role,
            Boolean autoActivate
    ) {}

    public record RegisterRequest(
            @NotBlank(message = "Full name is required") String fullName,
            @NotBlank(message = "Phone number is required") String phoneNumber,
            @Email(message = "Invalid email format") String email,
            @NotBlank(message = "Password is required") @Size(min = 6, message = "Password must be at least 6 characters") String password,
            String role
    ) {}

    public record LoginRequest(
            @NotBlank(message = "Identifier (phone or account number) is required") String identifier,
            @NotBlank(message = "Password is required") String password
    ) {}

    public record ChangePasswordRequest(
            UUID userId,
            String identifier,
            @NotBlank(message = "Current password is required") String oldPassword,
            @NotBlank(message = "New password is required") @Size(min = 6, message = "New password must be at least 6 characters") String newPassword
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
            String approvedBy,
            Boolean twoFactorEnabled,
            String twoFactorType,
            Boolean biometricEnabled,
            Boolean smsNotificationsEnabled,
            Boolean pushNotificationsEnabled
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
                    e.getApprovedBy(),
                    e.getTwoFactorEnabled(),
                    e.getTwoFactorType(),
                    e.getBiometricEnabled(),
                    e.getSmsNotificationsEnabled(),
                    e.getPushNotificationsEnabled()
            );
        }
    }

    private boolean isBcryptHash(String password) {
        if (password == null) return false;
        return password.matches("^\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53}$");
    }

    private boolean isNumericAccountNumber(String accNum) {
        // Enforces pattern like 00564829: only numbers, no characters, starting with 0, 8 digits long
        return accNum != null && accNum.matches("^0\\d{7}$");
    }

    private String generateNumericAccountNumber() {
        // Generates an 8-digit numeric account number starting with 0 (e.g. 00564829)
        String accNum;
        do {
            int randomPart = ThreadLocalRandom.current().nextInt(1_000_000);
            accNum = String.format("00%06d", randomPart);
        } while (repository.existsByAccountNumber(accNum));
        return accNum;
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
    public void onStartup() {
        // Optional environment-configured initial administrator seed (no hardcoded credentials)
        if (seedAdminPhone != null && !seedAdminPhone.isBlank() &&
            seedAdminPassword != null && !seedAdminPassword.isBlank()) {
            String cleanPhone = seedAdminPhone.trim();
            if (!repository.existsByPhoneNumber(cleanPhone)) {
                UUID adminId = UUID.randomUUID();
                UUID customerId = UUID.randomUUID();
                UUID sourceAccountId = UUID.randomUUID();
                String accNum = generateNumericAccountNumber();

                repository.save(UserAccountEntity.builder()
                        .id(adminId)
                        .fullName(seedAdminName != null && !seedAdminName.isBlank() ? seedAdminName.trim() : "Administrator")
                        .phoneNumber(cleanPhone)
                        .email(seedAdminEmail != null ? seedAdminEmail.trim() : "")
                        .password(passwordEncoder.encode(seedAdminPassword.trim()))
                        .role("ADMIN")
                        .accountNumber(accNum)
                        .customerId(customerId)
                        .sourceAccountId(sourceAccountId)
                        .status("ACTIVE")
                        .createdAt(Instant.now())
                        .approvedAt(Instant.now())
                        .approvedBy("SYSTEM")
                        .build());
            }
        }

        // Ensure all stored account numbers and passwords adhere to required patterns
        List<UserAccountEntity> allUsers = repository.findAll();
        for (UserAccountEntity u : allUsers) {
            boolean changed = false;
            if (!isNumericAccountNumber(u.getAccountNumber())) {
                u.setAccountNumber(generateNumericAccountNumber());
                changed = true;
            }
            if (!isBcryptHash(u.getPassword())) {
                u.setPassword(passwordEncoder.encode(u.getPassword().trim()));
                changed = true;
            }
            if (changed) {
                repository.save(u);
            }
        }
    }

    @PostMapping("/auth/register")
    @Operation(summary = "Register User Account", description = "Registers a new user with salted hashed credentials and an 8-digit numeric account number starting with 0.")
    public ResponseEntity<ApiResponse<UserAccountDto>> register(@Valid @RequestBody RegisterRequest request) {
        String cleanPhone = request.phoneNumber().trim();
        if (repository.existsByPhoneNumber(cleanPhone)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.error("PHONE_EXISTS", "An account with this phone number already exists"));
        }

        if (request.password() == null || request.password().trim().length() < 6) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("INVALID_PASSWORD", "Password must be at least 6 characters long"));
        }

        UUID userId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID sourceAccountId = UUID.randomUUID();
        
        // 8-digit numeric account number starting with 0 (e.g. 00564829)
        String accNum = generateNumericAccountNumber();

        // Salt and hash password with BCrypt
        String hashedPassword = passwordEncoder.encode(request.password().trim());

        UserAccountEntity entity = UserAccountEntity.builder()
                .id(userId)
                .fullName(request.fullName().trim())
                .phoneNumber(cleanPhone)
                .email(request.email() != null ? request.email().trim() : "")
                .password(hashedPassword)
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
    @Operation(summary = "User Login", description = "Authenticates user using BCrypt salted hash verification. Returns 403 Forbidden if account is PENDING_APPROVAL or SUSPENDED.")
    public ResponseEntity<ApiResponse<UserAccountDto>> login(@Valid @RequestBody LoginRequest request) {
        String idStr = request.identifier().trim();
        Optional<UserAccountEntity> userOpt = findUserByIdentifier(idStr);

        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("INVALID_CREDENTIALS", "Invalid phone number, account number, or password"));
        }

        UserAccountEntity user = userOpt.get();
        boolean passwordMatches = false;

        if (isBcryptHash(user.getPassword())) {
            passwordMatches = passwordEncoder.matches(request.password(), user.getPassword());
        } else {
            if (user.getPassword().equals(request.password())) {
                passwordMatches = true;
                user.setPassword(passwordEncoder.encode(request.password()));
                repository.save(user);
            }
        }

        if (!passwordMatches) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("INVALID_CREDENTIALS", "Invalid phone number, account number, or password"));
        }

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

        if (Boolean.TRUE.equals(user.getTwoFactorEnabled())) {
            return ResponseEntity.ok(new ApiResponse<>(
                    true,
                    "Two-factor authentication required. Please verify with your 6-digit code via /api/v1/auth/2fa/verify.",
                    UserAccountDto.fromEntity(user),
                    "2FA_REQUIRED",
                    Instant.now()
            ));
        }

        eventPublisher.publish(UserSecurityEvent.now(
                user.getId(), user.getPhoneNumber(), "LOGIN", "Successful login"
        ));

        return ResponseEntity.ok(ApiResponse.ok("Login successful", UserAccountDto.fromEntity(user)));
    }

    @PostMapping("/auth/change-password")
    @Operation(summary = "Change Password", description = "Securely changes user password by verifying the current password and saving the new password as a salted BCrypt hash.")
    public ResponseEntity<ApiResponse<UserAccountDto>> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        if (request.oldPassword() == null || request.oldPassword().isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("INVALID_CURRENT_PASSWORD", "Current password must be provided"));
        }
        if (request.newPassword() == null || request.newPassword().trim().length() < 6) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("INVALID_NEW_PASSWORD", "New password must be at least 6 characters long"));
        }

        Optional<UserAccountEntity> userOpt = Optional.empty();
        if (request.userId() != null) {
            userOpt = repository.findById(request.userId());
        }
        if (userOpt.isEmpty() && request.identifier() != null && !request.identifier().isBlank()) {
            userOpt = findUserByIdentifier(request.identifier().trim());
        }

        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("USER_NOT_FOUND", "User account not found"));
        }

        UserAccountEntity user = userOpt.get();

        boolean oldMatches = false;
        if (isBcryptHash(user.getPassword())) {
            oldMatches = passwordEncoder.matches(request.oldPassword(), user.getPassword());
        } else {
            oldMatches = user.getPassword().equals(request.oldPassword());
        }

        if (!oldMatches) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("CURRENT_PASSWORD_MISMATCH", "Current password does not match our records"));
        }

        String trimmedNewPassword = request.newPassword().trim();
        if ((isBcryptHash(user.getPassword()) && passwordEncoder.matches(trimmedNewPassword, user.getPassword()))
                || user.getPassword().equals(trimmedNewPassword)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("SAME_PASSWORD", "New password cannot be the same as the current password"));
        }

        String newHashedPassword = passwordEncoder.encode(trimmedNewPassword);
        user.setPassword(newHashedPassword);
        UserAccountEntity saved = repository.save(user);

        eventPublisher.publish(UserSecurityEvent.now(
                user.getId(), user.getPhoneNumber(), "PASSWORD_CHANGE", "Password successfully changed"
        ));

        return ResponseEntity.ok(ApiResponse.ok("Password successfully updated and securely hashed.", UserAccountDto.fromEntity(saved)));
    }

    @PostMapping("/accounts")
    @Operation(summary = "Admin Register User Account", description = "Allows an administrator to register and provision a new account directly.")
    public ResponseEntity<ApiResponse<UserAccountDto>> adminRegisterAccount(@Valid @RequestBody AdminCreateAccountRequest request) {
        String cleanPhone = request.phoneNumber().trim();
        if (repository.existsByPhoneNumber(cleanPhone)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.error("PHONE_EXISTS", "An account with this phone number already exists"));
        }

        if (request.password() == null || request.password().trim().length() < 6) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("INVALID_PASSWORD", "Password must be at least 6 characters long"));
        }

        UUID userId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID sourceAccountId = UUID.randomUUID();
        String accNum = generateNumericAccountNumber();
        String hashedPassword = passwordEncoder.encode(request.password().trim());

        boolean shouldActivate = request.autoActivate() == null || Boolean.TRUE.equals(request.autoActivate());
        String initialStatus = shouldActivate ? "ACTIVE" : "PENDING_APPROVAL";

        UserAccountEntity entity = UserAccountEntity.builder()
                .id(userId)
                .fullName(request.fullName().trim())
                .phoneNumber(cleanPhone)
                .email(request.email() != null ? request.email().trim() : "")
                .password(hashedPassword)
                .role(request.role() != null && !request.role().isBlank() ? request.role().toUpperCase() : "CUSTOMER")
                .accountNumber(accNum)
                .customerId(customerId)
                .sourceAccountId(sourceAccountId)
                .status(initialStatus)
                .createdAt(Instant.now())
                .approvedAt(shouldActivate ? Instant.now() : null)
                .approvedBy(shouldActivate ? "admin" : null)
                .build();

        UserAccountEntity saved = repository.save(entity);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("User account successfully registered" + (shouldActivate ? " and activated!" : "!"), UserAccountDto.fromEntity(saved)));
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
        int pageIndex = Math.max(0, page);
        int pageSize = (size != null && size > 0) ? size : ((limit != null && limit > 0) ? limit : 10);

        String validSortBy = switch (sortBy != null ? sortBy.trim() : "createdAt") {
            case "fullName" -> "fullName";
            case "accountNumber" -> "accountNumber";
            case "phoneNumber" -> "phoneNumber";
            case "status" -> "status";
            default -> "createdAt";
        };
        Sort.Direction sortDir = "asc".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(pageIndex, pageSize, Sort.by(sortDir, validSortBy));

        Page<UserAccountEntity> pageResult;
        if (query != null && !query.trim().isEmpty()) {
            pageResult = repository.searchAccounts(
                    "ALL".equalsIgnoreCase(status) ? "ALL" : status.toUpperCase(),
                    query.trim(),
                    pageable
            );
        } else if ("ALL".equalsIgnoreCase(status)) {
            pageResult = repository.findAll(pageable);
        } else {
            pageResult = repository.findByStatus(status.toUpperCase(), pageable);
        }

        List<UserAccountDto> dtos = pageResult.getContent().stream().map(UserAccountDto::fromEntity).toList();
        PageResponse<UserAccountDto> response = PageResponse.of(dtos, pageResult.getNumber(), pageResult.getSize(), pageResult.getTotalElements());
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping("/accounts/stats")
    @Operation(summary = "Get Account Metrics (Admin)", description = "Returns summary counts of registered, pending, active, and suspended accounts.")
    public ResponseEntity<ApiResponse<Map<String, Long>>> getAccountStats() {
        long total = repository.count();
        long pending = repository.countByStatus("PENDING_APPROVAL");
        long active = repository.countByStatus("ACTIVE");
        long suspended = repository.countByStatus("SUSPENDED") + repository.countByStatus("REJECTED");
        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "total", total,
                "pending", pending,
                "active", active,
                "suspended", suspended
        )));
    }

    @GetMapping("/accounts/lookup")
    @Cacheable(value = "account-lookups", key = "#query")
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
    @CacheEvict(value = "account-lookups", allEntries = true)
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
    @CacheEvict(value = "account-lookups", allEntries = true)
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
    @CacheEvict(value = "account-lookups", allEntries = true)
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

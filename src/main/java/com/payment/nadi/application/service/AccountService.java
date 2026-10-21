package com.payment.nadi.application.service;

import com.payment.nadi.application.dto.request.AdminCreateAccountRequest;
import com.payment.nadi.application.dto.request.ChangePasswordRequest;
import com.payment.nadi.application.dto.request.LoginRequest;
import com.payment.nadi.application.dto.request.RegisterRequest;
import com.payment.nadi.application.dto.response.PageResponse;
import com.payment.nadi.application.dto.response.UserAccountDto;
import com.payment.nadi.application.port.in.AccountUseCase;
import com.payment.nadi.application.port.out.EventPublisher;
import com.payment.nadi.common.response.ApiResponse;
import com.payment.nadi.domain.event.UserSecurityEvent;
import com.payment.nadi.domain.exception.DuplicatePaymentException;
import com.payment.nadi.domain.exception.InvalidPaymentException;
import com.payment.nadi.infrastructure.persistence.entity.UserAccountEntity;
import com.payment.nadi.infrastructure.persistence.mapper.UserAccountPersistenceMapper;
import com.payment.nadi.infrastructure.persistence.repository.SpringDataUserAccountRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class AccountService implements AccountUseCase {

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

    public AccountService(SpringDataUserAccountRepository repository,
                          PasswordEncoder passwordEncoder,
                          EventPublisher eventPublisher) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.eventPublisher = eventPublisher;
    }

    private boolean isBcryptHash(String password) {
        if (password == null) return false;
        return password.matches("^\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53}$");
    }

    private boolean isNumericAccountNumber(String accNum) {
        return accNum != null && accNum.matches("^0\\d{7}$");
    }

    private String generateNumericAccountNumber() {
        String accNum;
        do {
            int randomPart = ThreadLocalRandom.current().nextInt(1_000_000);
            accNum = String.format("00%06d", randomPart);
        } while (repository.existsByAccountNumber(accNum));
        return accNum;
    }

    public Optional<UserAccountEntity> findUserByIdentifier(String identifier) {
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

    @Override
    @Transactional
    public UserAccountDto register(RegisterRequest request) {
        String cleanPhone = request.phoneNumber().trim();
        if (repository.existsByPhoneNumber(cleanPhone)) {
            throw new DuplicatePaymentException("An account with this phone number already exists");
        }

        if (request.password() == null || request.password().trim().length() < 6) {
            throw new InvalidPaymentException("Password must be at least 6 characters long");
        }

        UUID userId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID sourceAccountId = UUID.randomUUID();
        String accNum = generateNumericAccountNumber();
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
        return UserAccountPersistenceMapper.toDto(saved);
    }

    @Override
    @Transactional
    public ApiResponse<UserAccountDto> login(LoginRequest request) {
        String idStr = request.identifier().trim();
        Optional<UserAccountEntity> userOpt = findUserByIdentifier(idStr);

        if (userOpt.isEmpty()) {
            return ApiResponse.error("INVALID_CREDENTIALS", "Invalid phone number, account number, or password");
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
            return ApiResponse.error("INVALID_CREDENTIALS", "Invalid phone number, account number, or password");
        }

        if ("PENDING_APPROVAL".equalsIgnoreCase(user.getStatus())) {
            return new ApiResponse<>(
                    false,
                    "Account is PENDING ADMIN APPROVAL. You cannot log in or perform transactions until an administrator approves your account.",
                    UserAccountPersistenceMapper.toDto(user),
                    "PENDING_APPROVAL",
                    Instant.now()
            );
        }

        if ("SUSPENDED".equalsIgnoreCase(user.getStatus()) || "REJECTED".equalsIgnoreCase(user.getStatus())) {
            return ApiResponse.error("ACCOUNT_LOCKED", "Your account has been suspended or rejected. Please contact administrator.");
        }

        if (Boolean.TRUE.equals(user.getTwoFactorEnabled())) {
            return new ApiResponse<>(
                    true,
                    "Two-factor authentication required. Please verify with your 6-digit code via /api/v1/auth/2fa/verify.",
                    UserAccountPersistenceMapper.toDto(user),
                    "2FA_REQUIRED",
                    Instant.now()
            );
        }

        eventPublisher.publish(UserSecurityEvent.now(
                user.getId(), user.getPhoneNumber(), "LOGIN", "Successful login"
        ));

        return ApiResponse.ok("Login successful", UserAccountPersistenceMapper.toDto(user));
    }

    @Override
    @Transactional
    public UserAccountDto changePassword(ChangePasswordRequest request) {
        if (request.oldPassword() == null || request.oldPassword().isBlank()) {
            throw new InvalidPaymentException("Current password must be provided");
        }
        if (request.newPassword() == null || request.newPassword().trim().length() < 6) {
            throw new InvalidPaymentException("New password must be at least 6 characters long");
        }

        Optional<UserAccountEntity> userOpt = Optional.empty();
        if (request.userId() != null) {
            userOpt = repository.findById(request.userId());
        }
        if (userOpt.isEmpty() && request.identifier() != null && !request.identifier().isBlank()) {
            userOpt = findUserByIdentifier(request.identifier().trim());
        }

        if (userOpt.isEmpty()) {
            throw new InvalidPaymentException("User account not found");
        }

        UserAccountEntity user = userOpt.get();

        boolean oldMatches = false;
        if (isBcryptHash(user.getPassword())) {
            oldMatches = passwordEncoder.matches(request.oldPassword(), user.getPassword());
        } else {
            oldMatches = user.getPassword().equals(request.oldPassword());
        }

        if (!oldMatches) {
            throw new InvalidPaymentException("Current password does not match our records");
        }

        String trimmedNewPassword = request.newPassword().trim();
        if ((isBcryptHash(user.getPassword()) && passwordEncoder.matches(trimmedNewPassword, user.getPassword()))
                || user.getPassword().equals(trimmedNewPassword)) {
            throw new InvalidPaymentException("New password cannot be the same as the current password");
        }

        String newHashedPassword = passwordEncoder.encode(trimmedNewPassword);
        user.setPassword(newHashedPassword);
        UserAccountEntity saved = repository.save(user);

        eventPublisher.publish(UserSecurityEvent.now(
                user.getId(), user.getPhoneNumber(), "PASSWORD_CHANGE", "Password successfully changed"
        ));

        return UserAccountPersistenceMapper.toDto(saved);
    }

    @Override
    @Transactional
    public UserAccountDto adminRegisterAccount(AdminCreateAccountRequest request) {
        String cleanPhone = request.phoneNumber().trim();
        if (repository.existsByPhoneNumber(cleanPhone)) {
            throw new DuplicatePaymentException("An account with this phone number already exists");
        }

        if (request.password() == null || request.password().trim().length() < 6) {
            throw new InvalidPaymentException("Password must be at least 6 characters long");
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
        return UserAccountPersistenceMapper.toDto(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<UserAccountDto> listAccounts(String status, int page, Integer size, Integer limit, String query, String sortBy, String direction) {
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

        List<UserAccountDto> dtos = pageResult.getContent().stream().map(UserAccountPersistenceMapper::toDto).toList();
        return PageResponse.of(dtos, pageResult.getNumber(), pageResult.getSize(), pageResult.getTotalElements());
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Long> getAccountStats() {
        long total = repository.count();
        long pending = repository.countByStatus("PENDING_APPROVAL");
        long active = repository.countByStatus("ACTIVE");
        long suspended = repository.countByStatus("SUSPENDED") + repository.countByStatus("REJECTED");
        return Map.of(
                "total", total,
                "pending", pending,
                "active", active,
                "suspended", suspended
        );
    }

    @Override
    @Transactional(readOnly = true)
    public UserAccountDto lookupAccount(String query) {
        Optional<UserAccountEntity> userOpt = findUserByIdentifier(query.trim());
        if (userOpt.isEmpty()) {
            throw new InvalidPaymentException("No account found matching: " + query);
        }
        UserAccountEntity user = userOpt.get();
        if (!"ACTIVE".equalsIgnoreCase(user.getStatus())) {
            throw new InvalidPaymentException("Recipient account is currently " + user.getStatus() + " and cannot receive funds.");
        }
        return UserAccountPersistenceMapper.toDto(user);
    }

    @Override
    @Transactional
    public UserAccountDto approveAccount(UUID id) {
        UserAccountEntity user = repository.findById(id)
                .orElseThrow(() -> new InvalidPaymentException("Account not found: " + id));
        user.setStatus("ACTIVE");
        user.setApprovedAt(Instant.now());
        user.setApprovedBy("admin");
        UserAccountEntity saved = repository.save(user);
        return UserAccountPersistenceMapper.toDto(saved);
    }

    @Override
    @Transactional
    public UserAccountDto suspendAccount(UUID id) {
        UserAccountEntity user = repository.findById(id)
                .orElseThrow(() -> new InvalidPaymentException("Account not found: " + id));
        user.setStatus("SUSPENDED");
        UserAccountEntity saved = repository.save(user);
        return UserAccountPersistenceMapper.toDto(saved);
    }

    @Override
    @Transactional
    public UserAccountDto activateAccount(UUID id) {
        UserAccountEntity user = repository.findById(id)
                .orElseThrow(() -> new InvalidPaymentException("Account not found: " + id));
        user.setStatus("ACTIVE");
        UserAccountEntity saved = repository.save(user);
        return UserAccountPersistenceMapper.toDto(saved);
    }
}

package com.payment.nadi.application.service;

import com.payment.nadi.application.dto.response.ActivityLogDto;
import com.payment.nadi.application.dto.response.PageResponse;
import com.payment.nadi.infrastructure.persistence.entity.NotificationEntity;
import com.payment.nadi.infrastructure.persistence.entity.PaymentEntity;
import com.payment.nadi.infrastructure.persistence.entity.UserAccountEntity;
import com.payment.nadi.infrastructure.persistence.repository.SpringDataNotificationRepository;
import com.payment.nadi.infrastructure.persistence.repository.SpringDataPaymentRepository;
import com.payment.nadi.infrastructure.persistence.repository.SpringDataUserAccountRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class ActivityLogService {

    private final SpringDataUserAccountRepository accountRepository;
    private final SpringDataPaymentRepository paymentRepository;
    private final SpringDataNotificationRepository notificationRepository;

    public ActivityLogService(
            SpringDataUserAccountRepository accountRepository,
            SpringDataPaymentRepository paymentRepository,
            SpringDataNotificationRepository notificationRepository
    ) {
        this.accountRepository = accountRepository;
        this.paymentRepository = paymentRepository;
        this.notificationRepository = notificationRepository;
    }

    public PageResponse<ActivityLogDto> getActivities(
            int page,
            int size,
            String category,
            String status,
            String query
    ) {
        int safePage = Math.max(0, page);
        int safeSize = size > 0 ? size : 10;

        // Bounded collection: Query only recent records rather than full table scans to safeguard JVM heap
        int maxFetch = Math.min(500, Math.max(100, (safePage + 1) * safeSize * 3));
        List<ActivityLogDto> allActivities = collectRecentActivities(maxFetch);

        // 1. Sort by timestamp descending
        allActivities.sort(Comparator.comparing(ActivityLogDto::timestamp, Comparator.reverseOrder()));

        // 2. Filter
        List<ActivityLogDto> filtered = allActivities.stream()
                .filter(a -> {
                    if (category != null && !category.isBlank() && !"ALL".equalsIgnoreCase(category)) {
                        if (!a.category().equalsIgnoreCase(category)) {
                            return false;
                        }
                    }
                    if (status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status)) {
                        if (!a.status().equalsIgnoreCase(status)) {
                            return false;
                        }
                    }
                    if (query != null && !query.isBlank()) {
                        String q = query.trim().toLowerCase();
                        boolean match = a.id().toLowerCase().contains(q)
                                || a.actor().toLowerCase().contains(q)
                                || a.action().toLowerCase().contains(q)
                                || a.actionLabel().toLowerCase().contains(q)
                                || a.targetEntity().toLowerCase().contains(q)
                                || a.details().toLowerCase().contains(q);
                        if (!match) return false;
                    }
                    return true;
                })
                .toList();

        long total = filtered.size();
        int skip = safePage * safeSize;

        List<ActivityLogDto> pagedItems = filtered.stream()
                .skip(skip)
                .limit(safeSize)
                .toList();

        return PageResponse.of(pagedItems, safePage, safeSize, total);
    }

    public Map<String, Long> getActivityStats() {
        long accountsCount = accountRepository.count();
        long paymentsCount = paymentRepository.count();
        long notifsCount = notificationRepository.count();
        long total = accountsCount + paymentsCount + notifsCount;

        long pendingAccounts = accountRepository.countByStatus("PENDING_APPROVAL");
        long activeAccounts = accountRepository.countByStatus("ACTIVE");

        return Map.of(
                "total", total,
                "governance", activeAccounts + pendingAccounts,
                "financial", paymentsCount,
                "security", notifsCount,
                "userManagement", accountsCount,
                "system", notifsCount
        );
    }

    private List<ActivityLogDto> collectRecentActivities(int limit) {
        List<ActivityLogDto> list = new ArrayList<>();
        Pageable recent = PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "createdAt"));

        // 1. Collect recent User Accounts
        List<UserAccountEntity> accounts = accountRepository.findAll(recent).getContent();
        for (UserAccountEntity u : accounts) {
            String shortId = u.getId().toString().substring(0, 8).toUpperCase();

            // Registration activity
            if (u.getCreatedAt() != null) {
                list.add(new ActivityLogDto(
                        "ACT-REG-" + shortId,
                        u.getCreatedAt().toString(),
                        u.getFullName() != null && !u.getFullName().isBlank() ? u.getFullName() : "New User",
                        u.getRole() != null ? u.getRole() : "CUSTOMER",
                        "USER_REGISTERED",
                        "User Account Provisioned",
                        "USER_MANAGEMENT",
                        "SUCCESS",
                        "Account #" + (u.getAccountNumber() != null ? u.getAccountNumber() : "—") + " (" + u.getFullName() + ")",
                        "Registered user account profile for " + u.getFullName() + " with phone number " + u.getPhoneNumber() + ".",
                        "192.168.1.104",
                        "Admin Web Console"
                ));
            }

            // Approval activity
            if (u.getApprovedAt() != null) {
                list.add(new ActivityLogDto(
                        "ACT-APP-" + shortId,
                        u.getApprovedAt().toString(),
                        u.getApprovedBy() != null && !u.getApprovedBy().isBlank() ? u.getApprovedBy() : "System Administrator",
                        "ADMIN",
                        "ACCOUNT_APPROVED",
                        "Account Approved & Activated",
                        "GOVERNANCE",
                        "SUCCESS",
                        "Account #" + (u.getAccountNumber() != null ? u.getAccountNumber() : "—") + " (" + u.getFullName() + ")",
                        "Approved KYC credentials and enabled active transacting capabilities for " + u.getFullName() + ".",
                        "192.168.1.104",
                        "Admin Web Console"
                ));
            }

            // Suspended activity
            if ("SUSPENDED".equalsIgnoreCase(u.getStatus())) {
                list.add(new ActivityLogDto(
                        "ACT-SUS-" + shortId,
                        (u.getCreatedAt() != null ? u.getCreatedAt() : Instant.now()).toString(),
                        "Security Engine",
                        "SECURITY",
                        "ACCOUNT_SUSPENDED",
                        "Account Suspended",
                        "GOVERNANCE",
                        "WARNING",
                        "Account #" + (u.getAccountNumber() != null ? u.getAccountNumber() : "—") + " (" + u.getFullName() + ")",
                        "Administrative hold applied on wallet account #" + u.getAccountNumber() + ". Restrictions enforced.",
                        "127.0.0.1",
                        "Risk Engine"
                ));
            }
        }

        // 2. Collect recent Payments
        List<PaymentEntity> payments = paymentRepository.findAll(recent).getContent();
        for (PaymentEntity p : payments) {
            String shortId = p.getId().toString().substring(0, 8).toUpperCase();
            String pType = p.getType() != null ? p.getType().name() : "PAYMENT";
            String pStatus = p.getStatus() != null ? p.getStatus().name() : "SUCCESS";
            String amtStr = p.getAmount() + " " + (p.getCurrency() != null ? p.getCurrency() : "ETB");

            String action = "PAYMENT_" + pType;
            String label = switch (pType) {
                case "TOP_UP" -> "Top-Up Balance Completed";
                case "TRANSFER" -> "Fund Transfer Processed";
                case "WITHDRAW", "WITHDRAWAL" -> "Funds Withdrawn";
                default -> "Payment Transaction";
            };

            String details = (p.getDescription() != null && !p.getDescription().isBlank())
                    ? p.getDescription()
                    : (label + " for amount " + amtStr);

            if (p.getCreatedAt() != null) {
                list.add(new ActivityLogDto(
                        "ACT-PAY-" + shortId,
                        p.getCreatedAt().toString(),
                        p.getSourceSystem() != null && !p.getSourceSystem().isBlank() ? p.getSourceSystem() : "Payment Engine",
                        "SYSTEM",
                        action,
                        label,
                        "FINANCIAL",
                        pStatus,
                        "Ref: " + (p.getReference() != null ? p.getReference() : shortId) + " (" + amtStr + ")",
                        details + " [Provider: " + (p.getProvider() != null ? p.getProvider().name() : "NADI_CORE") + "]",
                        "10.0.4.12",
                        "Transaction Processor"
                ));
            }
        }

        // 3. Collect recent Notifications
        List<NotificationEntity> notifs = notificationRepository.findAll(recent).getContent();
        for (NotificationEntity n : notifs) {
            String shortId = n.getId().toString().substring(0, 8).toUpperCase();
            String channel = n.getChannel() != null ? n.getChannel().name() : "SMS";
            String recipient = n.getPhoneNumber() != null ? n.getPhoneNumber() : ("User " + n.getUserId());

            if (n.getCreatedAt() != null) {
                list.add(new ActivityLogDto(
                        "ACT-NTF-" + shortId,
                        n.getCreatedAt().toString(),
                        "Notification Gateway",
                        "SYSTEM",
                        "ALERT_DISPATCHED",
                        channel + " Alert Broadcast",
                        "SECURITY",
                        "INFO",
                        "Recipient: " + recipient,
                        "Broadcast \"" + n.getTitle() + "\": " + n.getMessage(),
                        "127.0.0.1",
                        "Notification Dispatcher"
                ));
            }
        }

        return list;
    }
}

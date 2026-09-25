package com.payment.nadi.application.service;

import com.payment.nadi.application.port.in.PaymentCallbackUseCase;
import com.payment.nadi.application.port.out.EventPublisher;
import com.payment.nadi.application.port.out.PaymentProvider;
import com.payment.nadi.application.port.out.PaymentProviderResolver;
import com.payment.nadi.application.port.out.WalletService;
import com.payment.nadi.application.port.out.dto.PaymentStatusResult;
import com.payment.nadi.domain.event.PaymentFailedEvent;
import com.payment.nadi.domain.model.Payment;
import com.payment.nadi.domain.model.PaymentStatus;
import com.payment.nadi.domain.model.PaymentType;
import com.payment.nadi.domain.repository.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Background worker responsible for reconciling in-doubt PENDING/PROCESSING transactions
 * with upstream payment gateways and expiring stale transactions past their TTL.
 * Implements Section 15 of Architecture.md.
 */
@Service
public class PaymentReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(PaymentReconciliationService.class);

    private static final Duration IN_DOUBT_THRESHOLD = Duration.ofMinutes(3);
    private static final Duration EXPIRATION_TIMEOUT = Duration.ofMinutes(30);

    private final PaymentRepository paymentRepository;
    private final PaymentProviderResolver providerResolver;
    private final PaymentCallbackUseCase callbackUseCase;
    private final WalletService walletService;
    private final EventPublisher eventPublisher;

    public PaymentReconciliationService(
            PaymentRepository paymentRepository,
            PaymentProviderResolver providerResolver,
            PaymentCallbackUseCase callbackUseCase,
            WalletService walletService,
            EventPublisher eventPublisher
    ) {
        this.paymentRepository = paymentRepository;
        this.providerResolver = providerResolver;
        this.callbackUseCase = callbackUseCase;
        this.walletService = walletService;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Periodically queries and settles in-doubt payments.
     * Runs every 5 minutes with a 1-minute initial startup delay.
     */
    @Scheduled(fixedDelay = 300000, initialDelay = 60000)
    public void reconcilePendingPayments() {
        log.info("Starting scheduled payment reconciliation run...");

        List<Payment> pendingPayments = paymentRepository.findByStatus(PaymentStatus.PENDING);
        List<Payment> processingPayments = paymentRepository.findByStatus(PaymentStatus.PROCESSING);

        int totalFound = pendingPayments.size() + processingPayments.size();
        if (totalFound == 0) {
            log.debug("Reconciliation run completed: No in-doubt payments found.");
            return;
        }

        log.info("Reconciliation run found {} in-doubt payments to evaluate.", totalFound);
        Instant now = Instant.now();

        for (Payment payment : pendingPayments) {
            reconcileSinglePayment(payment, now);
        }
        for (Payment payment : processingPayments) {
            reconcileSinglePayment(payment, now);
        }

        log.info("Payment reconciliation run completed successfully.");
    }

    @Transactional
    public void reconcileSinglePayment(Payment payment, Instant now) {
        // Allow at least 3 minutes before checking to give standard callbacks time to arrive
        if (payment.getCreatedAt() != null && payment.getCreatedAt().isAfter(now.minus(IN_DOUBT_THRESHOLD))) {
            return;
        }

        log.info("Reconciling in-doubt payment reference: {} (created: {})", payment.getReference(), payment.getCreatedAt());

        if (payment.getProvider() != null && payment.getProviderReference() != null && !payment.getProviderReference().isBlank()) {
            try {
                PaymentProvider provider = providerResolver.resolve(payment.getProvider());
                PaymentStatusResult statusResult = provider.checkStatus(payment.getProviderReference());

                if (statusResult.status() == PaymentStatus.SUCCESS) {
                    log.info("Reconciliation confirmed SUCCESS for payment: {}", payment.getReference());
                    callbackUseCase.processCallback(
                            payment.getProvider(),
                            payment.getReference(),
                            payment.getProviderReference(),
                            PaymentStatus.SUCCESS,
                            payment.getAmount(),
                            "RECONCILIATION_SYNC",
                            true
                    );
                    return;
                } else if (statusResult.status() == PaymentStatus.FAILED) {
                    log.info("Reconciliation confirmed FAILED for payment: {}", payment.getReference());
                    callbackUseCase.processCallback(
                            payment.getProvider(),
                            payment.getReference(),
                            payment.getProviderReference(),
                            PaymentStatus.FAILED,
                            payment.getAmount(),
                            "RECONCILIATION_SYNC",
                            true
                    );
                    return;
                }
            } catch (Exception ex) {
                log.warn("Error querying provider status during reconciliation for {}: {}",
                        payment.getReference(), ex.getMessage());
            }
        }

        // If status remains unconfirmed and created more than 30 minutes ago, mark as EXPIRED
        if (payment.getCreatedAt() != null && payment.getCreatedAt().isBefore(now.minus(EXPIRATION_TIMEOUT))) {
            expireStalePayment(payment);
        }
    }

    private void expireStalePayment(Payment payment) {
        log.warn("Payment {} exceeded expiration timeout ({} mins). Marking as EXPIRED.",
                payment.getReference(), EXPIRATION_TIMEOUT.toMinutes());

        try {
            payment.markAsExpired();
            paymentRepository.save(payment);

            // If withdrawal was held in wallet, revert funds back to user
            if (payment.getType() == PaymentType.WITHDRAW && payment.getSourceAccountId() != null) {
                walletService.credit(
                        payment.getSourceAccountId(),
                        payment.getAmount(),
                        payment.getReference(),
                        "Revert expired withdrawal: " + payment.getReference()
                );
            }

            eventPublisher.publish(PaymentFailedEvent.now(
                    payment.getId(),
                    payment.getReference(),
                    payment.getType(),
                    payment.getAmount(),
                    payment.getCustomerId(),
                    "Payment expired after 30 minutes without provider confirmation"
            ));
        } catch (Exception ex) {
            log.error("Failed to expire payment {}: {}", payment.getReference(), ex.getMessage(), ex);
        }
    }
}

package com.payment.nadi.application.service;

import com.payment.nadi.application.port.in.PaymentCallbackUseCase;
import com.payment.nadi.application.port.out.EventPublisher;
import com.payment.nadi.application.port.out.WalletService;
import com.payment.nadi.domain.event.PaymentCompletedEvent;
import com.payment.nadi.domain.event.PaymentFailedEvent;
import com.payment.nadi.domain.model.Money;
import com.payment.nadi.domain.model.Payment;
import com.payment.nadi.domain.model.PaymentAttempt;
import com.payment.nadi.domain.model.PaymentProviderType;
import com.payment.nadi.domain.model.PaymentStatus;
import com.payment.nadi.domain.model.PaymentType;
import com.payment.nadi.domain.model.Transaction;
import com.payment.nadi.domain.repository.PaymentAttemptRepository;
import com.payment.nadi.domain.repository.PaymentRepository;
import com.payment.nadi.domain.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class PaymentCallbackService implements PaymentCallbackUseCase {

    private static final Logger log = LoggerFactory.getLogger(PaymentCallbackService.class);

    private final PaymentRepository paymentRepository;
    private final TransactionRepository transactionRepository;
    private final PaymentAttemptRepository attemptRepository;
    private final WalletService walletService;
    private final EventPublisher eventPublisher;

    public PaymentCallbackService(
            PaymentRepository paymentRepository,
            TransactionRepository transactionRepository,
            PaymentAttemptRepository attemptRepository,
            WalletService walletService,
            EventPublisher eventPublisher
    ) {
        this.paymentRepository = paymentRepository;
        this.transactionRepository = transactionRepository;
        this.attemptRepository = attemptRepository;
        this.walletService = walletService;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public CallbackResult processCallback(
            PaymentProviderType providerType,
            String paymentReference,
            String providerReference,
            PaymentStatus reportedStatus,
            Money reportedAmount,
            String rawPayload,
            boolean signatureVerified
    ) {
        log.info("Processing callback from {} for reference: {}, reported status: {}",
                providerType, paymentReference, reportedStatus);

        if (!signatureVerified) {
            log.error("Callback signature verification failed for reference: {}", paymentReference);
            return new CallbackResult(false, "Invalid signature", paymentReference, null);
        }

        // 1. Locate payment
        Optional<Payment> paymentOpt = paymentRepository.findByReference(paymentReference);
        if (paymentOpt.isEmpty() && providerReference != null) {
            paymentOpt = paymentRepository.findByProviderReference(providerReference);
        }

        if (paymentOpt.isEmpty()) {
            log.warn("Callback received for non-existent payment reference: {}", paymentReference);
            return new CallbackResult(false, "Payment not found", paymentReference, null);
        }

        Payment payment = paymentOpt.get();

        // 2. Audit callback attempt
        int attemptNum = attemptRepository.countByPaymentId(payment.getId()) + 1;
        attemptRepository.save(PaymentAttempt.record(
                payment.getId(),
                providerType,
                attemptNum,
                "CALLBACK_" + reportedStatus,
                paymentReference,
                providerReference,
                null,
                "Callback received: " + reportedStatus
        ));

        // 3. Idempotent check: If already in terminal status (SUCCESS / FAILED), acknowledge duplicate callback
        if (payment.getStatus().isTerminal()) {
            log.info("Callback for payment {} already reached terminal state: {}. Skipping duplicate transition.",
                    payment.getReference(), payment.getStatus());
            return new CallbackResult(true, "Duplicate callback acknowledged", payment.getReference(), payment.getStatus());
        }

        // 4. Validate Amount
        if (reportedAmount != null && !payment.getAmount().equals(reportedAmount)) {
            log.error("Callback amount mismatch for {}: expected {}, received {}",
                    payment.getReference(), payment.getAmount(), reportedAmount);
            payment.markAsFailed("Amount mismatch on callback: " + reportedAmount + " != " + payment.getAmount());
            paymentRepository.save(payment);
            eventPublisher.publish(PaymentFailedEvent.now(
                    payment.getId(),
                    payment.getReference(),
                    payment.getType(),
                    payment.getAmount(),
                    payment.getCustomerId(),
                    "Amount mismatch"
            ));
            return new CallbackResult(false, "Amount mismatch", payment.getReference(), PaymentStatus.FAILED);
        }

        // 5. Update Status according to provider callback
        if (reportedStatus == PaymentStatus.SUCCESS) {
            payment.markAsSuccess(providerReference);
            paymentRepository.save(payment);

            // Execute balance credit if top-up
            if (payment.getType() == PaymentType.TOP_UP && payment.getDestinationAccountId() != null) {
                walletService.credit(payment.getDestinationAccountId(), payment.getAmount(), payment.getReference(), "Top-up: " + payment.getReference());
            }

            // Create completed transaction record
            Transaction tx = Transaction.create(
                    payment.getId(),
                    PaymentReferenceGenerator.generateTransactionReference(),
                    payment.getType(),
                    payment.getAmount(),
                    Money.zero(payment.getAmount().currency()),
                    payment.getDestinationAccountId() != null ? payment.getDestinationAccountId() : payment.getSourceAccountId(),
                    "Payment settled via " + providerType,
                    PaymentStatus.SUCCESS
            );
            transactionRepository.save(tx);

            eventPublisher.publish(PaymentCompletedEvent.now(
                    payment.getId(),
                    payment.getReference(),
                    payment.getType(),
                    payment.getAmount(),
                    payment.getCustomerId(),
                    providerReference,
                    payment.getSourceSystem()
            ));

            log.info("Payment {} successfully settled via callback.", payment.getReference());
            return new CallbackResult(true, "Payment settled successfully", payment.getReference(), PaymentStatus.SUCCESS);

        } else if (reportedStatus == PaymentStatus.FAILED || reportedStatus == PaymentStatus.CANCELLED) {
            payment.markAsFailed("Payment marked failed by provider callback");
            paymentRepository.save(payment);

            // If withdrawal failed, revert reserved funds to customer
            if (payment.getType() == PaymentType.WITHDRAW && payment.getSourceAccountId() != null) {
                walletService.credit(payment.getSourceAccountId(), payment.getAmount(), payment.getReference(), "Withdrawal failed revert: " + payment.getReference());
            }

            eventPublisher.publish(PaymentFailedEvent.now(
                    payment.getId(),
                    payment.getReference(),
                    payment.getType(),
                    payment.getAmount(),
                    payment.getCustomerId(),
                    "Provider reported failure"
            ));

            log.info("Payment {} marked failed via callback.", payment.getReference());
            return new CallbackResult(true, "Payment marked failed", payment.getReference(), PaymentStatus.FAILED);
        }

        return new CallbackResult(true, "Callback processed", payment.getReference(), payment.getStatus());
    }
}

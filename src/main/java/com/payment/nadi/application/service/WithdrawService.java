package com.payment.nadi.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payment.nadi.application.dto.request.WithdrawRequest;
import com.payment.nadi.application.dto.response.PaymentResponse;
import com.payment.nadi.application.port.in.WithdrawUseCase;
import com.payment.nadi.application.port.out.EventPublisher;
import com.payment.nadi.application.port.out.IdempotencyStore;
import com.payment.nadi.application.port.out.PaymentProvider;
import com.payment.nadi.application.port.out.PaymentProviderResolver;
import com.payment.nadi.application.port.out.WalletService;
import com.payment.nadi.application.port.out.dto.PaymentInitiationResult;
import com.payment.nadi.application.port.out.dto.PaymentProviderRequest;
import com.payment.nadi.domain.event.PaymentCompletedEvent;
import com.payment.nadi.domain.event.PaymentFailedEvent;
import com.payment.nadi.domain.event.PaymentInitiatedEvent;
import com.payment.nadi.domain.exception.InsufficientBalanceException;
import com.payment.nadi.domain.exception.PaymentException;
import com.payment.nadi.domain.model.Money;
import com.payment.nadi.domain.model.Payment;
import com.payment.nadi.domain.model.PaymentAttempt;
import com.payment.nadi.domain.model.PaymentStatus;
import com.payment.nadi.domain.model.PaymentType;
import com.payment.nadi.domain.model.Transaction;
import com.payment.nadi.domain.repository.PaymentAttemptRepository;
import com.payment.nadi.domain.repository.PaymentRepository;
import com.payment.nadi.domain.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;

@Service
public class WithdrawService implements WithdrawUseCase {

    private static final Logger log = LoggerFactory.getLogger(WithdrawService.class);

    private final PaymentRepository paymentRepository;
    private final TransactionRepository transactionRepository;
    private final PaymentAttemptRepository attemptRepository;
    private final PaymentProviderResolver providerResolver;
    private final WalletService walletService;
    private final EventPublisher eventPublisher;
    private final IdempotencyStore idempotencyStore;
    private final ObjectMapper objectMapper;
    private final TransactionOperations txOperations;

    @Autowired
    public WithdrawService(
            PaymentRepository paymentRepository,
            TransactionRepository transactionRepository,
            PaymentAttemptRepository attemptRepository,
            PaymentProviderResolver providerResolver,
            WalletService walletService,
            EventPublisher eventPublisher,
            IdempotencyStore idempotencyStore,
            ObjectMapper objectMapper,
            @Autowired(required = false) PlatformTransactionManager transactionManager
    ) {
        this.paymentRepository = paymentRepository;
        this.transactionRepository = transactionRepository;
        this.attemptRepository = attemptRepository;
        this.providerResolver = providerResolver;
        this.walletService = walletService;
        this.eventPublisher = eventPublisher;
        this.idempotencyStore = idempotencyStore;
        this.objectMapper = objectMapper;
        this.txOperations = (transactionManager != null)
                ? new TransactionTemplate(transactionManager)
                : TransactionOperations.withoutTransaction();
    }

    public WithdrawService(
            PaymentRepository paymentRepository,
            TransactionRepository transactionRepository,
            PaymentAttemptRepository attemptRepository,
            PaymentProviderResolver providerResolver,
            WalletService walletService,
            EventPublisher eventPublisher,
            IdempotencyStore idempotencyStore,
            ObjectMapper objectMapper
    ) {
        this(paymentRepository, transactionRepository, attemptRepository, providerResolver, walletService, eventPublisher, idempotencyStore, objectMapper, null);
    }

    @Override
    public PaymentResponse initiateWithdraw(WithdrawRequest request, String idempotencyKey) {
        log.info("Processing withdrawal request for customer: {}, amount: {} {}",
                request.customerId(), request.amount(), request.currency());

        // 1. Idempotency Check
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<IdempotencyStore.IdempotencyEntry> existing = idempotencyStore.find(idempotencyKey);
            if (existing.isPresent()) {
                log.info("Idempotent withdrawal request detected for key: {}", idempotencyKey);
                try {
                    return objectMapper.readValue(existing.get().responseJson(), PaymentResponse.class);
                } catch (JsonProcessingException e) {
                    log.warn("Failed to deserialize cached idempotency response: {}", e.getMessage());
                }
            }
        }

        Money amount = Money.of(request.amount(), request.currency());

        // 2. Transaction Phase 1: Validate balance, create payment record, and reserve/debit from wallet
        Payment savedPayment = txOperations.execute(status -> {
            walletService.validateAccount(request.sourceAccountId());
            if (!walletService.hasSufficientBalance(request.sourceAccountId(), amount)) {
                throw new InsufficientBalanceException("Account " + request.sourceAccountId() + " has insufficient balance for withdrawal of " + amount);
            }

            String paymentReference = PaymentReferenceGenerator.generatePaymentReference(PaymentType.WITHDRAW);
            Payment payment = Payment.create(
                    paymentReference,
                    PaymentType.WITHDRAW,
                    amount,
                    request.customerId(),
                    request.sourceAccountId(),
                    null,
                    request.paymentMethod().type(),
                    request.sourceSystem(),
                    request.description() != null ? request.description() : "Wallet Withdrawal",
                    request.metadata()
            );
            Payment saved = paymentRepository.save(payment);

            // Reserve / Debit from wallet
            walletService.debit(request.sourceAccountId(), amount, saved.getReference(), "Withdrawal hold: " + saved.getReference());
            return saved;
        });

        // 3. Resolve Provider
        PaymentProvider provider = providerResolver.resolve(request.paymentMethod().type());
        PaymentProviderRequest providerRequest = PaymentProviderRequest.of(
                savedPayment.getId(),
                savedPayment.getReference(),
                PaymentType.WITHDRAW,
                amount,
                request.customerId(),
                request.paymentMethod().identifier(),
                savedPayment.getDescription(),
                null,
                null
        );

        // 4. Non-transactional Network I/O: External HTTP call to Telebirr B2C occurs outside DB transaction
        PaymentInitiationResult result;
        try {
            result = provider.initiateWithdraw(providerRequest);
        } catch (Exception ex) {
            log.error("Provider withdrawal failed for {}: {}", savedPayment.getReference(), ex.getMessage(), ex);
            // Settle failure and revert funds back to wallet in a committed transaction
            txOperations.executeWithoutResult(status -> {
                walletService.credit(request.sourceAccountId(), amount, savedPayment.getReference(), "Withdrawal failed revert: " + savedPayment.getReference());
                savedPayment.markAsFailed("Provider error: " + ex.getMessage());
                paymentRepository.save(savedPayment);
                attemptRepository.save(PaymentAttempt.record(
                        savedPayment.getId(),
                        provider.getProvider(),
                        1,
                        "ERROR",
                        savedPayment.getReference(),
                        null,
                        "PROVIDER_EXCEPTION",
                        ex.getMessage()
                ));
                eventPublisher.publish(PaymentFailedEvent.now(
                        savedPayment.getId(),
                        savedPayment.getReference(),
                        PaymentType.WITHDRAW,
                        amount,
                        savedPayment.getCustomerId(),
                        ex.getMessage()
                ));
            });
            throw new PaymentException("WITHDRAWAL_PROVIDER_FAILED", "Failed to initiate withdrawal: " + ex.getMessage(), ex);
        }

        // 5. Transaction Phase 2: Finalize outcome
        txOperations.executeWithoutResult(status -> {
            attemptRepository.save(PaymentAttempt.record(
                    savedPayment.getId(),
                    provider.getProvider(),
                    1,
                    result.status().name(),
                    savedPayment.getReference(),
                    result.providerReference(),
                    result.errorCode(),
                    result.errorMessage()
            ));

            if (result.status() == PaymentStatus.SUCCESS) {
                savedPayment.markAsSuccess(result.providerReference());
                paymentRepository.save(savedPayment);

                Transaction tx = Transaction.create(
                        savedPayment.getId(),
                        PaymentReferenceGenerator.generateTransactionReference(),
                        PaymentType.WITHDRAW,
                        amount,
                        Money.zero(amount.currency()),
                        request.sourceAccountId(),
                        "Withdrawal completed: " + savedPayment.getReference(),
                        PaymentStatus.SUCCESS
                );
                transactionRepository.save(tx);

                eventPublisher.publish(PaymentCompletedEvent.now(
                        savedPayment.getId(),
                        savedPayment.getReference(),
                        PaymentType.WITHDRAW,
                        amount,
                        savedPayment.getCustomerId(),
                        result.providerReference(),
                        savedPayment.getSourceSystem()
                ));
            } else if (result.status() == PaymentStatus.PENDING || result.status() == PaymentStatus.PROCESSING) {
                savedPayment.markAsPending(result.providerReference());
                paymentRepository.save(savedPayment);

                eventPublisher.publish(PaymentInitiatedEvent.now(
                        savedPayment.getId(),
                        savedPayment.getReference(),
                        PaymentType.WITHDRAW,
                        amount,
                        savedPayment.getCustomerId(),
                        provider.getProvider().name()
                ));
            } else {
                // Provider rejected withdrawal: Revert funds back to user wallet
                walletService.credit(request.sourceAccountId(), amount, savedPayment.getReference(), "Withdrawal failed revert");
                savedPayment.markAsFailed(result.errorMessage());
                paymentRepository.save(savedPayment);

                eventPublisher.publish(PaymentFailedEvent.now(
                        savedPayment.getId(),
                        savedPayment.getReference(),
                        PaymentType.WITHDRAW,
                        amount,
                        savedPayment.getCustomerId(),
                        result.errorMessage()
                ));
            }
        });

        PaymentResponse response = PaymentResponse.from(savedPayment);

        // 6. Cache Idempotency
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            try {
                idempotencyStore.save(idempotencyKey, String.valueOf(request.hashCode()), savedPayment.getId(), objectMapper.writeValueAsString(response), 1440);
            } catch (JsonProcessingException e) {
                log.warn("Failed to serialize response for idempotency caching: {}", e.getMessage());
            }
        }

        return response;
    }
}

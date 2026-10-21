package com.payment.nadi.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payment.nadi.application.dto.request.TopUpRequest;
import com.payment.nadi.application.dto.response.PaymentResponse;
import com.payment.nadi.application.port.in.TopUpUseCase;
import com.payment.nadi.application.port.out.EventPublisher;
import com.payment.nadi.application.port.out.IdempotencyStore;
import com.payment.nadi.application.port.out.PaymentProvider;
import com.payment.nadi.application.port.out.PaymentProviderResolver;
import com.payment.nadi.application.port.out.dto.PaymentInitiationResult;
import com.payment.nadi.application.port.out.dto.PaymentProviderRequest;
import com.payment.nadi.domain.event.PaymentFailedEvent;
import com.payment.nadi.domain.event.PaymentInitiatedEvent;
import com.payment.nadi.domain.exception.PaymentException;
import com.payment.nadi.domain.model.Money;
import com.payment.nadi.domain.model.Payment;
import com.payment.nadi.domain.model.PaymentAttempt;
import com.payment.nadi.domain.model.PaymentType;
import com.payment.nadi.domain.repository.PaymentAttemptRepository;
import com.payment.nadi.domain.repository.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;

@Service
public class TopUpService implements TopUpUseCase {

    private static final Logger log = LoggerFactory.getLogger(TopUpService.class);

    private final PaymentRepository paymentRepository;
    private final PaymentAttemptRepository attemptRepository;
    private final PaymentProviderResolver providerResolver;
    private final EventPublisher eventPublisher;
    private final IdempotencyStore idempotencyStore;
    private final ObjectMapper objectMapper;
    private final TransactionOperations txOperations;

    @Autowired
    public TopUpService(
            PaymentRepository paymentRepository,
            PaymentAttemptRepository attemptRepository,
            PaymentProviderResolver providerResolver,
            EventPublisher eventPublisher,
            IdempotencyStore idempotencyStore,
            ObjectMapper objectMapper,
            @Autowired(required = false) PlatformTransactionManager transactionManager
    ) {
        this.paymentRepository = paymentRepository;
        this.attemptRepository = attemptRepository;
        this.providerResolver = providerResolver;
        this.eventPublisher = eventPublisher;
        this.idempotencyStore = idempotencyStore;
        this.objectMapper = objectMapper;
        this.txOperations = (transactionManager != null)
                ? new TransactionTemplate(transactionManager)
                : TransactionOperations.withoutTransaction();
    }

    public TopUpService(
            PaymentRepository paymentRepository,
            PaymentAttemptRepository attemptRepository,
            PaymentProviderResolver providerResolver,
            EventPublisher eventPublisher,
            IdempotencyStore idempotencyStore,
            ObjectMapper objectMapper
    ) {
        this(paymentRepository, attemptRepository, providerResolver, eventPublisher, idempotencyStore, objectMapper, null);
    }

    @Override
    public PaymentResponse initiateTopUp(TopUpRequest request, String idempotencyKey) {
        log.info("Processing top-up request for customer: {}, amount: {} {}",
                request.customerId(), request.amount(), request.currency());

        // 1. Idempotency Check
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<IdempotencyStore.IdempotencyEntry> existing = idempotencyStore.find(idempotencyKey);
            if (existing.isPresent()) {
                log.info("Idempotent request detected for key: {}", idempotencyKey);
                try {
                    return objectMapper.readValue(existing.get().responseJson(), PaymentResponse.class);
                } catch (JsonProcessingException e) {
                    log.warn("Failed to deserialize cached idempotency response: {}", e.getMessage());
                }
            }
        }

        Money money = Money.of(request.amount(), request.currency());

        // 2. Transaction Phase 1: Create Initial Payment in Database
        Payment savedPayment = txOperations.execute(status -> {
            String reference = PaymentReferenceGenerator.generatePaymentReference(PaymentType.TOP_UP);
            Payment payment = Payment.create(
                    reference,
                    PaymentType.TOP_UP,
                    money,
                    request.customerId(),
                    null,
                    request.destinationAccountId(),
                    request.paymentMethod().type(),
                    request.sourceSystem(),
                    request.description() != null ? request.description() : "Wallet Top-up",
                    request.metadata()
            );
            return paymentRepository.save(payment);
        });

        // 3. Resolve Provider
        PaymentProvider provider = providerResolver.resolve(request.paymentMethod().type());

        PaymentProviderRequest providerRequest = PaymentProviderRequest.of(
                savedPayment.getId(),
                savedPayment.getReference(),
                PaymentType.TOP_UP,
                money,
                request.customerId(),
                request.paymentMethod().identifier(),
                savedPayment.getDescription(),
                request.returnUrl(),
                null
        );

        // 4. Non-transactional Network I/O: External HTTP call is made outside DB transaction to prevent HikariCP pool exhaustion
        PaymentInitiationResult result;
        try {
            result = provider.initiateTopUp(providerRequest);
        } catch (Exception ex) {
            log.error("Provider initiation failed for payment {}: {}", savedPayment.getReference(), ex.getMessage(), ex);
            txOperations.executeWithoutResult(status -> {
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
                        PaymentType.TOP_UP,
                        money,
                        savedPayment.getCustomerId(),
                        ex.getMessage()
                ));
            });
            throw new PaymentException("PROVIDER_COMMUNICATION_ERROR", "Failed to communicate with payment provider: " + ex.getMessage(), ex);
        }

        // 5. Transaction Phase 2: Persist Attempt and Finalize Payment State
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

            if (result.success()) {
                savedPayment.markAsPending(result.providerReference());
                paymentRepository.save(savedPayment);

                eventPublisher.publish(PaymentInitiatedEvent.now(
                        savedPayment.getId(),
                        savedPayment.getReference(),
                        PaymentType.TOP_UP,
                        money,
                        savedPayment.getCustomerId(),
                        provider.getProvider().name()
                ));
            } else {
                savedPayment.markAsFailed(result.errorMessage());
                paymentRepository.save(savedPayment);

                eventPublisher.publish(PaymentFailedEvent.now(
                        savedPayment.getId(),
                        savedPayment.getReference(),
                        PaymentType.TOP_UP,
                        money,
                        savedPayment.getCustomerId(),
                        result.errorMessage()
                ));
            }
        });

        PaymentResponse response = PaymentResponse.from(savedPayment, result.redirectUrl());

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

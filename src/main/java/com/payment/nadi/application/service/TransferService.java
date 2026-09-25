package com.payment.nadi.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payment.nadi.application.dto.request.TransferRequest;
import com.payment.nadi.application.dto.response.PaymentResponse;
import com.payment.nadi.application.port.in.TransferUseCase;
import com.payment.nadi.application.port.out.EventPublisher;
import com.payment.nadi.application.port.out.IdempotencyStore;
import com.payment.nadi.application.port.out.WalletService;
import com.payment.nadi.domain.event.TransferCompletedEvent;
import com.payment.nadi.domain.exception.InsufficientBalanceException;
import com.payment.nadi.domain.exception.InvalidPaymentException;
import com.payment.nadi.domain.model.Money;
import com.payment.nadi.domain.model.Payment;
import com.payment.nadi.domain.model.PaymentProviderType;
import com.payment.nadi.domain.model.PaymentStatus;
import com.payment.nadi.domain.model.PaymentType;
import com.payment.nadi.domain.model.Transaction;
import com.payment.nadi.domain.repository.PaymentRepository;
import com.payment.nadi.domain.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class TransferService implements TransferUseCase {

    private static final Logger log = LoggerFactory.getLogger(TransferService.class);

    private final PaymentRepository paymentRepository;
    private final TransactionRepository transactionRepository;
    private final WalletService walletService;
    private final EventPublisher eventPublisher;
    private final IdempotencyStore idempotencyStore;
    private final ObjectMapper objectMapper;

    public TransferService(
            PaymentRepository paymentRepository,
            TransactionRepository transactionRepository,
            WalletService walletService,
            EventPublisher eventPublisher,
            IdempotencyStore idempotencyStore,
            ObjectMapper objectMapper
    ) {
        this.paymentRepository = paymentRepository;
        this.transactionRepository = transactionRepository;
        this.walletService = walletService;
        this.eventPublisher = eventPublisher;
        this.idempotencyStore = idempotencyStore;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public PaymentResponse initiateTransfer(TransferRequest request, String idempotencyKey) {
        log.info("Processing transfer from {} to {}, amount: {} {}",
                request.sourceAccountId(), request.destinationAccountId(), request.amount(), request.currency());

        // 1. Validate distinct accounts
        if (request.sourceAccountId().equals(request.destinationAccountId())) {
            throw new InvalidPaymentException("Source and destination accounts must be different");
        }

        // 2. Idempotency Check
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<IdempotencyStore.IdempotencyEntry> existing = idempotencyStore.find(idempotencyKey);
            if (existing.isPresent()) {
                log.info("Idempotent transfer request detected for key: {}", idempotencyKey);
                try {
                    return objectMapper.readValue(existing.get().responseJson(), PaymentResponse.class);
                } catch (JsonProcessingException e) {
                    log.warn("Failed to deserialize cached idempotency response: {}", e.getMessage());
                }
            }
        }

        Money amount = Money.of(request.amount(), request.currency());

        // 3. Validate accounts and balance
        walletService.validateAccount(request.sourceAccountId());
        walletService.validateAccount(request.destinationAccountId());

        if (!walletService.hasSufficientBalance(request.sourceAccountId(), amount)) {
            throw new InsufficientBalanceException("Source account has insufficient balance for transfer of " + amount);
        }

        // 4. Create Payment
        String reference = PaymentReferenceGenerator.generatePaymentReference(PaymentType.TRANSFER);
        Payment payment = Payment.create(
                reference,
                PaymentType.TRANSFER,
                amount,
                request.customerId(),
                request.sourceAccountId(),
                request.destinationAccountId(),
                PaymentProviderType.INTERNAL_LEDGER,
                request.sourceSystem(),
                request.description() != null ? request.description() : "Account Transfer",
                request.metadata()
        );
        Payment savedPayment = paymentRepository.save(payment);

        // 5. Atomic Debit & Credit via Wallet Service
        String txRef = PaymentReferenceGenerator.generateTransactionReference();
        walletService.debit(request.sourceAccountId(), amount, txRef, "Transfer debit: " + savedPayment.getReference());
        walletService.credit(request.destinationAccountId(), amount, txRef, "Transfer credit: " + savedPayment.getReference());

        // 6. Record Transaction
        Transaction tx = Transaction.create(
                savedPayment.getId(),
                txRef,
                PaymentType.TRANSFER,
                amount,
                Money.zero(amount.currency()),
                request.sourceAccountId(),
                "Transfer completed: " + savedPayment.getReference(),
                PaymentStatus.SUCCESS
        );
        transactionRepository.save(tx);

        // 7. Update Payment to SUCCESS
        savedPayment.markAsSuccess(txRef);
        paymentRepository.save(savedPayment);

        // 8. Publish Event
        eventPublisher.publish(TransferCompletedEvent.now(
                savedPayment.getId(),
                savedPayment.getReference(),
                amount,
                request.sourceAccountId(),
                request.destinationAccountId(),
                savedPayment.getDescription()
        ));

        PaymentResponse response = PaymentResponse.from(savedPayment);

        // 9. Cache Idempotency
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

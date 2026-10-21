package com.payment.nadi.application.service;

import com.payment.nadi.application.dto.response.PageResponse;
import com.payment.nadi.application.dto.response.PaymentResponse;
import com.payment.nadi.application.dto.response.TransactionResponse;
import com.payment.nadi.application.port.in.PaymentQueryUseCase;
import com.payment.nadi.application.port.out.EventPublisher;
import com.payment.nadi.application.port.out.PaymentProvider;
import com.payment.nadi.application.port.out.PaymentProviderResolver;
import com.payment.nadi.application.port.out.WalletService;
import com.payment.nadi.application.port.out.dto.PaymentStatusResult;
import com.payment.nadi.domain.event.PaymentCompletedEvent;
import com.payment.nadi.domain.event.PaymentFailedEvent;
import com.payment.nadi.domain.exception.PaymentNotFoundException;
import com.payment.nadi.domain.model.Money;
import com.payment.nadi.domain.model.Payment;
import com.payment.nadi.domain.model.PaymentStatus;
import com.payment.nadi.domain.model.Transaction;
import com.payment.nadi.domain.repository.PaymentRepository;
import com.payment.nadi.domain.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class PaymentQueryService implements PaymentQueryUseCase {

    private static final Logger log = LoggerFactory.getLogger(PaymentQueryService.class);

    private final PaymentRepository paymentRepository;
    private final TransactionRepository transactionRepository;
    private final PaymentProviderResolver providerResolver;
    private final EventPublisher eventPublisher;
    private final WalletService walletService;

    public PaymentQueryService(
            PaymentRepository paymentRepository,
            TransactionRepository transactionRepository,
            PaymentProviderResolver providerResolver,
            EventPublisher eventPublisher,
            WalletService walletService
    ) {
        this.paymentRepository = paymentRepository;
        this.transactionRepository = transactionRepository;
        this.providerResolver = providerResolver;
        this.eventPublisher = eventPublisher;
        this.walletService = walletService;
    }

    @Override
    public Money getAccountBalance(UUID accountId, String currency) {
        return walletService.getBalance(accountId, currency);
    }

    @Override
    @org.springframework.cache.annotation.Cacheable(value = "payments", key = "#paymentId", unless = "#result == null || !#result.status().isTerminal()")
    public PaymentResponse getPaymentById(UUID paymentId) {
        return paymentRepository.findById(paymentId)
                .map(PaymentResponse::from)
                .orElseThrow(() -> new PaymentNotFoundException(paymentId));
    }

    @Override
    public PaymentResponse getPaymentByReference(String reference) {
        return paymentRepository.findByReference(reference)
                .map(PaymentResponse::from)
                .orElseThrow(() -> new PaymentNotFoundException(reference));
    }

    @Override
    public PageResponse<PaymentResponse> getCustomerPayments(UUID customerId, int page, int size) {
        int offset = Math.max(0, page) * size;
        List<Payment> payments = paymentRepository.findByCustomerId(customerId, size, offset);
        List<PaymentResponse> responseList = payments.stream().map(PaymentResponse::from).toList();
        return PageResponse.of(responseList, page, size, payments.size());
    }

    @Override
    public List<TransactionResponse> getPaymentTransactions(UUID paymentId) {
        List<Transaction> transactions = transactionRepository.findByPaymentId(paymentId);
        return transactions.stream().map(TransactionResponse::from).toList();
    }

    @Override
    @Transactional
    @org.springframework.cache.annotation.CacheEvict(value = "payments", key = "#paymentId")
    public PaymentResponse refreshPaymentStatus(UUID paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(paymentId));

        if (payment.getStatus().isTerminal()) {
            return PaymentResponse.from(payment);
        }

        if (payment.getProvider() == null || payment.getProviderReference() == null) {
            return PaymentResponse.from(payment);
        }

        log.info("Refreshing payment status from provider {} for ref {}", payment.getProvider(), payment.getReference());
        PaymentProvider provider = providerResolver.resolve(payment.getProvider());
        PaymentStatusResult statusResult = provider.checkStatus(payment.getProviderReference());

        if (statusResult.status() == PaymentStatus.SUCCESS && payment.getStatus() != PaymentStatus.SUCCESS) {
            payment.markAsSuccess(statusResult.providerReference());
            paymentRepository.save(payment);
            eventPublisher.publish(PaymentCompletedEvent.now(
                    payment.getId(),
                    payment.getReference(),
                    payment.getType(),
                    payment.getAmount(),
                    payment.getCustomerId(),
                    payment.getProviderReference(),
                    payment.getSourceSystem()
            ));
        } else if (statusResult.status() == PaymentStatus.FAILED && payment.getStatus() != PaymentStatus.FAILED) {
            payment.markAsFailed(statusResult.errorMessage());
            paymentRepository.save(payment);
            eventPublisher.publish(PaymentFailedEvent.now(
                    payment.getId(),
                    payment.getReference(),
                    payment.getType(),
                    payment.getAmount(),
                    payment.getCustomerId(),
                    statusResult.errorMessage()
            ));
        }

        return PaymentResponse.from(payment);
    }
}

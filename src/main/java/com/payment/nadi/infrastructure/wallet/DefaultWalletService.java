package com.payment.nadi.infrastructure.wallet;

import com.payment.nadi.application.port.out.WalletService;
import com.payment.nadi.domain.exception.InsufficientBalanceException;
import com.payment.nadi.domain.model.LedgerEntry;
import com.payment.nadi.domain.model.Money;
import com.payment.nadi.domain.repository.LedgerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * Double-entry ledger based wallet service implementation.
 * Ensures strict accounting rules and auditable balance updates.
 */
@Service
public class DefaultWalletService implements WalletService {

    private static final Logger log = LoggerFactory.getLogger(DefaultWalletService.class);

    private final LedgerRepository ledgerRepository;

    public DefaultWalletService(LedgerRepository ledgerRepository) {
        this.ledgerRepository = ledgerRepository;
    }

    @Override
    public void validateAccount(UUID accountId) {
        Objects.requireNonNull(accountId, "Account ID cannot be null");
        // In this implementation, account is considered valid if UUID is present.
        // For external Wallet microservice integration, an HTTP or gRPC call can be made here.
    }

    @Override
    public boolean hasSufficientBalance(UUID accountId, Money amount) {
        Objects.requireNonNull(accountId, "Account ID cannot be null");
        Objects.requireNonNull(amount, "Amount cannot be null");

        BigDecimal balance = ledgerRepository.calculateBalance(accountId);
        log.debug("Current balance for account {}: {}, requested: {}", accountId, balance, amount.amount());
        return balance.compareTo(amount.amount()) >= 0;
    }

    @Override
    @Transactional
    public void debit(UUID accountId, Money amount, String transactionRef, String description) {
        log.info("Debiting {} {} from account {} (ref: {})", amount.amount(), amount.getCurrencyCode(), accountId, transactionRef);
        LedgerEntry entry = LedgerEntry.create(
                UUID.randomUUID(),
                accountId,
                LedgerEntry.EntryType.DEBIT,
                amount,
                description != null ? description : "Debit transaction: " + transactionRef
        );
        ledgerRepository.save(entry);
    }

    @Override
    @Transactional
    public void credit(UUID accountId, Money amount, String transactionRef, String description) {
        log.info("Crediting {} {} to account {} (ref: {})", amount.amount(), amount.getCurrencyCode(), accountId, transactionRef);
        LedgerEntry entry = LedgerEntry.create(
                UUID.randomUUID(),
                accountId,
                LedgerEntry.EntryType.CREDIT,
                amount,
                description != null ? description : "Credit transaction: " + transactionRef
        );
        ledgerRepository.save(entry);
    }

    @Override
    public Money getBalance(UUID accountId, String currencyCode) {
        BigDecimal balance = ledgerRepository.calculateBalance(accountId);
        return Money.of(balance != null ? balance : BigDecimal.ZERO, currencyCode);
    }
}

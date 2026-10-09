package com.payment.nadi.infrastructure.wallet;

import com.payment.nadi.application.port.out.WalletService;
import com.payment.nadi.domain.exception.InsufficientBalanceException;
import com.payment.nadi.domain.model.LedgerEntry;
import com.payment.nadi.domain.model.Money;
import com.payment.nadi.domain.repository.LedgerRepository;
import com.payment.nadi.infrastructure.persistence.repository.SpringDataUserAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

@Service
public class DefaultWalletService implements WalletService {

    private static final Logger log = LoggerFactory.getLogger(DefaultWalletService.class);

    private final LedgerRepository ledgerRepository;
    private final SpringDataUserAccountRepository userAccountRepository;

    public DefaultWalletService(LedgerRepository ledgerRepository,
            SpringDataUserAccountRepository userAccountRepository) {
        this.ledgerRepository = ledgerRepository;
        this.userAccountRepository = userAccountRepository;
    }

    @Override
    public void validateAccount(UUID accountId) {
        Objects.requireNonNull(accountId, "Account ID cannot be null");
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasSufficientBalance(UUID accountId, Money amount) {
        Objects.requireNonNull(accountId, "Account ID cannot be null");
        Objects.requireNonNull(amount, "Amount cannot be null");

        BigDecimal balance = ledgerRepository.calculateBalance(accountId);
        log.debug("Current balance for account {}: {}, requested: {}", accountId, balance, amount.amount());
        return balance != null && balance.compareTo(amount.amount()) >= 0;
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public void lockAccountsInOrder(UUID accountA, UUID accountB) {
        if (accountA == null || accountB == null || accountA.equals(accountB)) {
            return;
        }
        // Strict ACID Ordering: Always acquire locks in deterministic lexicographical order to prevent deadlocks
        UUID firstLockId = accountA.compareTo(accountB) < 0 ? accountA : accountB;
        UUID secondLockId = accountA.compareTo(accountB) < 0 ? accountB : accountA;

        log.debug("Acquiring ordered pessimistic write locks on accounts: {} then {}", firstLockId, secondLockId);
        userAccountRepository.findBySourceAccountIdForUpdate(firstLockId)
                .or(() -> userAccountRepository.findByIdForUpdate(firstLockId));
        userAccountRepository.findBySourceAccountIdForUpdate(secondLockId)
                .or(() -> userAccountRepository.findByIdForUpdate(secondLockId));
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public void debit(UUID accountId, Money amount, String transactionRef, String description) {
        Objects.requireNonNull(accountId, "Account ID cannot be null");
        Objects.requireNonNull(amount, "Amount cannot be null");

        // ACID Isolation: Acquire pessimistic write lock on the account record
        userAccountRepository.findBySourceAccountIdForUpdate(accountId)
                .or(() -> userAccountRepository.findByIdForUpdate(accountId));

        // ACID Consistency: Verify balance under the locked row
        BigDecimal currentBalance = ledgerRepository.calculateBalance(accountId);
        if (currentBalance == null || currentBalance.compareTo(amount.amount()) < 0) {
            log.error("Insufficient balance during atomic debit on account {}. Current: {}, Required: {}",
                    accountId, currentBalance, amount.amount());
            throw new InsufficientBalanceException(
                    "Insufficient funds for account " + accountId + ": available " + currentBalance + ", requested "
                            + amount.amount());
        }

        log.info("Debiting {} {} from account {} (ref: {})", amount.amount(), amount.getCurrencyCode(), accountId,
                transactionRef);
        LedgerEntry entry = LedgerEntry.create(
                UUID.randomUUID(),
                accountId,
                LedgerEntry.EntryType.DEBIT,
                amount,
                description != null ? description : "Debit transaction: " + transactionRef);
        ledgerRepository.save(entry);
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public void credit(UUID accountId, Money amount, String transactionRef, String description) {
        Objects.requireNonNull(accountId, "Account ID cannot be null");
        Objects.requireNonNull(amount, "Amount cannot be null");

        // ACID Isolation: Lock row to ensure serial updates
        userAccountRepository.findBySourceAccountIdForUpdate(accountId)
                .or(() -> userAccountRepository.findByIdForUpdate(accountId));

        log.info("Crediting {} {} to account {} (ref: {})", amount.amount(), amount.getCurrencyCode(), accountId,
                transactionRef);
        LedgerEntry entry = LedgerEntry.create(
                UUID.randomUUID(),
                accountId,
                LedgerEntry.EntryType.CREDIT,
                amount,
                description != null ? description : "Credit transaction: " + transactionRef);
        ledgerRepository.save(entry);
    }

    @Override
    @Transactional(readOnly = true)
    public Money getBalance(UUID accountId, String currencyCode) {
        BigDecimal balance = ledgerRepository.calculateBalance(accountId);
        return Money.of(balance != null ? balance : BigDecimal.ZERO, currencyCode);
    }
}

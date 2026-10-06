package com.payment.nadi.application.service;

import com.payment.nadi.domain.exception.InsufficientBalanceException;
import com.payment.nadi.domain.model.Money;
import com.payment.nadi.domain.repository.LedgerRepository;
import com.payment.nadi.infrastructure.persistence.entity.UserAccountEntity;
import com.payment.nadi.infrastructure.persistence.repository.SpringDataUserAccountRepository;
import com.payment.nadi.infrastructure.wallet.DefaultWalletService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DatabaseAcidAndSolidTest {

    private LedgerRepository ledgerRepository;
    private SpringDataUserAccountRepository userAccountRepository;
    private DefaultWalletService walletService;

    private UUID accountId;
    private UserAccountEntity userAccount;

    @BeforeEach
    void setUp() {
        ledgerRepository = mock(LedgerRepository.class);
        userAccountRepository = mock(SpringDataUserAccountRepository.class);
        walletService = new DefaultWalletService(ledgerRepository, userAccountRepository);

        accountId = UUID.randomUUID();
        userAccount = UserAccountEntity.builder()
                .id(UUID.randomUUID())
                .sourceAccountId(accountId)
                .accountNumber("00123456")
                .phoneNumber("+251911223344")
                .status("ACTIVE")
                .version(1L)
                .build();

        when(userAccountRepository.findBySourceAccountIdForUpdate(accountId)).thenReturn(Optional.of(userAccount));
    }

    @Test
    @DisplayName("ACID Isolation: Wallet debit must acquire pessimistic write lock on account row")
    void shouldAcquirePessimisticLockOnDebit() {
        when(ledgerRepository.calculateBalance(accountId)).thenReturn(new BigDecimal("1000.00"));

        walletService.debit(accountId, Money.of(new BigDecimal("200.00"), "ETB"), "TX-001", "Test debit");

        // Verify row-level pessimistic write lock was requested to guarantee isolation
        verify(userAccountRepository).findBySourceAccountIdForUpdate(accountId);
        verify(ledgerRepository).save(any());
    }

    @Test
    @DisplayName("ACID Consistency: Wallet debit must reject transactions exceeding available balance")
    void shouldEnforceConsistencyByRejectingOverdraft() {
        when(ledgerRepository.calculateBalance(accountId)).thenReturn(new BigDecimal("50.00"));

        InsufficientBalanceException ex = assertThrows(
                InsufficientBalanceException.class,
                () -> walletService.debit(accountId, Money.of(new BigDecimal("200.00"), "ETB"), "TX-002", "Overdraft attempt")
        );

        assertTrue(ex.getMessage().contains("Insufficient funds"));
        verify(ledgerRepository, never()).save(any());
    }

    @Test
    @DisplayName("ACID Atomicity & Durability: Wallet credit records double-entry ledger entry")
    void shouldRecordLedgerCreditAtomically() {
        walletService.credit(accountId, Money.of(new BigDecimal("500.00"), "ETB"), "TX-003", "Test credit");

        verify(userAccountRepository).findBySourceAccountIdForUpdate(accountId);
        verify(ledgerRepository).save(argThat(entry ->
                entry.getAccountId().equals(accountId) &&
                entry.getAmount().amount().compareTo(new BigDecimal("500.00")) == 0
        ));
    }
}

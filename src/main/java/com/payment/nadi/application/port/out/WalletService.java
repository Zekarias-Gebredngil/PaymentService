package com.payment.nadi.application.port.out;

import com.payment.nadi.domain.model.Money;

import java.util.UUID;

/**
 * Port decoupling Payment Service from internal balance and wallet operations.
 * Allows seamless integration with an external Wallet microservice or local ledger.
 */
public interface WalletService {

    void validateAccount(UUID accountId);

    boolean hasSufficientBalance(UUID accountId, Money amount);

    void debit(UUID accountId, Money amount, String transactionRef, String description);

    void credit(UUID accountId, Money amount, String transactionRef, String description);

    Money getBalance(UUID accountId, String currencyCode);
}

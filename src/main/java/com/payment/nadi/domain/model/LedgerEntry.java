package com.payment.nadi.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Auditable financial ledger entry following double-entry bookkeeping principles.
 */
public class LedgerEntry {

    public enum EntryType {
        DEBIT,
        CREDIT
    }

    private UUID id;
    private UUID transactionId;
    private UUID accountId;
    private EntryType entryType;
    private Money amount;
    private String description;
    private Instant createdAt;

    public LedgerEntry() {
    }

    public static LedgerEntry create(
            UUID transactionId,
            UUID accountId,
            EntryType entryType,
            Money amount,
            String description
    ) {
        LedgerEntry entry = new LedgerEntry();
        entry.id = UUID.randomUUID();
        entry.transactionId = transactionId;
        entry.accountId = accountId;
        entry.entryType = entryType;
        entry.amount = amount;
        entry.description = description;
        entry.createdAt = Instant.now();
        return entry;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getTransactionId() { return transactionId; }
    public void setTransactionId(UUID transactionId) { this.transactionId = transactionId; }

    public UUID getAccountId() { return accountId; }
    public void setAccountId(UUID accountId) { this.accountId = accountId; }

    public EntryType getEntryType() { return entryType; }
    public void setEntryType(EntryType entryType) { this.entryType = entryType; }

    public Money getAmount() { return amount; }
    public void setAmount(Money amount) { this.amount = amount; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}

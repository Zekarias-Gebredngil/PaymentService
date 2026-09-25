package com.payment.nadi.domain.model;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;

/**
 * Value Object representing monetary value with currency.
 * Immutable and guarantees positive or zero amounts with proper scale.
 */
public record Money(
        BigDecimal amount,
        Currency currency
) implements Serializable, Comparable<Money> {

    public Money {
        Objects.requireNonNull(amount, "Amount must not be null");
        Objects.requireNonNull(currency, "Currency must not be null");
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Amount must be non-negative: " + amount);
        }
        // Normalize to standard currency fraction digits (typically 2)
        amount = amount.setScale(Math.max(currency.getDefaultFractionDigits(), 2), RoundingMode.HALF_EVEN);
    }

    public static Money of(BigDecimal amount, String currencyCode) {
        return new Money(amount, Currency.getInstance(currencyCode));
    }

    public static Money of(BigDecimal amount, Currency currency) {
        return new Money(amount, currency);
    }

    public static Money zero(String currencyCode) {
        return of(BigDecimal.ZERO, currencyCode);
    }

    public static Money zero(Currency currency) {
        return of(BigDecimal.ZERO, currency);
    }

    public Money add(Money other) {
        validateSameCurrency(other);
        return new Money(this.amount.add(other.amount), this.currency);
    }

    public Money subtract(Money other) {
        validateSameCurrency(other);
        if (this.amount.compareTo(other.amount) < 0) {
            throw new IllegalArgumentException("Cannot subtract: resulting amount would be negative");
        }
        return new Money(this.amount.subtract(other.amount), this.currency);
    }

    public boolean isGreaterThan(Money other) {
        validateSameCurrency(other);
        return this.amount.compareTo(other.amount) > 0;
    }

    public boolean isGreaterThanOrEqual(Money other) {
        validateSameCurrency(other);
        return this.amount.compareTo(other.amount) >= 0;
    }

    public boolean isLessThan(Money other) {
        validateSameCurrency(other);
        return this.amount.compareTo(other.amount) < 0;
    }

    public boolean isZero() {
        return this.amount.compareTo(BigDecimal.ZERO) == 0;
    }

    public String getCurrencyCode() {
        return currency.getCurrencyCode();
    }

    private void validateSameCurrency(Money other) {
        Objects.requireNonNull(other, "Other money must not be null");
        if (!this.currency.equals(other.currency)) {
            throw new IllegalArgumentException(
                    String.format("Currency mismatch: %s vs %s", this.currency.getCurrencyCode(), other.currency.getCurrencyCode())
            );
        }
    }

    @Override
    public int compareTo(Money other) {
        validateSameCurrency(other);
        return this.amount.compareTo(other.amount);
    }

    @Override
    public String toString() {
        return amount + " " + currency.getCurrencyCode();
    }
}

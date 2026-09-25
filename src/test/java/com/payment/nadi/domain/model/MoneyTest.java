package com.payment.nadi.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Currency;

import static org.junit.jupiter.api.Assertions.*;

class MoneyTest {

    @Test
    @DisplayName("Should create valid Money with currency")
    void shouldCreateValidMoney() {
        Money money = Money.of(new BigDecimal("100.50"), "ETB");

        assertNotNull(money);
        assertEquals(new BigDecimal("100.50"), money.amount());
        assertEquals("ETB", money.getCurrencyCode());
        assertFalse(money.isZero());
        assertTrue(money.isGreaterThan(Money.zero("ETB")));
    }

    @Test
    @DisplayName("Should reject negative amounts")
    void shouldRejectNegativeAmount() {
        assertThrows(IllegalArgumentException.class, () ->
                Money.of(new BigDecimal("-10.00"), "ETB")
        );
    }

    @Test
    @DisplayName("Should correctly round to 2 decimal places using Banker's rounding")
    void shouldRoundToTwoDecimals() {
        Money money = Money.of(new BigDecimal("100.555"), "ETB");
        assertEquals(new BigDecimal("100.56"), money.amount());
    }

    @Test
    @DisplayName("Should add money of same currency")
    void shouldAddMoneySameCurrency() {
        Money m1 = Money.of(new BigDecimal("50.25"), "ETB");
        Money m2 = Money.of(new BigDecimal("49.75"), "ETB");
        Money sum = m1.add(m2);

        assertEquals(new BigDecimal("100.00"), sum.amount());
        assertEquals("ETB", sum.getCurrencyCode());
    }

    @Test
    @DisplayName("Should subtract money of same currency")
    void shouldSubtractMoneySameCurrency() {
        Money m1 = Money.of(new BigDecimal("100.00"), "ETB");
        Money m2 = Money.of(new BigDecimal("40.00"), "ETB");
        Money result = m1.subtract(m2);

        assertEquals(new BigDecimal("60.00"), result.amount());
    }

    @Test
    @DisplayName("Should reject addition of different currencies")
    void shouldRejectDifferentCurrencyAddition() {
        Money etb = Money.of(new BigDecimal("100.00"), "ETB");
        Money usd = Money.of(new BigDecimal("100.00"), "USD");

        assertThrows(IllegalArgumentException.class, () -> etb.add(usd));
    }
}

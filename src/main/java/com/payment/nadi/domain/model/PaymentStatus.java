package com.payment.nadi.domain.model;

import java.util.Set;

/**
 * Standard payment lifecycle states.
 */
public enum PaymentStatus {
    CREATED,
    PENDING,
    PROCESSING,
    SUCCESS,
    FAILED,
    CANCELLED,
    EXPIRED;

    private static final Set<PaymentStatus> TERMINAL_STATES = Set.of(
            SUCCESS,
            FAILED,
            CANCELLED,
            EXPIRED);

    public boolean isTerminal() {
        return TERMINAL_STATES.contains(this);
    }

    public boolean canTransitionTo(PaymentStatus next) {
        if (this == next) {
            return true;
        }
        if (this.isTerminal()) {
            return false;
        }
        return switch (this) {
            case CREATED ->
                next == PENDING || next == PROCESSING || next == SUCCESS || next == FAILED || next == CANCELLED;
            case PENDING ->
                next == PROCESSING || next == SUCCESS || next == FAILED || next == CANCELLED || next == EXPIRED;
            case PROCESSING -> next == SUCCESS || next == FAILED || next == CANCELLED;
            default -> false;
        };
    }
}

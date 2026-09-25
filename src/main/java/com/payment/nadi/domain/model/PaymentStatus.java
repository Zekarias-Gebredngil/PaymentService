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
            EXPIRED
    );

    /**
     * Checks if this status is terminal (no further changes allowed).
     */
    public boolean isTerminal() {
        return TERMINAL_STATES.contains(this);
    }

    /**
     * Validates whether a state transition from this status to target status is valid.
     */
    public boolean canTransitionTo(PaymentStatus next) {
        if (this == next) {
            return true;
        }
        if (this.isTerminal()) {
            return false;
        }
        return switch (this) {
            case CREATED -> next == PENDING || next == PROCESSING || next == SUCCESS || next == FAILED || next == CANCELLED;
            case PENDING -> next == PROCESSING || next == SUCCESS || next == FAILED || next == CANCELLED || next == EXPIRED;
            case PROCESSING -> next == SUCCESS || next == FAILED || next == CANCELLED;
            default -> false;
        };
    }
}

package com.keni.starter.modules.payments;

import java.math.BigDecimal;

public enum PaymentStatus {
  PENDING,
  COMPLETED,
  FAILED,
  REFUNDED;

  /**
   * A payment can only move forward through a fixed set of steps.
   *
   * PENDING -> COMPLETED | FAILED
   * COMPLETED -> REFUNDED
   * FAILED and REFUNDED are terminal
   *
   * Re-stating the current status is allowed so a retry of the same webhook is harmless.
   */
  public boolean canTransitionTo(PaymentStatus next) {
    if (next == this) {
      return true;
    }
    return switch (this) {
      case PENDING -> next == COMPLETED || next == FAILED;
      case COMPLETED -> next == REFUNDED;
      case FAILED, REFUNDED -> false;
    };
  }

  /**
   * Whether a refund of the given size may be applied from this status.
   *
   * <p>A refund is rejected outright if the amount exceeds what was paid. Clamping it
   * silently would let a bug invent money, and a refund on a payment that never succeeded
   * would be money out of the door for nothing.
   */
  public boolean allowsRefund(BigDecimal alreadyRefunded, BigDecimal requested,
      BigDecimal paid) {
    if (this != COMPLETED) {
      return false;
    }
    if (requested == null || requested.signum() <= 0) {
      return false;
    }
    var already = alreadyRefunded == null ? BigDecimal.ZERO : alreadyRefunded;
    return already.add(requested).compareTo(paid) <= 0;
  }
}
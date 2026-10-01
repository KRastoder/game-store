package com.keni.starter.modules.payments;

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
}
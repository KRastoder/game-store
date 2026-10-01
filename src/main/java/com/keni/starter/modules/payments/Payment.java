package com.keni.starter.modules.payments;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.keni.starter.modules.subscriptions.Subscription;
import com.keni.starter.modules.user.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "payments")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Payment {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id")
  private User user;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "subscription_id")
  private Subscription subscription;

  @Column(name = "date_paid", nullable = false)
  private Instant datePaid;

  @Column(name = "amount", nullable = false)
  private BigDecimal amount;

  /**
   * How much of this payment has been handed back. Null until a refund happens.
   *
   * <p>Needed because a mid period cancellation refunds the unused fraction rather than
   * the whole amount, and a single status flag cannot express "partly refunded".
   */
  @Column(name = "refunded_amount")
  private BigDecimal refundedAmount;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private PaymentStatus status;

  /** Nothing given back yet. */
  public boolean hasNoRefund() {
    return refundedAmount == null;
  }

  /** The entire amount was given back. */
  public boolean isFullyRefunded() {
    return refundedAmount != null && refundedAmount.compareTo(amount) >= 0;
  }

  /** What is still held, after any refund. */
  public BigDecimal netAmount() {
    return refundedAmount == null ? amount : amount.subtract(refundedAmount);
  }
}
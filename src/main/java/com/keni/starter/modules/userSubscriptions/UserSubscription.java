
package com.keni.starter.modules.userSubscriptions;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import com.keni.starter.modules.subscriptions.Subscription;
import com.keni.starter.modules.user.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "user_subscriptions", uniqueConstraints = @UniqueConstraint(columnNames = { "user_id",
    "subscription_id" }))
@Getter
@Setter
@NoArgsConstructor
public class UserSubscription {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id")
  private User user;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "subscription_id")
  private Subscription subscription;

  @Column(name = "started_at", nullable = false)
  private Instant startedAt;
  private Instant expiresAt;

  /**
   * Set when access is ended early, by a refund. Null while the subscription is live,
   * which is why it has to be nullable rather than defaulting to some epoch date.
   */
  @Column(name = "cancelled_at")
  private Instant cancelledAt;

  /** Set once an expiry reminder has gone out, so the scheduled job never sends twice. */
  @Column(name = "reminder_sent_at")
  private Instant reminderSentAt;

  /** Live means not cancelled and not yet past its expiry. */
  public boolean isActive(Instant now) {
    return cancelledAt == null && expiresAt != null && expiresAt.isAfter(now);
  }

  public void cancel(Instant now) {
    if (cancelledAt == null) {
      cancelledAt = now;
    }
  }

  public void markReminderSent(Instant now) {
    this.reminderSentAt = now;
  }

  /**
   * Money still owed back for the part of the period the customer never got to use.
   *
   * <p>The current period is measured backwards from expiresAt, not forwards from
   * startedAt. After two renewals of a 30 day tier those are 90 days apart while the
   * latest payment only ever covered 30, so proration has to follow the payment period or
   * the refund would be three times too large.
   *
   * <p>Never negative and never more than was paid. Zero when the period is over, because
   * there is nothing left to give back.
   *
   * @param price what was paid for the current period
   * @param now the moment of cancellation
   * @return the unspent proportion, rounded to cents
   */
  public BigDecimal unspentAmount(BigDecimal price, int durationDays, Instant now) {
    if (expiresAt == null || durationDays <= 0 || price == null) {
      return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }
    if (!expiresAt.isAfter(now)) {
      // the period has already run out, so nothing is unspent
      return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }

    var periodStart = expiresAt.minus(durationDays, ChronoUnit.DAYS);
    var periodMillis = Duration.between(periodStart, expiresAt).toMillis();
    var remainingMillis = Duration.between(now, expiresAt).toMillis();
    if (periodMillis <= 0 || remainingMillis <= 0) {
      return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }

    var unspentFraction = BigDecimal.valueOf(remainingMillis)
        .divide(BigDecimal.valueOf(periodMillis), 10, RoundingMode.HALF_UP);
    var refund = price.multiply(unspentFraction).setScale(2, RoundingMode.HALF_UP);

    // clamp, so a clock skew or a bad price can never produce a negative or inflated refund
    return refund.max(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
        .min(price.setScale(2, RoundingMode.HALF_UP));
  }
}

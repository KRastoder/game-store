
package com.keni.starter.modules.userSubscriptions;

import java.time.Instant;
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

  /** Live means not cancelled and not yet past its expiry. */
  public boolean isActive() {
    return cancelledAt == null && expiresAt != null && expiresAt.isAfter(Instant.now());
  }

  public void cancel() {
    if (cancelledAt == null) {
      cancelledAt = Instant.now();
    }
  }
}

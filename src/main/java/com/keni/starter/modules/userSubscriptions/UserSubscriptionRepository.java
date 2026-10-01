package com.keni.starter.modules.userSubscriptions;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface UserSubscriptionRepository
    extends JpaRepository<UserSubscription, UUID> {

  Page<UserSubscription> findByUserId(UUID userId, Pageable pageable);

  Page<UserSubscription> findBySubscriptionId(UUID subscriptionId, Pageable pageable);

  Optional<UserSubscription> findByUserIdAndSubscriptionId(UUID userId, UUID subscriptionId);

  boolean existsByUserIdAndSubscriptionId(UUID userId, UUID subscriptionId);

  /**
   * The caller's own row for this tier, locked for update.
   *
   * <p>Renewal reads expiresAt, adds a period to it, and writes it back. Without the lock,
   * two concurrent renewals both read the same expiresAt, both add one period, and the
   * second write silently discards the first. The customer would have paid twice and
   * received a single period, which is a customer who has to be refunded by hand.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select us from UserSubscription us"
      + " where us.user.id = :userId and us.subscription.id = :subscriptionId")
  Optional<UserSubscription> findForRenewal(@Param("userId") UUID userId,
      @Param("subscriptionId") UUID subscriptionId);

  /**
   * Locked read by primary key, so a cancel racing a renewal cannot interleave. Cancel
   * reads the row, writes cancelled_at, and a renewal reads the same row to work out
   * which end date to extend from.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select us from UserSubscription us where us.id = :id")
  Optional<UserSubscription> findForUpdate(@Param("id") UUID id);

  /**
   * Live subscriptions that run out before {@code before}, and have not been reminded
   * about yet. This is the reminder job's query.
   *
   * <p>{@code expiresAtAfter now} rather than a plain expiry comparison, so a
   * subscription that has already lapsed is never nagged about after the fact.
   */
  @Query("select us from UserSubscription us"
      + " where us.cancelledAt is null"
      + " and us.reminderSentAt is null"
      + " and us.expiresAt is not null"
      + " and us.expiresAt > :now"
      + " and us.expiresAt <= :before")
  List<UserSubscription> findExpiringWithoutReminder(@Param("now") Instant now,
      @Param("before") Instant before);
}
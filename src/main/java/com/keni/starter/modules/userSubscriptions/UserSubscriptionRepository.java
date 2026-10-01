package com.keni.starter.modules.userSubscriptions;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface UserSubscriptionRepository
    extends JpaRepository<UserSubscription, UUID> {

  List<UserSubscription> findByUserId(UUID userId);

  List<UserSubscription> findBySubscriptionId(UUID subscriptionId);

  Optional<UserSubscription> findByUserIdAndSubscriptionId(UUID userId, UUID subscriptionId);

  boolean existsByUserIdAndSubscriptionId(UUID userId, UUID subscriptionId);

  /**
   * Locked read by primary key, so a cancel racing a renewal cannot interleave. Cancel
   * reads the row, writes cancelled_at, and a renewal reads the same row to work out
   * which end date to extend from.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select us from UserSubscription us where us.id = :id")
  Optional<UserSubscription> findForUpdate(@Param("id") UUID id);

  /**
   * Same lookup, but takes a row level write lock.
   *
   * Renewal reads expiresAt, adds a period to it, and writes it back. Without the lock,
   * two concurrent renewals both read the same expiresAt, both add one period, and the
   * second write silently discards the first. The customer would have paid twice and
   * received a single period, which is a customer who has to be refunded by hand.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select us from UserSubscription us"
      + " where us.user.id = :userId and us.subscription.id = :subscriptionId")
  Optional<UserSubscription> findForRenewal(@Param("userId") UUID userId,
      @Param("subscriptionId") UUID subscriptionId);
}
package com.keni.starter.modules.userSubscriptions;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserSubscriptionRepository
    extends JpaRepository<UserSubscription, UUID> {

  List<UserSubscription> findByUserId(UUID userId);

  List<UserSubscription> findBySubscriptionId(UUID subscriptionId);

  Optional<UserSubscription> findByUserIdAndSubscriptionId(UUID userId, UUID subscriptionId);

  boolean existsByUserIdAndSubscriptionId(UUID userId, UUID subscriptionId);
}

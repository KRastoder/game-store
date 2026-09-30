package com.keni.starter.modules.userSubscriptions.dtos;

import java.time.Instant;
import java.util.UUID;

import com.keni.starter.modules.userSubscriptions.UserSubscription;

/**
 * userSubscription response
 */
public record UserSubscriptionResponse(
    UUID id,
    UUID userId,
    UUID subscriptionId,
    Instant startedAt,
    Instant expiresAt) {

  public static UserSubscriptionResponse from(UserSubscription userSubscription) {
    return new UserSubscriptionResponse(userSubscription.getId(),
        userSubscription.getUser().getId(), userSubscription.getSubscription().getId(),
        userSubscription.getStartedAt(), userSubscription.getExpiresAt());
  }
}
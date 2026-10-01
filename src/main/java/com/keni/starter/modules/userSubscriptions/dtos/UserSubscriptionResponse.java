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
    Instant expiresAt,
    Instant cancelledAt,
    Instant reminderSentAt,
    boolean active) {

  public static UserSubscriptionResponse from(UserSubscription userSubscription, Instant now) {
    return new UserSubscriptionResponse(userSubscription.getId(),
        userSubscription.getUser().getId(), userSubscription.getSubscription().getId(),
        userSubscription.getStartedAt(), userSubscription.getExpiresAt(),
        userSubscription.getCancelledAt(), userSubscription.getReminderSentAt(),
        userSubscription.isActive(now));
  }
}
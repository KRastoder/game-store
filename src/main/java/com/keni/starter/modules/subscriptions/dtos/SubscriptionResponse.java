package com.keni.starter.modules.subscriptions.dtos;

import java.math.BigDecimal;
import java.util.UUID;

import com.keni.starter.modules.subscriptions.Subscription;

/**
 * subscription response
 */
public record SubscriptionResponse(
    UUID id,
    String name,
    BigDecimal price) {

  public static SubscriptionResponse from(Subscription subscription) {
    return new SubscriptionResponse(subscription.getId(), subscription.getName(),
        subscription.getPrice());
  }
}
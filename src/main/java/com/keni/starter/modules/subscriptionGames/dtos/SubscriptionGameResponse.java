package com.keni.starter.modules.subscriptionGames.dtos;

import java.util.UUID;

import com.keni.starter.modules.subscriptionGames.SubscriptionGame;

/**
 * subscriptionGame response
 */
public record SubscriptionGameResponse(
    UUID id,
    UUID subscriptionId,
    UUID gameId) {

  public static SubscriptionGameResponse from(SubscriptionGame subscriptionGame) {
    return new SubscriptionGameResponse(subscriptionGame.getId(),
        subscriptionGame.getSubscription().getId(), subscriptionGame.getGame().getId());
  }
}
package com.keni.starter.modules.subscriptionGames.dtos;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

/**
 * newSubscriptionGame
 */
public record NewSubscriptionGameRequest(
    @NotNull UUID subscriptionId,
    @NotNull UUID gameId) {
}
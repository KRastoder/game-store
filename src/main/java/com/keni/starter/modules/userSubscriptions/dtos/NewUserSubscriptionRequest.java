package com.keni.starter.modules.userSubscriptions.dtos;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

/**
 * newUserSubscription
 */
public record NewUserSubscriptionRequest(
    @NotNull UUID userId,
    @NotNull UUID subscriptionId) {
}
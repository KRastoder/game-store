package com.keni.starter.modules.userSubscriptions.dtos;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

/**
 * subscribe the signed in user to a tier
 */
public record NewUserSubscriptionRequest(
    @NotNull UUID subscriptionId) {
}
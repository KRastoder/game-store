package com.keni.starter.modules.subscriptions.dtos;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * newSubscription
 */
public record NewSubscriptionRequest(
    @NotBlank String name,
    @NotNull @Positive BigDecimal price) {
}
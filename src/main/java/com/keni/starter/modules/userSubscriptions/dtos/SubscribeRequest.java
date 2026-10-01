package com.keni.starter.modules.userSubscriptions.dtos;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * subscribe the signed in user to a tier and pay for it in the same call
 */
public record SubscribeRequest(
    @NotNull UUID subscriptionId,
    @NotNull UUID userId,
    /** Checked against the tier price on the server, never trusted blindly. */
    @NotNull @Positive BigDecimal amount,
    Instant datePaid) {
}
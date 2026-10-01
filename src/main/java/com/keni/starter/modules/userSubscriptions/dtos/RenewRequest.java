package com.keni.starter.modules.userSubscriptions.dtos;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * renew the signed in user's subscription to a tier they already hold
 */
public record RenewRequest(
    @NotNull UUID subscriptionId,
    /** Checked against the tier price on the server, never trusted blindly. */
    @NotNull @Positive BigDecimal amount) {
}
package com.keni.starter.modules.payments.dtos;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * createPayment
 */
public record CreateRequestPayment(
    @NotNull UUID userId,
    @NotNull UUID subId,
    Instant datePaid,
    @NotNull @Positive BigDecimal amount) {
}
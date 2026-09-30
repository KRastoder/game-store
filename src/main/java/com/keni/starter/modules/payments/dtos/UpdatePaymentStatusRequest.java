package com.keni.starter.modules.payments.dtos;

import com.keni.starter.modules.payments.PaymentStatus;

import jakarta.validation.constraints.NotNull;

/**
 * updatePaymentStatus
 */
public record UpdatePaymentStatusRequest(
    @NotNull PaymentStatus status) {
}
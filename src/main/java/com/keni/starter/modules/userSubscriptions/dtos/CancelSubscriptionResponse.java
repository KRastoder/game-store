package com.keni.starter.modules.userSubscriptions.dtos;

import java.math.BigDecimal;

import com.keni.starter.modules.payments.dtos.PaymentResponse;

/**
 * the outcome of a cancellation, and what it owed back
 */
public record CancelSubscriptionResponse(
    UserSubscriptionResponse userSubscription,
    /** null when nothing was refunded, which is the normal case for a full period used */
    PaymentResponse payment,
    BigDecimal refundedAmount) {
}
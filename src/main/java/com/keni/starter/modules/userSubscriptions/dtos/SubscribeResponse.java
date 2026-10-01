package com.keni.starter.modules.userSubscriptions.dtos;

import com.keni.starter.modules.payments.dtos.PaymentResponse;

/**
 * the user subscription and the payment that paid for it
 */
public record SubscribeResponse(
    UserSubscriptionResponse userSubscription,
    PaymentResponse payment) {
}
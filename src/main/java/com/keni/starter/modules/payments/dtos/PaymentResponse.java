package com.keni.starter.modules.payments.dtos;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.keni.starter.modules.payments.Payment;
import com.keni.starter.modules.payments.PaymentStatus;

/**
 * payment response
 */
public record PaymentResponse(
    UUID id,
    UUID userId,
    UUID subId,
    Instant datePaid,
    BigDecimal amount,
    BigDecimal refundedAmount,
    BigDecimal netAmount,
    PaymentStatus status) {

  public static PaymentResponse from(Payment payment) {
    return new PaymentResponse(payment.getId(), payment.getUser().getId(),
        payment.getSubscription().getId(), payment.getDatePaid(), payment.getAmount(),
        payment.getRefundedAmount(), payment.netAmount(), payment.getStatus());
  }
}
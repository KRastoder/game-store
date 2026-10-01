package com.keni.starter.modules.payments;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.keni.starter.modules.payments.dtos.PaymentResponse;
import com.keni.starter.modules.payments.dtos.UpdatePaymentStatusRequest;
import com.keni.starter.modules.userSubscriptions.UserSubscriptionRepository;

@Service
public class PaymentService {
  private final PaymentRepository paymentRepository;
  private final UserSubscriptionRepository userSubscriptionRepository;
  private final Clock clock;

  public PaymentService(PaymentRepository paymentRepository,
      UserSubscriptionRepository userSubscriptionRepository, Clock clock) {
    this.paymentRepository = paymentRepository;
    this.userSubscriptionRepository = userSubscriptionRepository;
    this.clock = clock;
  }

  /**
   * There is deliberately no create method here. A payment is only ever created by the
   * subscribe checkout, where it is written in the same transaction as the subscription
   * it pays for. A standalone create would let someone record a payment against a user
   * who has no subscription for it.
   */
  @Transactional
  public PaymentResponse updateStatus(UUID id, UpdatePaymentStatusRequest request) {
    var payment = paymentRepository.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found"));

    if (!payment.getStatus().canTransitionTo(request.status())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
          "Cannot move a payment from " + payment.getStatus() + " to " + request.status());
    }

    if (request.status() == PaymentStatus.REFUNDED) {
      // a bare REFUNDED means give back everything still held
      refund(payment, payment.netAmount());
    } else {
      payment.setStatus(request.status());
    }

    return PaymentResponse.from(payment);
  }

  /**
   * Gives back part of a payment, which is what cancelling mid period owes.
   *
   * <p>Partial, so the status becomes REFUNDED even though money is still held. The amount
   * actually returned is carried on the payment, because a status flag alone cannot say
   * "9.99 paid, 3.33 back, 6.66 still owed".
   *
   * @return the amount refunded, zero when there was nothing to give back
   */
  @Transactional
  public BigDecimal refundPartially(Payment payment, BigDecimal amount) {
    if (amount == null || amount.signum() <= 0) {
      // nothing unspent, so this is a plain cancellation and the payment is untouched
      return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }
    refund(payment, amount);
    return amount.setScale(2, RoundingMode.HALF_UP);
  }

  private void refund(Payment payment, BigDecimal amount) {
    if (!payment.getStatus()
        .allowsRefund(payment.getRefundedAmount(), amount, payment.getAmount())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
          "Cannot refund " + amount + " against a payment of " + payment.getAmount()
              + " in state " + payment.getStatus());
    }

    var already = payment.getRefundedAmount() == null ? BigDecimal.ZERO
        : payment.getRefundedAmount();
    payment.setRefundedAmount(already.add(amount));
    payment.setStatus(PaymentStatus.REFUNDED);

    // Handing money back has to take the access back with it.
    closeSubscriptionFor(payment);
  }

  /**
   * Ends the subscription the refunded payment was for.
   *
   * <p>Partial refunds are not supported: refunding any payment for a tier closes that
   * user's access to the tier entirely. That is the safe direction to be wrong in.
   */
  private void closeSubscriptionFor(Payment payment) {
    userSubscriptionRepository
        .findByUserIdAndSubscriptionId(payment.getUser().getId(),
            payment.getSubscription().getId())
        .ifPresent(userSubscription -> {
          userSubscription.cancel(clock.instant());
          userSubscriptionRepository.save(userSubscription);
        });
  }

  /** Admin only, guarded by SecurityConfig. Shows every payment in the system. */
  public List<PaymentResponse> getAllPayments() {
    return paymentRepository.findAll().stream().map(PaymentResponse::from).toList();
  }

  /** Scoped to the caller, the id never comes from the request. */
  public List<PaymentResponse> getPaymentsByUserId(UUID userId) {
    return paymentRepository.findByUserId(userId).stream().map(PaymentResponse::from).toList();
  }
}
package com.keni.starter.modules.payments;

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

  public PaymentService(PaymentRepository paymentRepository,
      UserSubscriptionRepository userSubscriptionRepository) {
    this.paymentRepository = paymentRepository;
    this.userSubscriptionRepository = userSubscriptionRepository;
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

    payment.setStatus(request.status());

    // Handing the money back has to take the access back with it. Without this the
    // customer is refunded and keeps playing until the original expiry.
    if (request.status() == PaymentStatus.REFUNDED) {
      closeSubscriptionFor(payment);
    }

    return PaymentResponse.from(payment);
  }

  /**
   * Ends the subscription the refunded payment was for.
   *
   * <p>Partial refunds are not supported: refunding any payment for a tier closes that
   * user's access to the tier entirely. That is the safe direction to be wrong in, and it
   * is why refunds are the one transition that is deliberately not reversible.
   */
  private void closeSubscriptionFor(Payment payment) {
    userSubscriptionRepository
        .findByUserIdAndSubscriptionId(payment.getUser().getId(),
            payment.getSubscription().getId())
        .ifPresent(userSubscription -> {
          userSubscription.cancel();
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
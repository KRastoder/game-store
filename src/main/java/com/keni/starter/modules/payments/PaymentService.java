package com.keni.starter.modules.payments;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.keni.starter.modules.payments.dtos.PaymentResponse;
import com.keni.starter.modules.payments.dtos.UpdatePaymentStatusRequest;

@Service
public class PaymentService {
  private final PaymentRepository paymentRepository;

  public PaymentService(PaymentRepository paymentRepository) {
    this.paymentRepository = paymentRepository;
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

    return PaymentResponse.from(payment);
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
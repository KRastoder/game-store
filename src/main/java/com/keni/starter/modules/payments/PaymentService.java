package com.keni.starter.modules.payments;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.keni.starter.modules.payments.dtos.CreateRequestPayment;
import com.keni.starter.modules.payments.dtos.PaymentResponse;
import com.keni.starter.modules.payments.dtos.UpdatePaymentStatusRequest;
import com.keni.starter.modules.subscriptions.SubscriptionRepository;
import com.keni.starter.modules.user.UserRepository;

@Service
public class PaymentService {
  private final PaymentRepository paymentRepository;
  private final UserRepository userRepository;
  private final SubscriptionRepository subscriptionRepository;

  public PaymentService(PaymentRepository paymentRepository, UserRepository userRepository,
      SubscriptionRepository subscriptionRepository) {
    this.paymentRepository = paymentRepository;
    this.userRepository = userRepository;
    this.subscriptionRepository = subscriptionRepository;
  }

  @Transactional
  public PaymentResponse createPayment(CreateRequestPayment request) {
    var user = userRepository.findById(request.userId())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    var subscription = subscriptionRepository.findById(request.subId()).orElseThrow(
        () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Subscription not found"));

    // Manual mapping xd
    var payment = new Payment();
    payment.setUser(user);
    payment.setSubscription(subscription);
    payment.setDatePaid(request.datePaid() == null ? Instant.now() : request.datePaid());
    payment.setAmount(request.amount());
    // Status is never taken from the client, otherwise anyone could self approve a payment
    payment.setStatus(PaymentStatus.PENDING);

    return PaymentResponse.from(paymentRepository.save(payment));
  }

  @Transactional
  public PaymentResponse updateStatus(UUID id, UpdatePaymentStatusRequest request) {
    var payment = paymentRepository.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found"));

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
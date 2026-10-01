package com.keni.starter.modules.userSubscriptions;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.keni.starter.modules.payments.Payment;
import com.keni.starter.modules.payments.PaymentRepository;
import com.keni.starter.modules.payments.PaymentStatus;
import com.keni.starter.modules.subscriptions.SubscriptionRepository;
import com.keni.starter.modules.user.User;
import com.keni.starter.modules.userSubscriptions.dtos.SubscribeRequest;
import com.keni.starter.modules.userSubscriptions.dtos.SubscribeResponse;
import com.keni.starter.modules.userSubscriptions.dtos.UserSubscriptionResponse;

@Service
public class UserSubscriptionService {
  private final UserSubscriptionRepository userSubscriptionRepository;
  private final SubscriptionRepository subscriptionRepository;
  private final PaymentRepository paymentRepository;

  public UserSubscriptionService(UserSubscriptionRepository userSubscriptionRepository,
      SubscriptionRepository subscriptionRepository, PaymentRepository paymentRepository) {
    this.userSubscriptionRepository = userSubscriptionRepository;
    this.subscriptionRepository = subscriptionRepository;
    this.paymentRepository = paymentRepository;
  }

  /**
   * Subscribe and pay in one shot. The two writes share a transaction, so you never
   * end up with an active subscription that was never paid for.
   *
   * @param currentUser the authenticated caller, the only user allowed to be subscribed
   */
  @Transactional
  public SubscribeResponse subscribe(User currentUser, SubscribeRequest request) {
    // the body may say who to subscribe, but it may only ever be the caller
    if (!currentUser.getId().equals(request.userId())) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN,
          "You can only subscribe yourself");
    }

    var subscription = subscriptionRepository.findById(request.subscriptionId()).orElseThrow(
        () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Subscription not found"));

    // the price is whatever the tier says it costs, whatever the client claims
    if (request.amount().compareTo(subscription.getPrice()) != 0) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
          "Amount does not match the price of " + subscription.getName());
    }

    if (userSubscriptionRepository.existsByUserIdAndSubscriptionId(request.userId(),
        request.subscriptionId())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT,
          "You already have a subscription to " + subscription.getName());
    }

    // Manual mapping xd
    var now = Instant.now();
    var userSubscription = new UserSubscription();
    userSubscription.setUser(currentUser);
    userSubscription.setSubscription(subscription);
    userSubscription.setStartedAt(now);
    userSubscription
        .setExpiresAt(now.plus(subscription.getDurationDays(), ChronoUnit.DAYS));

    var payment = new Payment();
    payment.setUser(currentUser);
    payment.setSubscription(subscription);
    payment.setDatePaid(request.datePaid() == null ? now : request.datePaid());
    payment.setAmount(subscription.getPrice());
    payment.setStatus(PaymentStatus.COMPLETED);

    var savedSubscription = userSubscriptionRepository.save(userSubscription);
    var savedPayment = paymentRepository.save(payment);

    return new SubscribeResponse(UserSubscriptionResponse.from(savedSubscription),
        com.keni.starter.modules.payments.dtos.PaymentResponse.from(savedPayment));
  }

  /** Scoped to the caller, the id never comes from the request. */
  public List<UserSubscriptionResponse> getByUserId(UUID userId) {
    return userSubscriptionRepository.findByUserId(userId).stream()
        .map(UserSubscriptionResponse::from).toList();
  }

  /** Admin only, guarded by SecurityConfig. */
  public List<UserSubscriptionResponse> getAll() {
    return userSubscriptionRepository.findAll().stream().map(UserSubscriptionResponse::from)
        .toList();
  }

  /** Admin only, who subscribed to this particular tier. */
  public List<UserSubscriptionResponse> getBySubscriptionId(UUID subscriptionId) {
    return userSubscriptionRepository.findBySubscriptionId(subscriptionId).stream()
        .map(UserSubscriptionResponse::from).toList();
  }
}
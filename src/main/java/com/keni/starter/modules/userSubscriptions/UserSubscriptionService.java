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
import com.keni.starter.modules.payments.dtos.PaymentResponse;
import com.keni.starter.modules.subscriptions.SubscriptionRepository;
import com.keni.starter.modules.user.User;
import com.keni.starter.modules.userSubscriptions.dtos.RenewRequest;
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

    // locked read so two concurrent subscribes cannot both miss an existing row
    var existing = userSubscriptionRepository.findForRenewal(request.userId(),
        request.subscriptionId());

    if (existing.isPresent() && existing.get().isActive()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT,
          "You already have an active subscription to " + subscription.getName());
    }

    // Manual mapping xd
    var now = Instant.now();
    // A cancelled or lapsed row is reused rather than inserted, because the table has a
    // unique constraint on (user_id, subscription_id). Refusing here would mean a
    // refunded user could never buy that tier again.
    var userSubscription = existing.orElseGet(UserSubscription::new);
    if (existing.isPresent()) {
      // clearing the cancellation is what revives it
      userSubscription.setCancelledAt(null);
    } else {
      userSubscription.setStartedAt(now);
    }
    userSubscription.setUser(currentUser);
    userSubscription.setSubscription(subscription);
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
        PaymentResponse.from(savedPayment));
  }

  /**
   * Renews a subscription the caller already holds by pushing expiresAt out by another
   * period and charging again.
   *
   * <p>The row is updated rather than a second one inserted, because the table has a
   * unique constraint on (user_id, subscription_id). What was paid is still recorded,
   * once per period, in the payments table.
   *
   * @param currentUser the authenticated caller, the only user allowed to renew
   */
  @Transactional
  public SubscribeResponse renew(User currentUser, RenewRequest request) {
    var subscription = subscriptionRepository.findById(request.subscriptionId()).orElseThrow(
        () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Subscription not found"));

    // the price is whatever the tier says it costs, whatever the client claims
    if (request.amount().compareTo(subscription.getPrice()) != 0) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
          "Amount does not match the price of " + subscription.getName());
    }

    // locked read, otherwise a concurrent renewal would overwrite this one
    var userSubscription = userSubscriptionRepository
        .findForRenewal(currentUser.getId(), request.subscriptionId())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
            "You do not have a subscription to " + subscription.getName()));

    if (!userSubscription.isActive()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT,
          "That subscription has ended, subscribe again instead of renewing it");
    }

    var now = Instant.now();
    var currentExpiry = userSubscription.getExpiresAt();

    // Renewing early keeps the time already paid for and adds the new period on the
    // end. Renewing a lapsed one starts again from today.
    var periodStarts = currentExpiry != null && currentExpiry.isAfter(now) ? currentExpiry : now;
    var periodEnds = periodStarts.plus(subscription.getDurationDays(), ChronoUnit.DAYS);

    userSubscription.setExpiresAt(periodEnds);

    var payment = new Payment();
    payment.setUser(currentUser);
    payment.setSubscription(subscription);
    payment.setDatePaid(now);
    payment.setAmount(subscription.getPrice());
    payment.setStatus(PaymentStatus.COMPLETED);

    var savedPayment = paymentRepository.save(payment);

    return new SubscribeResponse(UserSubscriptionResponse.from(userSubscription),
        PaymentResponse.from(savedPayment));
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
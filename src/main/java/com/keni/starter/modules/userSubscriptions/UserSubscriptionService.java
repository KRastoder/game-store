package com.keni.starter.modules.userSubscriptions;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.keni.starter.modules.common.PageResponse;
import com.keni.starter.modules.payments.Payment;
import com.keni.starter.modules.payments.PaymentRepository;
import com.keni.starter.modules.payments.PaymentService;
import com.keni.starter.modules.payments.PaymentStatus;
import com.keni.starter.modules.payments.dtos.PaymentResponse;
import com.keni.starter.modules.subscriptions.Subscription;
import com.keni.starter.modules.subscriptions.SubscriptionRepository;
import com.keni.starter.modules.user.User;
import com.keni.starter.modules.userSubscriptions.dtos.CancelSubscriptionResponse;
import com.keni.starter.modules.userSubscriptions.dtos.RenewRequest;
import com.keni.starter.modules.userSubscriptions.dtos.SubscribeRequest;
import com.keni.starter.modules.userSubscriptions.dtos.SubscribeResponse;
import com.keni.starter.modules.userSubscriptions.dtos.UserSubscriptionResponse;

@Service
public class UserSubscriptionService {
  private final UserSubscriptionRepository userSubscriptionRepository;
  private final SubscriptionRepository subscriptionRepository;
  private final PaymentRepository paymentRepository;
  private final PaymentService paymentService;
  private final Clock clock;

  public UserSubscriptionService(UserSubscriptionRepository userSubscriptionRepository,
      SubscriptionRepository subscriptionRepository, PaymentRepository paymentRepository,
      PaymentService paymentService, Clock clock) {
    this.userSubscriptionRepository = userSubscriptionRepository;
    this.subscriptionRepository = subscriptionRepository;
    this.paymentRepository = paymentRepository;
    this.paymentService = paymentService;
    this.clock = clock;
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

    var now = clock.instant();

    // locked read so two concurrent subscribes cannot both miss an existing row
    var existing = userSubscriptionRepository.findForRenewal(request.userId(),
        request.subscriptionId());

    if (existing.isPresent() && existing.get().isActive(now)) {
      throw new ResponseStatusException(HttpStatus.CONFLICT,
          "You already have an active subscription to " + subscription.getName());
    }

    // Manual mapping xd
    // A cancelled or lapsed row is reused rather than inserted, because the table has a
    // unique constraint on (user_id, subscription_id). Refusing here would mean a
    // refunded user could never buy that tier again.
    var userSubscription = existing.orElseGet(UserSubscription::new);
    if (existing.isPresent()) {
      // clearing the cancellation is what revives it
      userSubscription.setCancelledAt(null);
      userSubscription.setReminderSentAt(null);
    } else {
      userSubscription.setStartedAt(now);
    }
    userSubscription.setUser(currentUser);
    userSubscription.setSubscription(subscription);
    userSubscription
        .setExpiresAt(now.plus(subscription.getDurationDays(), ChronoUnit.DAYS));

    var payment = newPayment(currentUser, subscription,
        request.datePaid() == null ? now : request.datePaid());

    var savedSubscription = userSubscriptionRepository.save(userSubscription);
    var savedPayment = paymentRepository.save(payment);

    return new SubscribeResponse(UserSubscriptionResponse.from(savedSubscription, now),
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

    var now = clock.instant();
    if (!userSubscription.isActive(now)) {
      throw new ResponseStatusException(HttpStatus.CONFLICT,
          "That subscription has ended, subscribe again instead of renewing it");
    }

    var currentExpiry = userSubscription.getExpiresAt();

    // Renewing early keeps the time already paid for and adds the new period on the
    // end. Renewing a lapsed one starts again from today.
    var periodStarts = currentExpiry != null && currentExpiry.isAfter(now) ? currentExpiry : now;
    var periodEnds = periodStarts.plus(subscription.getDurationDays(), ChronoUnit.DAYS);

    userSubscription.setExpiresAt(periodEnds);
    // the old reminder referred to the old end date
    userSubscription.setReminderSentAt(null);

    var payment = newPayment(currentUser, subscription, now);

    var savedPayment = paymentRepository.save(payment);

    return new SubscribeResponse(UserSubscriptionResponse.from(userSubscription, now),
        PaymentResponse.from(savedPayment));
  }

  /**
   * Ends the subscription early, prorating whatever of the current period is unused.
   *
   * <p>The customer gets the unspent fraction of what they paid back, which is why this
   * is not the same as a full refund. If the period is already over, or almost entirely
   * used, nothing is refunded and the cancellation is free.
   *
   * <p>Owner or admin only. The id is in the path, so the owner check cannot be left to
   * the route matcher. Idempotent, because a double tap should not error.
   */
  @Transactional
  public CancelSubscriptionResponse cancel(User currentUser, boolean isAdmin, UUID id) {
    var userSubscription = userSubscriptionRepository.findForUpdate(id).orElseThrow(
        () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Subscription not found"));

    var isOwner = userSubscription.getUser().getId().equals(currentUser.getId());
    if (!isOwner && !isAdmin) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN,
          "You can only cancel your own subscription");
    }

    var now = clock.instant();
    userSubscription.cancel(now);
    var saved = userSubscriptionRepository.save(userSubscription);

    var refunded = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    var payment = latestCompletedPayment(userSubscription);
    if (payment != null) {
      var unspent = saved.unspentAmount(payment.getAmount(),
          saved.getSubscription().getDurationDays(), now);
      refunded = paymentService.refundPartially(payment, unspent);
    }

    var paymentResponse = refunded.signum() > 0 && payment != null
        ? PaymentResponse.from(payment)
        : null;

    return new CancelSubscriptionResponse(UserSubscriptionResponse.from(saved, now),
        paymentResponse, refunded);
  }

  /**
   * The payment covering the current period, which is the newest completed one. Older
   * payments belong to periods that have already been served.
   *
   * <p>Asked for as a single sorted row rather than every payment, so cancelling does not
   * load a user's whole payment history into memory to pick the newest row.
   */
  private Payment latestCompletedPayment(UserSubscription userSubscription) {
    var newestFirst = PageRequest.of(0, 1,
        Sort.by(Sort.Direction.DESC, "datePaid"));
    return paymentRepository
        .findByUserIdAndSubscriptionId(userSubscription.getUser().getId(),
            userSubscription.getSubscription().getId(), newestFirst)
        .stream()
        .filter(p -> p.getStatus() == PaymentStatus.COMPLETED)
        .findFirst()
        .orElse(null);
  }

  private Payment newPayment(User user, Subscription subscription, Instant paidAt) {
    var payment = new Payment();
    payment.setUser(user);
    payment.setSubscription(subscription);
    payment.setDatePaid(paidAt);
    payment.setAmount(subscription.getPrice());
    // Status is never taken from the client, otherwise anyone could self approve a payment
    payment.setStatus(PaymentStatus.COMPLETED);
    return payment;
  }

  /** Scoped to the caller, the id never comes from the request. */
  public PageResponse<UserSubscriptionResponse> getByUserId(UUID userId, Pageable pageable) {
    var now = clock.instant();
    return PageResponse.from(
        userSubscriptionRepository.findByUserId(userId, pageable)
            .map(us -> UserSubscriptionResponse.from(us, now)));
  }

  /** Admin only, guarded by SecurityConfig. */
  public PageResponse<UserSubscriptionResponse> getAll(Pageable pageable) {
    var now = clock.instant();
    return PageResponse.from(userSubscriptionRepository.findAll(pageable)
        .map(us -> UserSubscriptionResponse.from(us, now)));
  }

  /** Admin only, who subscribed to this particular tier. */
  public PageResponse<UserSubscriptionResponse> getBySubscriptionId(UUID subscriptionId,
      Pageable pageable) {
    var now = clock.instant();
    return PageResponse.from(
        userSubscriptionRepository.findBySubscriptionId(subscriptionId, pageable)
            .map(us -> UserSubscriptionResponse.from(us, now)));
  }
}
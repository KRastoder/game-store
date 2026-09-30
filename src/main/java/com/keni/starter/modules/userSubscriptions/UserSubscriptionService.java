package com.keni.starter.modules.userSubscriptions;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.keni.starter.modules.subscriptions.SubscriptionRepository;
import com.keni.starter.modules.user.User;
import com.keni.starter.modules.userSubscriptions.dtos.NewUserSubscriptionRequest;
import com.keni.starter.modules.userSubscriptions.dtos.UserSubscriptionResponse;

@Service
public class UserSubscriptionService {
  private final UserSubscriptionRepository userSubscriptionRepository;
  private final SubscriptionRepository subscriptionRepository;

  public UserSubscriptionService(UserSubscriptionRepository userSubscriptionRepository,
      SubscriptionRepository subscriptionRepository) {
    this.userSubscriptionRepository = userSubscriptionRepository;
    this.subscriptionRepository = subscriptionRepository;
  }

  /**
   * The user is taken from the security context rather than the request body, otherwise
   * anyone could subscribe somebody else.
   */
  @Transactional
  public UserSubscriptionResponse subscribe(User currentUser,
      NewUserSubscriptionRequest request) {
    var subscription = subscriptionRepository.findById(request.subscriptionId()).orElseThrow(
        () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Subscription not found"));

    if (userSubscriptionRepository.existsByUserIdAndSubscriptionId(currentUser.getId(),
        request.subscriptionId())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT,
          "You already have this subscription");
    }

    // Manual mapping xd
    var userSubscription = new UserSubscription();
    userSubscription.setUser(currentUser);
    userSubscription.setSubscription(subscription);
    userSubscription.setStartedAt(Instant.now());

    return UserSubscriptionResponse.from(userSubscriptionRepository.save(userSubscription));
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
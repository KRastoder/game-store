package com.keni.starter.modules.userSubscriptions;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.keni.starter.modules.subscriptions.SubscriptionRepository;
import com.keni.starter.modules.user.UserRepository;
import com.keni.starter.modules.userSubscriptions.dtos.NewUserSubscriptionRequest;
import com.keni.starter.modules.userSubscriptions.dtos.UserSubscriptionResponse;

@Service
public class UserSubscriptionService {
  private final UserSubscriptionRepository userSubscriptionRepository;
  private final UserRepository userRepository;
  private final SubscriptionRepository subscriptionRepository;

  public UserSubscriptionService(UserSubscriptionRepository userSubscriptionRepository,
      UserRepository userRepository, SubscriptionRepository subscriptionRepository) {
    this.userSubscriptionRepository = userSubscriptionRepository;
    this.userRepository = userRepository;
    this.subscriptionRepository = subscriptionRepository;
  }

  @Transactional
  public UserSubscriptionResponse subscribe(NewUserSubscriptionRequest request) {
    var user = userRepository.findById(request.userId())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    var subscription = subscriptionRepository.findById(request.subscriptionId()).orElseThrow(
        () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Subscription not found"));

    if (userSubscriptionRepository.existsByUserIdAndSubscriptionId(request.userId(),
        request.subscriptionId())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT,
          "User already has this subscription");
    }

    // Manual mapping xd
    var userSubscription = new UserSubscription();
    userSubscription.setUser(user);
    userSubscription.setSubscription(subscription);
    userSubscription.setStartedAt(Instant.now());

    return UserSubscriptionResponse.from(userSubscriptionRepository.save(userSubscription));
  }

  public List<UserSubscriptionResponse> getByUserId(UUID userId) {
    return userSubscriptionRepository.findByUserId(userId).stream()
        .map(UserSubscriptionResponse::from).toList();
  }

  public List<UserSubscriptionResponse> getBySubscriptionId(UUID subscriptionId) {
    return userSubscriptionRepository.findBySubscriptionId(subscriptionId).stream()
        .map(UserSubscriptionResponse::from).toList();
  }
}
package com.keni.starter.modules.subscriptions;

import java.util.UUID;

import org.springframework.data.domain.Pageable;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.keni.starter.modules.common.PageResponse;
import com.keni.starter.modules.subscriptions.dtos.NewSubscriptionRequest;
import com.keni.starter.modules.subscriptions.dtos.SubscriptionResponse;

@Service
public class SubscriptionService {
  private final SubscriptionRepository subscriptionRepository;

  public SubscriptionService(SubscriptionRepository subscriptionRepository) {
    this.subscriptionRepository = subscriptionRepository;
  }

  @Transactional
  public SubscriptionResponse createSubscription(NewSubscriptionRequest request) {
    if (subscriptionRepository.existsByName(request.name())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Subscription already exists");
    }

    // Manual mapping xd
    var subscription = new Subscription();
    subscription.setName(request.name());
    subscription.setPrice(request.price());
    subscription.setDurationDays(request.durationDays());

    return SubscriptionResponse.from(subscriptionRepository.save(subscription));
  }

  public PageResponse<SubscriptionResponse> getAllSubscriptions(Pageable pageable) {
    return PageResponse.from(
        subscriptionRepository.findAll(pageable).map(SubscriptionResponse::from));
  }

  public SubscriptionResponse getSubscriptionById(UUID id) {
    return subscriptionRepository.findById(id).map(SubscriptionResponse::from)
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Subscription not found"));
  }
}
package com.keni.starter.modules.subscriptions;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.keni.starter.modules.subscriptions.dtos.NewSubscriptionRequest;
import com.keni.starter.modules.subscriptions.dtos.SubscriptionResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/subscription")
public class SubscriptionController {
  private final SubscriptionService subscriptionService;

  public SubscriptionController(SubscriptionService subscriptionService) {
    this.subscriptionService = subscriptionService;
  }

  @PostMapping
  public SubscriptionResponse createSubscription(@Valid @RequestBody NewSubscriptionRequest request) {
    return subscriptionService.createSubscription(request);
  }

  @GetMapping
  public List<SubscriptionResponse> getAllSubscriptions() {
    return subscriptionService.getAllSubscriptions();
  }

  @GetMapping("/{id}")
  public SubscriptionResponse getSubscriptionById(@PathVariable UUID id) {
    return subscriptionService.getSubscriptionById(id);
  }
}
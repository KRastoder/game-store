package com.keni.starter.modules.userSubscriptions;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.keni.starter.modules.userSubscriptions.dtos.NewUserSubscriptionRequest;
import com.keni.starter.modules.userSubscriptions.dtos.UserSubscriptionResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/user-subscription")
public class UserSubscriptionController {
  private final UserSubscriptionService userSubscriptionService;

  public UserSubscriptionController(UserSubscriptionService userSubscriptionService) {
    this.userSubscriptionService = userSubscriptionService;
  }

  @PostMapping
  public UserSubscriptionResponse subscribe(@Valid @RequestBody NewUserSubscriptionRequest request) {
    return userSubscriptionService.subscribe(request);
  }

  @GetMapping("/user/{userId}")
  public List<UserSubscriptionResponse> getByUserId(@PathVariable UUID userId) {
    return userSubscriptionService.getByUserId(userId);
  }

  @GetMapping("/subscription/{subscriptionId}")
  public List<UserSubscriptionResponse> getBySubscriptionId(@PathVariable UUID subscriptionId) {
    return userSubscriptionService.getBySubscriptionId(subscriptionId);
  }
}
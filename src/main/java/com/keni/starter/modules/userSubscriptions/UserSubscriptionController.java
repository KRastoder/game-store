package com.keni.starter.modules.userSubscriptions;

import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import com.keni.starter.modules.common.PageResponse;
import com.keni.starter.modules.user.User;
import com.keni.starter.modules.userSubscriptions.dtos.CancelSubscriptionResponse;
import com.keni.starter.modules.userSubscriptions.dtos.RenewRequest;
import com.keni.starter.modules.userSubscriptions.dtos.SubscribeRequest;
import com.keni.starter.modules.userSubscriptions.dtos.SubscribeResponse;
import com.keni.starter.modules.userSubscriptions.dtos.UserSubscriptionResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/user-subscription")
public class UserSubscriptionController {
  private final UserSubscriptionService userSubscriptionService;

  public UserSubscriptionController(UserSubscriptionService userSubscriptionService) {
    this.userSubscriptionService = userSubscriptionService;
  }

  /** Any signed in user. Creates the subscription and its payment together. */
  @PostMapping
  public SubscribeResponse subscribe(@AuthenticationPrincipal User currentUser,
      @Valid @RequestBody SubscribeRequest request) {
    return userSubscriptionService.subscribe(currentUser, request);
  }

  /** Any signed in user. Pushes expiresAt out by another period and charges again. */
  @PostMapping("/renew")
  public SubscribeResponse renew(@AuthenticationPrincipal User currentUser,
      @Valid @RequestBody RenewRequest request) {
    return userSubscriptionService.renew(currentUser, request);
  }

  /**
   * Cancels by row id, so the owner check has to happen in the service. Route matching
   * cannot express "the caller owns this id", and pretending otherwise is how IDOR bugs
   * ship.
   */
  @PatchMapping("/{id}/cancel")
  public CancelSubscriptionResponse cancel(@AuthenticationPrincipal User currentUser,
      @PathVariable UUID id) {
    var isAdmin = currentUser.getAuthorities().stream()
        .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    return userSubscriptionService.cancel(currentUser, isAdmin, id);
  }

  @GetMapping("/me")
  public PageResponse<UserSubscriptionResponse> getMySubscriptions(
      @AuthenticationPrincipal User currentUser,
      @PageableDefault(size = 20, sort = "expiresAt") Pageable pageable) {
    return userSubscriptionService.getByUserId(currentUser.getId(), pageable);
  }

  /** Admin only. This is the "who bought what" view. */
  @GetMapping
  public PageResponse<UserSubscriptionResponse> getAll(
      @PageableDefault(size = 20, sort = "expiresAt") Pageable pageable) {
    return userSubscriptionService.getAll(pageable);
  }

  /** Admin only. */
  @GetMapping("/user/{userId}")
  public PageResponse<UserSubscriptionResponse> getByUserId(@PathVariable UUID userId,
      @PageableDefault(size = 20, sort = "expiresAt") Pageable pageable) {
    return userSubscriptionService.getByUserId(userId, pageable);
  }

  /** Admin only. */
  @GetMapping("/subscription/{subscriptionId}")
  public PageResponse<UserSubscriptionResponse> getBySubscriptionId(
      @PathVariable UUID subscriptionId,
      @PageableDefault(size = 20, sort = "expiresAt") Pageable pageable) {
    return userSubscriptionService.getBySubscriptionId(subscriptionId, pageable);
  }
}
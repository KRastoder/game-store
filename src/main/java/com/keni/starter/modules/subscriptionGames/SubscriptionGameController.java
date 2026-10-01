package com.keni.starter.modules.subscriptionGames;

import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.keni.starter.modules.common.PageResponse;
import com.keni.starter.modules.subscriptionGames.dtos.NewSubscriptionGameRequest;
import com.keni.starter.modules.subscriptionGames.dtos.SubscriptionGameResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/subscription-game")
public class SubscriptionGameController {
  private final SubscriptionGameService subscriptionGameService;

  public SubscriptionGameController(SubscriptionGameService subscriptionGameService) {
    this.subscriptionGameService = subscriptionGameService;
  }

  @PostMapping
  public SubscriptionGameResponse addGame(@Valid @RequestBody NewSubscriptionGameRequest request) {
    return subscriptionGameService.addGame(request);
  }

  @GetMapping("/subscription/{subscriptionId}")
  public PageResponse<SubscriptionGameResponse> getBySubscriptionId(
      @PathVariable UUID subscriptionId,
      @PageableDefault(size = 50, sort = "id") Pageable pageable) {
    return subscriptionGameService.getBySubscriptionId(subscriptionId, pageable);
  }

  @GetMapping("/game/{gameId}")
  public PageResponse<SubscriptionGameResponse> getByGameId(@PathVariable UUID gameId,
      @PageableDefault(size = 50, sort = "id") Pageable pageable) {
    return subscriptionGameService.getByGameId(gameId, pageable);
  }
}
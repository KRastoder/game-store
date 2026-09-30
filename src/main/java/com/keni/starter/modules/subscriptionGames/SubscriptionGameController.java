package com.keni.starter.modules.subscriptionGames;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
  public List<SubscriptionGameResponse> getBySubscriptionId(@PathVariable UUID subscriptionId) {
    return subscriptionGameService.getBySubscriptionId(subscriptionId);
  }

  @GetMapping("/game/{gameId}")
  public List<SubscriptionGameResponse> getByGameId(@PathVariable UUID gameId) {
    return subscriptionGameService.getByGameId(gameId);
  }
}
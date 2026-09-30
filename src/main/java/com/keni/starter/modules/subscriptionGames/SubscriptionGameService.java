package com.keni.starter.modules.subscriptionGames;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.keni.starter.modules.games.GameRepository;
import com.keni.starter.modules.subscriptionGames.dtos.NewSubscriptionGameRequest;
import com.keni.starter.modules.subscriptionGames.dtos.SubscriptionGameResponse;
import com.keni.starter.modules.subscriptions.SubscriptionRepository;

@Service
public class SubscriptionGameService {
  private final SubscriptionGameRepository subscriptionGameRepository;
  private final SubscriptionRepository subscriptionRepository;
  private final GameRepository gameRepository;

  public SubscriptionGameService(SubscriptionGameRepository subscriptionGameRepository,
      SubscriptionRepository subscriptionRepository, GameRepository gameRepository) {
    this.subscriptionGameRepository = subscriptionGameRepository;
    this.subscriptionRepository = subscriptionRepository;
    this.gameRepository = gameRepository;
  }

  @Transactional
  public SubscriptionGameResponse addGame(NewSubscriptionGameRequest request) {
    var subscription = subscriptionRepository.findById(request.subscriptionId()).orElseThrow(
        () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Subscription not found"));
    var game = gameRepository.findById(request.gameId())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Game not found"));

    if (subscriptionGameRepository.existsBySubscriptionIdAndGameId(request.subscriptionId(),
        request.gameId())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT,
          "Game is already part of this subscription");
    }

    // Manual mapping xd
    var subscriptionGame = new SubscriptionGame();
    subscriptionGame.setSubscription(subscription);
    subscriptionGame.setGame(game);

    return SubscriptionGameResponse.from(subscriptionGameRepository.save(subscriptionGame));
  }

  public List<SubscriptionGameResponse> getBySubscriptionId(UUID subscriptionId) {
    return subscriptionGameRepository.findBySubscriptionId(subscriptionId).stream()
        .map(SubscriptionGameResponse::from).toList();
  }

  public List<SubscriptionGameResponse> getByGameId(UUID gameId) {
    return subscriptionGameRepository.findByGameId(gameId).stream()
        .map(SubscriptionGameResponse::from).toList();
  }
}
package com.keni.starter.modules.subscriptionGames;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionGameRepository
    extends JpaRepository<SubscriptionGame, UUID> {

  List<SubscriptionGame> findBySubscriptionId(UUID subscriptionId);

  List<SubscriptionGame> findByGameId(UUID gameId);

  Optional<SubscriptionGame> findBySubscriptionIdAndGameId(UUID subscriptionId, UUID gameId);

  boolean existsBySubscriptionIdAndGameId(UUID subscriptionId, UUID gameId);
}

package com.keni.starter.modules.subscriptionGames;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionGameRepository
    extends JpaRepository<SubscriptionGame, UUID> {

  Page<SubscriptionGame> findBySubscriptionId(UUID subscriptionId, Pageable pageable);

  Page<SubscriptionGame> findByGameId(UUID gameId, Pageable pageable);

  /**
   * How many tiers list this game. A count rather than a boolean because the delete
   * refusal can then tell the admin how much has to be taken off first.
   */
  long countByGameId(UUID gameId);

  Optional<SubscriptionGame> findBySubscriptionIdAndGameId(UUID subscriptionId, UUID gameId);

  boolean existsBySubscriptionIdAndGameId(UUID subscriptionId, UUID gameId);
}
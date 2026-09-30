package com.keni.starter.modules.subscriptionGames;

import java.util.UUID;

import com.keni.starter.modules.games.Game;
import com.keni.starter.modules.subscriptions.Subscription;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "subscription_games", uniqueConstraints = @UniqueConstraint(columnNames = { "subscription_id",
    "game_id" }))
@Getter
@Setter
@NoArgsConstructor
public class SubscriptionGame {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "subscription_id")
  private Subscription subscription;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "game_id")
  private Game game;
}

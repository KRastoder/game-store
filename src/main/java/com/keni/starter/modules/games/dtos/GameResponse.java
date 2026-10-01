package com.keni.starter.modules.games.dtos;

import java.util.UUID;

import com.keni.starter.modules.games.Game;

/**
 * game response
 */
public record GameResponse(
    UUID id,
    String title,
    String description,
    String company) {

  public static GameResponse from(Game game) {
    return new GameResponse(game.getId(), game.getTitle(), game.getDescription(),
        game.getCompany());
  }
}
package com.keni.starter.modules.games;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.keni.starter.modules.games.dtos.GameResponse;
import com.keni.starter.modules.games.dtos.NewGameRequest;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/game")
public class GemeController {
  private final GameService gameService;

  public GemeController(GameService gameService) {
    this.gameService = gameService;
  }

  /**
   * Returns a DTO rather than the entity, which is what every other endpoint does. It
   * also means a column added to Game later cannot appear in the API by accident.
   */
  @PostMapping
  public GameResponse createGame(@Valid @RequestBody NewGameRequest request) {
    return gameService.createGame(request);
  }

}
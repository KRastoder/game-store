package com.keni.starter.modules.games;

import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.keni.starter.modules.common.PageResponse;
import com.keni.starter.modules.games.dtos.GameResponse;
import com.keni.starter.modules.games.dtos.NewGameRequest;
import com.keni.starter.modules.games.dtos.UpdateGameRequest;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/game")
public class GameController {
  private final GameService gameService;

  public GameController(GameService gameService) {
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

  /** Paged like every other list, so a large catalogue cannot be pulled in one request. */
  @GetMapping
  public PageResponse<GameResponse> getAllGames(
      @PageableDefault(size = 20, sort = "title") Pageable pageable) {
    return gameService.getAllGames(pageable);
  }

  @GetMapping("/{id}")
  public GameResponse getGameById(@PathVariable UUID id) {
    return gameService.getGameById(id);
  }

  /**
   * PUT rather than PATCH: the body carries every field, so a client cannot clear a
   * description by leaving it out and get a half applied edit instead.
   */
  @PutMapping("/{id}")
  public GameResponse updateGame(@PathVariable UUID id,
      @Valid @RequestBody UpdateGameRequest request) {
    return gameService.updateGame(id, request);
  }

  /**
   * 204 rather than a body echoing the deleted game. There is nothing left to describe,
   * and repeating it back invites a client to cache a copy it can no longer refresh.
   */
  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteGame(@PathVariable UUID id) {
    gameService.deleteGame(id);
  }

}
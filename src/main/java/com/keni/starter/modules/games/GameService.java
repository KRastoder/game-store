package com.keni.starter.modules.games;

import java.util.UUID;

import org.springframework.data.domain.Pageable;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.keni.starter.modules.common.PageResponse;
import com.keni.starter.modules.games.dtos.GameResponse;
import com.keni.starter.modules.games.dtos.NewGameRequest;
import com.keni.starter.modules.games.dtos.UpdateGameRequest;
import com.keni.starter.modules.subscriptionGames.SubscriptionGameRepository;

@Service
public class GameService {
  private final GameRepository gameRepository;
  private final SubscriptionGameRepository subscriptionGameRepository;

  public GameService(GameRepository gameRepository,
      SubscriptionGameRepository subscriptionGameRepository) {
    this.gameRepository = gameRepository;
    this.subscriptionGameRepository = subscriptionGameRepository;
  }

  @Transactional
  public GameResponse createGame(NewGameRequest request) {
    if (gameRepository.existsByTitle(request.title())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT,
          "A game with that title already exists");
    }

    // Manual mapping xd
    var game = new Game();
    game.setTitle(request.title());
    game.setCompany(request.company());
    game.setDescription(request.description());

    return GameResponse.from(gameRepository.save(game));
  }

  /**
   * Reading the catalogue is not an admin action, so this is open to any signed in
   * user, exactly like the tier list next to it.
   */
  public PageResponse<GameResponse> getAllGames(Pageable pageable) {
    return PageResponse.from(gameRepository.findAll(pageable).map(GameResponse::from));
  }

  public GameResponse getGameById(UUID id) {
    return gameRepository.findById(id).map(GameResponse::from)
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Game not found"));
  }

  @Transactional
  public GameResponse updateGame(UUID id, UpdateGameRequest request) {
    var game = findExisting(id);

    // The title is how the catalogue is identified, so it still has to stay unique
    // once edited. The id is excluded because the game is allowed to keep its own.
    if (gameRepository.existsByTitleAndIdNot(request.title(), id)) {
      throw new ResponseStatusException(HttpStatus.CONFLICT,
          "A game with that title already exists");
    }

    game.setTitle(request.title());
    game.setCompany(request.company());
    // Set even when the client sent null. This is a replace, not a merge, so omitting
    // the description has to clear it rather than quietly keep the old text.
    game.setDescription(request.description());

    return GameResponse.from(gameRepository.save(game));
  }

  @Transactional
  public void deleteGame(UUID id) {
    var game = findExisting(id);

    // A tier is something customers have already paid for. Removing the game from the
    // database would take it out of their access without warning and without leaving a
    // trace, so it has to come off the tiers first. Caught here rather than left to the
    // foreign key, which would only surface as an unexplained 500.
    var tiers = subscriptionGameRepository.countByGameId(id);
    if (tiers > 0) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Game is part of " + tiers
          + " subscription tier(s) and cannot be deleted until it is removed from them");
    }

    gameRepository.delete(game);
  }

  /** One lookup for every write path, so a missing game is reported the same way. */
  private Game findExisting(UUID id) {
    return gameRepository.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Game not found"));
  }

}
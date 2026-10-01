package com.keni.starter.modules.games;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.keni.starter.modules.games.dtos.GameResponse;
import com.keni.starter.modules.games.dtos.NewGameRequest;

@Service
public class GameService {
  private final GameRepository gameRepository;

  public GameService(GameRepository gameRepository) {
    this.gameRepository = gameRepository;
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

}
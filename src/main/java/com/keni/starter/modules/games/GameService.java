package com.keni.starter.modules.games;

import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.PostMapping;

import com.keni.starter.modules.games.dtos.NewGameRequest;

@Service
public class GameService {
  private final GameRepository gameRepository;

  public GameService(GameRepository gameRepository) {
    this.gameRepository = gameRepository;
  }

  @PostMapping("/")
  public Game createGame(NewGameRequest request) {
    var game = new Game();

    // Manual mapping xd
    game.setTitle(request.title());
    game.setCompany(request.company());
    game.setDescription(request.description());

    gameRepository.save(game);

    return game;
  }

}

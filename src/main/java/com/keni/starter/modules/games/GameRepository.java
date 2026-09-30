package com.keni.starter.modules.games;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface GameRepository extends JpaRepository<Game, UUID> {

  boolean existsByTitle(String title);
}

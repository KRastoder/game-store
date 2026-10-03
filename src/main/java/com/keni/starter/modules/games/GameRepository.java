package com.keni.starter.modules.games;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface GameRepository extends JpaRepository<Game, UUID> {

  boolean existsByTitle(String title);

  /**
   * The same uniqueness rule as above, but ignoring the row being edited. Without the
   * id exclusion, saving a game without touching its title looks like a clash with
   * itself and every update is rejected.
   */
  boolean existsByTitleAndIdNot(String title, UUID id);
}

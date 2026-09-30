package com.keni.starter.modules.games.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * newGame
 */
public record NewGameRequest(
    @NotBlank String title,
    @Size(max = 1000) String description,
    @NotBlank String company) {
}
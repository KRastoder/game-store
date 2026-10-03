package com.keni.starter.modules.games.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * updateGame
 *
 * <p>A full replacement rather than a patch, so the same validation as creation applies:
 * title and company are required, description is optional. A PATCH would have had to
 * treat every field as optional, and then a missing title would have to mean both
 * "leave it alone" and "you forgot", which are not the same thing.
 */
public record UpdateGameRequest(
    @NotBlank String title,
    @Size(max = 1000) String description,
    @NotBlank String company) {
}
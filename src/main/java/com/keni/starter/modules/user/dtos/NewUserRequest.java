package com.keni.starter.modules.user.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * newUser
 */
public record NewUserRequest(
    @NotBlank String userName,
    @NotNull @Size(min = 8, max = 64) String password) {
}
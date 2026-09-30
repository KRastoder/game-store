package com.keni.starter.modules.user.dtos;

import java.util.UUID;

import com.keni.starter.modules.user.Role;
import com.keni.starter.modules.user.User;

/**
 * user response without the password
 */
public record UserResponse(
    UUID id,
    String userName,
    Role role) {

  public static UserResponse from(User user) {
    return new UserResponse(user.getId(), user.getUsername(), user.getRole());
  }
}
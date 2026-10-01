package com.keni.starter.modules.user;

import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.keni.starter.modules.common.PageResponse;
import com.keni.starter.modules.user.dtos.NewUserRequest;
import com.keni.starter.modules.user.dtos.UserResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/user")
public class UserController {
  private final UserService userService;

  public UserController(UserService userService) {
    this.userService = userService;
  }

  @PostMapping
  public UserResponse createUser(@Valid @RequestBody NewUserRequest request) {
    return userService.createUser(request);
  }

  /** Taken from the security context, never from a request parameter. */
  @GetMapping("/me")
  public UserResponse getMe(@AuthenticationPrincipal User currentUser) {
    return UserResponse.from(currentUser);
  }

  @GetMapping
  public PageResponse<UserResponse> getAllUsers(
      @PageableDefault(size = 20, sort = "userName") Pageable pageable) {
    return userService.getAllUsers(pageable);
  }

  @GetMapping("/{id}")
  public UserResponse getUserById(@PathVariable UUID id) {
    return userService.getUserById(id);
  }
}
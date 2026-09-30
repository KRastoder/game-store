package com.keni.starter.modules.user;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.keni.starter.modules.user.dtos.NewUserRequest;
import com.keni.starter.modules.user.dtos.UserResponse;

@Service
public class UserService {
  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;

  public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
  }

  @Transactional
  public UserResponse createUser(NewUserRequest request) {
    if (userRepository.existsByUserName(request.userName())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already taken");
    }

    // Manual mapping xd
    var user = new User();
    user.setUserName(request.userName());
    user.setPassword(passwordEncoder.encode(request.password()));

    return UserResponse.from(userRepository.save(user));
  }

  public List<UserResponse> getAllUsers() {
    return userRepository.findAll().stream().map(UserResponse::from).toList();
  }

  public UserResponse getUserById(UUID id) {
    return userRepository.findById(id).map(UserResponse::from)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
  }
}
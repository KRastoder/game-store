package com.keni.starter.config;

import jakarta.servlet.DispatcherType;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

import com.keni.starter.modules.user.UserRepository;

@EnableWebSecurity
@Configuration
public class SecurityConfig {

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  /**
   * Returning our own User entity means the controller can pull the id straight off
   * the principal with @AuthenticationPrincipal instead of looking the user up again.
   */
  @Bean
  public UserDetailsService userDetailsService(UserRepository userRepository) {
    return username -> userRepository.findByUserName(username)
        .orElseThrow(() -> new UsernameNotFoundException("No user named " + username));
  }

  /**
   * Rules are matched top to bottom and the first match wins. Every "/me" rule
   * therefore has to sit ABOVE the "/something/*" wildcard that would otherwise
   * swallow it and lock the owner out of their own data.
   */
  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http.authorizeHttpRequests(auth -> auth
        // sendError() triggers an ERROR dispatch to /error, which re-enters this chain.
        // Without this the URI is /error there, so every rule above misses it, it falls
        // through to anyRequest().authenticated() and anonymous callers get a 401 for
        // what is really a 400 such as a validation failure on POST /user.
        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()

        // ---- open ----
        .requestMatchers(HttpMethod.POST, "/user").permitAll()

        // ---- my own data, any signed in user ----
        .requestMatchers(HttpMethod.GET, "/user/me").authenticated()
        .requestMatchers(HttpMethod.GET, "/user-subscription/me").authenticated()
        .requestMatchers(HttpMethod.GET, "/payment/me").authenticated()
        // subscribing and renewing both charge the caller, so both stay open to any
        // signed in user. Neither takes a userId, the caller comes from the token.
        .requestMatchers(HttpMethod.POST, "/user-subscription").authenticated()
        .requestMatchers(HttpMethod.POST, "/user-subscription/renew").authenticated()

        // ---- admin only ----
        .requestMatchers(HttpMethod.POST, "/game").hasRole("ADMIN")
        .requestMatchers(HttpMethod.POST, "/subscription").hasRole("ADMIN")
        .requestMatchers(HttpMethod.POST, "/subscription-game").hasRole("ADMIN")
        .requestMatchers(HttpMethod.PATCH, "/payment/*/status").hasRole("ADMIN")
        .requestMatchers(HttpMethod.GET, "/user").hasRole("ADMIN")
        .requestMatchers(HttpMethod.GET, "/user/*").hasRole("ADMIN")
        // who bought which subscription, and every payment, are both admin only
        .requestMatchers(HttpMethod.GET, "/user-subscription").hasRole("ADMIN")
        .requestMatchers(HttpMethod.GET, "/user-subscription/subscription/*").hasRole("ADMIN")
        .requestMatchers(HttpMethod.GET, "/user-subscription/user/*").hasRole("ADMIN")
        .requestMatchers(HttpMethod.GET, "/payment").hasRole("ADMIN")

        // everything left still needs a signed in user
        .anyRequest().authenticated())
        // no cookies or sessions are used, so there is nothing for CSRF to ride on
        .csrf(csfr -> csfr.disable())
        .formLogin(form -> form.disable())
        // 403 for a signed in user with the wrong role, 401 for no credentials at all
        .httpBasic(basic -> basic
            .authenticationEntryPoint((request, response, exception) -> response.sendError(401)));

    return http.build();

  }

}
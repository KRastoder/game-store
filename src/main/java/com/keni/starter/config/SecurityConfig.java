package com.keni.starter.config;

import java.net.URI;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.web.SecurityFilterChain;

import com.keni.starter.modules.user.UserRepository;

import tools.jackson.databind.ObjectMapper;

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
  public SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper objectMapper)
      throws Exception {
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
        // cancelling is open to any signed in user, the service then checks that the row
        // is theirs. An admin may cancel anyone's.
        .requestMatchers(HttpMethod.PATCH, "/user-subscription/*/cancel").authenticated()

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
        .httpBasic(basic -> basic.authenticationEntryPoint((request, response, exception) ->
            writeProblem(response, HttpServletResponse.SC_UNAUTHORIZED,
                "Full authentication is required to access this resource", objectMapper)))
        // 401 and 403 are raised before a controller ever runs, so @RestControllerAdvice
        // never sees them. Writing the RFC 9457 body here is what keeps the error shape
        // identical across the filter chain and the controllers.
        .exceptionHandling(ex -> ex
            .accessDeniedHandler((request, response, exception) ->
                writeProblem(response, HttpServletResponse.SC_FORBIDDEN,
                    "You do not have permission to access this resource", objectMapper)));

    return http.build();

  }

  /**
   * Serialises a ProblemDetail straight to the servlet response.
   *
   * <p>Uses the application's own ObjectMapper rather than a new one, so a body written
   * here is byte for byte the same shape as one written by GlobalExceptionHandler.
   */
  private static void writeProblem(HttpServletResponse response, int status, String detail,
      ObjectMapper objectMapper) throws java.io.IOException {
    var problem = ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(status), detail);
    problem.setType(URI.create("about:blank"));

    response.setStatus(status);
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    objectMapper.writeValue(response.getWriter(), problem);
  }

}
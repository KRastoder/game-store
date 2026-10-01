package com.keni.starter.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The application reads the current time from an injected Clock instead of calling
 * Instant.now() directly.
 *
 * <p>Prorated refunds and expiry reminders are both pure functions of "now", and both are
 * easy to get subtly wrong. With a real clock in place they can be tested at an exact
 * moment, such as halfway through a billing period, which is the case that catches
 * off-by-one-day bugs.
 */
@Configuration
public class TimeConfig {

  @Bean
  public Clock clock() {
    return Clock.systemUTC();
  }
}
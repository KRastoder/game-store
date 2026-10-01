package com.keni.starter.modules.notifications;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Supplies the logging notifier unless a real one is provided.
 *
 * <p>Declared as a @Bean rather than a scanned @Component on purpose: the
 * ConditionalOnMissingBean check runs during configuration class parsing, and it cannot
 * reliably see component-scanned candidates. Declared this way a deployment can supply its
 * own ExpiryNotifier bean and this one steps aside.
 */
@Configuration
public class ExpiryNotifierConfig {

  @Bean
  @ConditionalOnMissingBean(ExpiryNotifier.class)
  public ExpiryNotifier loggingExpiryNotifier() {
    return new LoggingExpiryNotifier();
  }

  /**
   * Default notifier, which logs instead of sending.
   *
   * <p>Chosen so the scheduled job works end to end with no SMTP server and no side
   * effects in tests.
   */
  static class LoggingExpiryNotifier implements ExpiryNotifier {

    private static final Logger log = LoggerFactory.getLogger(LoggingExpiryNotifier.class);

    @Override
    public void sendExpiryReminder(
        com.keni.starter.modules.userSubscriptions.UserSubscription userSubscription,
        int daysRemaining) {
      log.info("Expiry reminder: user={} tier={} expiresAt={} daysRemaining={}",
          userSubscription.getUser().getUsername(),
          userSubscription.getSubscription().getName(),
          userSubscription.getExpiresAt(), daysRemaining);
    }
  }
}
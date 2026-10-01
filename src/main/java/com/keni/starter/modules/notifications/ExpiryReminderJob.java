package com.keni.starter.modules.notifications;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.keni.starter.modules.userSubscriptions.UserSubscriptionRepository;

/**
 * Warns people before their subscription runs out.
 *
 * <p>Runs on a schedule rather than at cancellation time, because the whole point is to
 * give someone the chance to renew. A subscription only ever receives one reminder:
 * reminder_sent_at is stamped in the same transaction as the send, so re-running the job
 * cannot produce duplicates even if two runs overlap.
 */
@Component
public class ExpiryReminderJob {

  /** How far ahead to warn. */
  static final int DEFAULT_LEAD_DAYS = 3;

  private static final Logger log = LoggerFactory.getLogger(ExpiryReminderJob.class);

  private final UserSubscriptionRepository userSubscriptionRepository;
  private final ExpiryNotifier notifier;
  private final Clock clock;

  public ExpiryReminderJob(UserSubscriptionRepository userSubscriptionRepository,
      ExpiryNotifier notifier, Clock clock) {
    this.userSubscriptionRepository = userSubscriptionRepository;
    this.notifier = notifier;
    this.clock = clock;
  }

  @Scheduled(cron = "${app.reminders.cron:0 0 7 * * *}")
  @Transactional
  public void sendReminders() {
    sendReminders(DEFAULT_LEAD_DAYS);
  }

  /**
   * @param leadDays how far ahead of expiry to warn
   * @return how many reminders were sent
   */
  @Transactional
  public int sendReminders(int leadDays) {
    var now = clock.instant();
    var horizon = now.plus(leadDays, ChronoUnit.DAYS);

    var expiring = userSubscriptionRepository.findExpiringWithoutReminder(now, horizon);

    for (var subscription : expiring) {
      var daysRemaining = daysUntil(subscription.getExpiresAt(), now);
      notifier.sendExpiryReminder(subscription, daysRemaining);
      subscription.markReminderSent(now);
      userSubscriptionRepository.save(subscription);
    }

    if (!expiring.isEmpty()) {
      log.info("Sent {} expiry reminder(s)", expiring.size());
    }
    return expiring.size();
  }

  /** Rounded up, so 30 hours still reads as "2 days left" rather than "1". */
  private static int daysUntil(Instant expiresAt, Instant now) {
    var millis = expiresAt.toEpochMilli() - now.toEpochMilli();
    if (millis <= 0) {
      return 0;
    }
    return (int) Math.ceil(millis / (double) java.time.Duration.ofDays(1).toMillis());
  }
}
package com.keni.starter;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import com.keni.starter.modules.notifications.ExpiryNotifier;
import com.keni.starter.modules.notifications.ExpiryReminderJob;
import com.keni.starter.modules.subscriptions.Subscription;
import com.keni.starter.modules.subscriptions.SubscriptionRepository;
import com.keni.starter.modules.user.Role;
import com.keni.starter.modules.user.User;
import com.keni.starter.modules.user.UserRepository;
import com.keni.starter.modules.userSubscriptions.UserSubscription;
import com.keni.starter.modules.userSubscriptions.UserSubscriptionRepository;

/**
 * The reminder job is a scheduled task, which is the kind of thing that silently does
 * nothing forever if its query or its guard is wrong. These tests drive it directly.
 *
 * <p>The real Spring bean is used, with only the Clock and the notifier replaced. Building
 * the job by hand would bypass its transaction, and the notifier touches lazy relations
 * that need an open session.
 */
@SpringBootTest
class ExpiryReminderJobTest {

  static final Instant NOW = Instant.parse("2026-06-01T12:00:00Z");

  static class RecordingNotifier implements ExpiryNotifier {
    final List<String> sent = new ArrayList<>();

    @Override
    public void sendExpiryReminder(UserSubscription userSubscription, int daysRemaining) {
      sent.add(userSubscription.getSubscription().getName() + "@"
          + userSubscription.getUser().getUsername() + " in " + daysRemaining + "d");
    }
  }

  @TestConfiguration
  static class Overrides {
    @Bean
    @Primary
    Clock fixedClock() {
      return Clock.fixed(NOW, ZoneOffset.UTC);
    }

    @Bean
    @Primary
    RecordingNotifier recordingNotifier() {
      return new RecordingNotifier();
    }
  }

  @Autowired
  private UserSubscriptionRepository userSubscriptionRepository;
  @Autowired
  private SubscriptionRepository subscriptionRepository;
  @Autowired
  private UserRepository userRepository;
  @Autowired
  private ExpiryReminderJob job;
  @Autowired
  private RecordingNotifier notifier;

  private Subscription tier;

  @BeforeEach
  void setUp() {
    userSubscriptionRepository.deleteAll();
    subscriptionRepository.deleteAll();
    userRepository.deleteAll();
    notifier.sent.clear();

    tier = new Subscription();
    tier.setName("Gold");
    tier.setPrice(new BigDecimal("9.99"));
    tier.setDurationDays(30);
    tier = subscriptionRepository.saveAndFlush(tier);
  }

  private UserSubscription row(String name, Instant expiresAt) {
    var user = new User();
    user.setUserName(name);
    user.setPassword("hashed");
    user.setRole(Role.USER);
    user = userRepository.saveAndFlush(user);

    var row = new UserSubscription();
    row.setUser(user);
    row.setSubscription(tier);
    row.setStartedAt(expiresAt.minus(30, ChronoUnit.DAYS));
    row.setExpiresAt(expiresAt);
    return userSubscriptionRepository.saveAndFlush(row);
  }

  @Test
  void warnsAboutSubscriptionsExpiringInsideTheWindow() {
    row("soon", NOW.plus(2, ChronoUnit.DAYS));

    assertThat(job.sendReminders(3)).isEqualTo(1);
    assertThat(notifier.sent).hasSize(1);
    assertThat(notifier.sent.get(0)).contains("soon").contains("in 2d");
  }

  @Test
  void staysQuietAboutSubscriptionsOutsideTheWindow() {
    row("later", NOW.plus(20, ChronoUnit.DAYS));
    assertThat(job.sendReminders(3)).isZero();
    assertThat(notifier.sent).isEmpty();
  }

  @Test
  void neverWarnsAboutSomethingAlreadyExpired() {
    row("gone", NOW.minus(1, ChronoUnit.DAYS));
    assertThat(job.sendReminders(3)).isZero();
    assertThat(notifier.sent).isEmpty();
  }

  @Test
  void neverWarnsTwice() {
    var target = row("soon", NOW.plus(1, ChronoUnit.DAYS));

    assertThat(job.sendReminders(3)).isEqualTo(1);
    // a second run must find nothing, or every customer would be emailed hourly
    assertThat(job.sendReminders(3)).isZero();
    assertThat(notifier.sent).hasSize(1);

    var reloaded = userSubscriptionRepository.findById(target.getId()).orElseThrow();
    assertThat(reloaded.getReminderSentAt()).isEqualTo(NOW);
  }

  @Test
  void renewingResetsTheFlagSoTheNextPeriodWarnsAgain() {
    var target = row("soon", NOW.plus(1, ChronoUnit.DAYS));
    assertThat(job.sendReminders(3)).isEqualTo(1);

    target.setReminderSentAt(null);
    target.setExpiresAt(NOW.plus(2, ChronoUnit.DAYS));
    userSubscriptionRepository.saveAndFlush(target);

    assertThat(job.sendReminders(3)).isEqualTo(1);
    assertThat(notifier.sent).hasSize(2);
  }

  @Test
  void cancelledSubscriptionsAreNeverWarnedAbout() {
    var target = row("cancelled", NOW.plus(1, ChronoUnit.DAYS));
    target.cancel(NOW);
    userSubscriptionRepository.saveAndFlush(target);

    assertThat(job.sendReminders(3)).isZero();
    assertThat(notifier.sent).isEmpty();
  }

  @Test
  void countsManySubscriptionsCorrectly() {
    row("a", NOW.plus(1, ChronoUnit.DAYS));
    row("b", NOW.plus(2, ChronoUnit.DAYS));
    row("c", NOW.plus(3, ChronoUnit.DAYS));
    row("far", NOW.plus(90, ChronoUnit.DAYS));

    assertThat(job.sendReminders(3)).isEqualTo(3);
    assertThat(notifier.sent).hasSize(3);
  }

  @Test
  void daysRemainingIsRoundedUpSoNobodyIsToldZeroDays() {
    row("soon", NOW.plus(1, ChronoUnit.DAYS).plus(1, ChronoUnit.HOURS));
    job.sendReminders(3);
    // one day and one hour left should read as 2 days, not 1
    assertThat(notifier.sent.get(0)).contains("in 2d");
  }
}
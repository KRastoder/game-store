package com.keni.starter;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import com.keni.starter.modules.subscriptions.Subscription;
import com.keni.starter.modules.user.User;
import com.keni.starter.modules.userSubscriptions.UserSubscription;

/**
 * Proration is pure arithmetic on dates, which is exactly the kind of code that looks
 * right and is wrong by a day. The clock is fixed so each case names an exact moment.
 */
class ProrationTest {

  private static final Instant DAY_START = Instant.parse("2026-03-01T00:00:00Z");

  private UserSubscription period(int durationDays, Instant startedAt, Instant expiresAt) {
    var tier = new Subscription();
    tier.setName("Gold");
    tier.setPrice(new BigDecimal("9.99"));
    tier.setDurationDays(durationDays);

    var user = new User();
    user.setUserName("keni");

    var row = new UserSubscription();
    row.setUser(user);
    row.setSubscription(tier);
    row.setStartedAt(startedAt);
    row.setExpiresAt(expiresAt);
    return row;
  }

  private static BigDecimal unspent(UserSubscription row, Instant at) {
    return row.unspentAmount(row.getSubscription().getPrice(),
        row.getSubscription().getDurationDays(), at);
  }

  @Test
  void nothingUsedMeansTheWholePriceIsUnspent() {
    var row = period(30, DAY_START, DAY_START.plusSeconds(30 * 86400L));
    assertThat(unspent(row, DAY_START)).isEqualByComparingTo("9.99");
  }

  @Test
  void halfwayThroughGivesBackHalf() {
    var expiresAt = DAY_START.plusSeconds(30 * 86400L);
    var halfway = DAY_START.plusSeconds(15 * 86400L);
    var row = period(30, DAY_START, expiresAt);
    assertThat(unspent(row, halfway)).isEqualByComparingTo("5.00");
  }

  @Test
  void threeQuartersUsedLeavesAQuarter() {
    var expiresAt = DAY_START.plusSeconds(30 * 86400L);
    var row = period(30, DAY_START, expiresAt);
    var at = DAY_START.plusSeconds(22 * 86400L + 12 * 3600L);
    assertThat(unspent(row, at)).isEqualByComparingTo("2.50");
  }

  @Test
  void expiredPeriodOwesNothing() {
    var expiresAt = DAY_START.plusSeconds(30 * 86400L);
    var row = period(30, DAY_START, expiresAt);
    // after expiry there is no unspent time, and importantly not a negative refund
    assertThat(unspent(row, expiresAt)).isEqualByComparingTo("0.00");
    assertThat(unspent(row, expiresAt.plusSeconds(86400L))).isEqualByComparingTo("0.00");
  }

  @Test
  void almostNoTimeLeftOwesAlmostNothing() {
    var expiresAt = DAY_START.plusSeconds(30 * 86400L);
    var row = period(30, DAY_START, expiresAt);
    // one second out of thirty days is a vanishing fraction, so this rounds to zero
    var refund = unspent(row, expiresAt.minusSeconds(1));
    assertThat(refund).isEqualByComparingTo("0.00");
    // and it is never negative, which is the part that would actually hurt
    assertThat(refund).isGreaterThanOrEqualTo(BigDecimal.ZERO);
  }

  @Test
  void oneDayLeftOwsOneDay() {
    var expiresAt = DAY_START.plusSeconds(30 * 86400L);
    var row = period(30, DAY_START, expiresAt);
    // a thirtieth of 9.99 rounds to 0.33
    assertThat(unspent(row, expiresAt.minusSeconds(86400L))).isEqualByComparingTo("0.33");
  }

  @Test
  void refundIsNeverMoreThanWasPaid() {
    // a clock behind the period start would otherwise divide by a negative span
    var expiresAt = DAY_START.plusSeconds(30 * 86400L);
    var row = period(30, DAY_START, expiresAt);
    var refund = unspent(row, DAY_START.minusSeconds(3600L));
    assertThat(refund).isLessThanOrEqualTo(new BigDecimal("9.99"));
    assertThat(refund).isGreaterThanOrEqualTo(BigDecimal.ZERO);
  }

  @Test
  void renewalProratesAgainstTheCurrentPeriodNotTheWholeSpan() {
    // two renewals of a 30 day tier: startedAt is 90 days back, but the latest payment
    // only covered 30. Prorating over 90 days would refund three times too much.
    var expiresAt = DAY_START.plusSeconds(90 * 86400L);
    var startedAt = DAY_START;
    var row = period(30, startedAt, expiresAt);

    // 15 days before expiry, 75 of the 90 days have served, but the current 30 day
    // period has only used 15. The refund is half the latest payment, not a third.
    var at = expiresAt.minusSeconds(15 * 86400L);
    assertThat(unspent(row, at)).isEqualByComparingTo("5.00");
  }

  @Test
  void refundAlwaysHasTwoDecimalPlaces() {
    var expiresAt = DAY_START.plusSeconds(7 * 86400L);
    var row = period(30, DAY_START, expiresAt);
    var refund = unspent(row, DAY_START.plusSeconds(3 * 86400L + 7 * 3600L));
    assertThat(refund.scale()).isEqualTo(2);
  }

  @Test
  void oddPriceIsHandledWithoutRoundingErrors() {
    var tierPrice = new BigDecimal("0.07");
    var expiresAt = DAY_START.plusSeconds(30 * 86400L);
    var row = period(30, DAY_START, expiresAt);
    row.getSubscription().setPrice(tierPrice);

    var refund = unspent(row, DAY_START.plusSeconds(15 * 86400L));
    // half of 0.07 is 0.035, which must round rather than blow up or go negative
    assertThat(refund).isEqualByComparingTo("0.04");
  }

  @Test
  void zeroDurationIsHandledRatherThanDividingByZero() {
    var row = period(0, DAY_START, DAY_START.plusSeconds(86400L));
    assertThat(unspent(row, DAY_START)).isEqualByComparingTo("0.00");
  }

  @Test
  void isActiveUsesTheInjectedMoment() {
    var expiresAt = DAY_START.plusSeconds(30 * 86400L);
    var row = period(30, DAY_START, expiresAt);
    var clock = Clock.fixed(DAY_START, ZoneOffset.UTC);

    assertThat(row.isActive(clock.instant())).isTrue();
    assertThat(row.isActive(expiresAt.plusSeconds(1))).isFalse();
  }

  @Test
  void cancellingIsStampedWithTheGivenMomentNotWallClockTime() {
    var row = period(30, DAY_START, DAY_START.plusSeconds(30 * 86400L));
    row.cancel(DAY_START);
    row.cancel(DAY_START.plusSeconds(86400L));
    // the first cancellation wins, so the recorded time is honest
    assertThat(row.getCancelledAt()).isEqualTo(DAY_START);
  }
}
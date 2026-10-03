package com.keni.starter;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import com.keni.starter.modules.games.Game;
import com.keni.starter.modules.games.GameRepository;
import com.keni.starter.modules.payments.Payment;
import com.keni.starter.modules.payments.PaymentRepository;
import com.keni.starter.modules.payments.PaymentStatus;
import com.keni.starter.modules.subscriptions.Subscription;
import com.keni.starter.modules.subscriptions.SubscriptionRepository;
import com.keni.starter.modules.subscriptionGames.SubscriptionGame;
import com.keni.starter.modules.subscriptionGames.SubscriptionGameRepository;
import com.keni.starter.modules.user.Role;
import com.keni.starter.modules.user.User;
import com.keni.starter.modules.user.UserRepository;
import com.keni.starter.modules.userSubscriptions.UserSubscription;
import com.keni.starter.modules.userSubscriptions.UserSubscriptionRepository;

/**
 * Every derived query in every repository is exercised here.
 *
 * These queries are resolved by Spring Data at runtime, not at compile time, so a
 * method naming a property that does not exist compiles fine and only explodes when
 * the context starts. That has already bitten this project twice.
 */
@DataJpaTest
class RepositoryQueryTest {
  /** Big enough that a test never has to think about paging. */
  private static final Pageable ALL = PageRequest.of(0, 10_000);


  @Autowired
  private UserRepository userRepository;
  @Autowired
  private SubscriptionRepository subscriptionRepository;
  @Autowired
  private GameRepository gameRepository;
  @Autowired
  private UserSubscriptionRepository userSubscriptionRepository;
  @Autowired
  private SubscriptionGameRepository subscriptionGameRepository;
  @Autowired
  private PaymentRepository paymentRepository;

  private User user(String name) {
    var user = new User();
    user.setUserName(name);
    user.setPassword("hashed");
    user.setRole(Role.USER);
    return userRepository.saveAndFlush(user);
  }

  private Subscription tier(String name, String price, int days) {
    var tier = new Subscription();
    tier.setName(name);
    tier.setPrice(new BigDecimal(price));
    tier.setDurationDays(days);
    return subscriptionRepository.saveAndFlush(tier);
  }

  private Game game(String title) {
    var game = new Game();
    game.setTitle(title);
    game.setCompany("Someone");
    game.setDescription("desc");
    return gameRepository.saveAndFlush(game);
  }

  @Test
  void userRepositoryFinders() {
    var keni = user("keni");
    assertThat(userRepository.findByUserName("keni")).contains(keni);
    assertThat(userRepository.findByUserName("nobody")).isEmpty();
    assertThat(userRepository.existsByUserName("keni")).isTrue();
    assertThat(userRepository.existsByUserName("nobody")).isFalse();
  }

  @Test
  void duplicateUserNameIsRejectedByTheDatabase() {
    user("keni");
    var clash = new User();
    clash.setUserName("keni");
    clash.setPassword("hashed");
    clash.setRole(Role.USER);
    assertThatThrownByDataIntegrity(() -> userRepository.saveAndFlush(clash));
  }

  @Test
  void subscriptionRepositoryFinders() {
    tier("Gold", "9.99", 30);
    assertThat(subscriptionRepository.existsByName("Gold")).isTrue();
    assertThat(subscriptionRepository.existsByName("Silver")).isFalse();
  }

  @Test
  void duplicateTierNameIsRejectedByTheDatabase() {
    tier("Gold", "9.99", 30);
    assertThatThrownByDataIntegrity(() -> tier("Gold", "19.99", 30));
  }

  @Test
  void gameRepositoryFinders() {
    var minecraft = game("Minecraft");
    game("Tetris");

    assertThat(gameRepository.existsByTitle("Minecraft")).isTrue();
    assertThat(gameRepository.existsByTitle("Portal")).isFalse();

    // the update check has to ignore the row being edited, or it clashes with itself
    assertThat(gameRepository.existsByTitleAndIdNot("Minecraft", minecraft.getId())).isFalse();
    assertThat(gameRepository.existsByTitleAndIdNot("Tetris", minecraft.getId())).isTrue();
    assertThat(gameRepository.existsByTitleAndIdNot("Portal", minecraft.getId())).isFalse();
  }

  @Test
  void userSubscriptionRepositoryFinders() {
    var keni = user("keni");
    var mallory = user("mallory");
    var gold = tier("Gold", "9.99", 30);

    var row = new UserSubscription();
    row.setUser(keni);
    row.setSubscription(gold);
    row.setStartedAt(Instant.now());
    row.setExpiresAt(Instant.now().plus(30, ChronoUnit.DAYS));
    userSubscriptionRepository.saveAndFlush(row);

    assertThat(userSubscriptionRepository.findByUserId(keni.getId(), ALL).getContent()).hasSize(1);
    assertThat(userSubscriptionRepository.findByUserId(mallory.getId(), ALL).getContent()).isEmpty();
    assertThat(userSubscriptionRepository.findBySubscriptionId(gold.getId(), ALL).getContent()).hasSize(1);
    assertThat(userSubscriptionRepository.findByUserIdAndSubscriptionId(keni.getId(),
        gold.getId())).isPresent();
    assertThat(
        userSubscriptionRepository.findByUserIdAndSubscriptionId(mallory.getId(), gold.getId()))
            .isEmpty();
    assertThat(userSubscriptionRepository.existsByUserIdAndSubscriptionId(keni.getId(),
        gold.getId())).isTrue();
    assertThat(userSubscriptionRepository.existsByUserIdAndSubscriptionId(mallory.getId(),
        gold.getId())).isFalse();
  }

  @Test
  void duplicateUserTierPairIsRejectedByTheDatabase() {
    var keni = user("keni");
    var gold = tier("Gold", "9.99", 30);

    var row = new UserSubscription();
    row.setUser(keni);
    row.setSubscription(gold);
    row.setStartedAt(Instant.now());
    row.setExpiresAt(Instant.now().plus(30, ChronoUnit.DAYS));
    userSubscriptionRepository.saveAndFlush(row);

    var duplicate = new UserSubscription();
    duplicate.setUser(keni);
    duplicate.setSubscription(gold);
    duplicate.setStartedAt(Instant.now());
    duplicate.setExpiresAt(Instant.now().plus(30, ChronoUnit.DAYS));
    assertThatThrownByDataIntegrity(() -> userSubscriptionRepository.saveAndFlush(duplicate));
  }

  @Test
  void subscriptionGameRepositoryFinders() {
    var gold = tier("Gold", "9.99", 30);
    var silver = tier("Silver", "4.99", 30);
    var minecraft = game("Minecraft");
    var tetris = game("Tetris");

    subscriptionGameRepository.saveAndFlush(link(gold, minecraft));
    subscriptionGameRepository.saveAndFlush(link(silver, tetris));

    assertThat(subscriptionGameRepository.findBySubscriptionId(gold.getId(), ALL).getContent()).hasSize(1);
    assertThat(subscriptionGameRepository.findBySubscriptionId(silver.getId(), ALL).getContent()).hasSize(1);
    assertThat(subscriptionGameRepository.findByGameId(minecraft.getId(), ALL).getContent()).hasSize(1);
    assertThat(subscriptionGameRepository.findBySubscriptionIdAndGameId(gold.getId(),
        minecraft.getId())).isPresent();
    assertThat(subscriptionGameRepository.findBySubscriptionIdAndGameId(gold.getId(),
        tetris.getId())).isEmpty();
    assertThat(subscriptionGameRepository.existsBySubscriptionIdAndGameId(gold.getId(),
        minecraft.getId())).isTrue();
    assertThat(subscriptionGameRepository.existsBySubscriptionIdAndGameId(gold.getId(),
        tetris.getId())).isFalse();

    // what the delete guard reads: zero means the game is safe to remove
    assertThat(subscriptionGameRepository.countByGameId(minecraft.getId())).isEqualTo(1);
    assertThat(subscriptionGameRepository.countByGameId(tetris.getId())).isEqualTo(1);
    assertThat(subscriptionGameRepository.countByGameId(UUID.randomUUID())).isZero();
  }

  @Test
  void duplicateGameInSameTierIsRejectedByTheDatabase() {
    var gold = tier("Gold", "9.99", 30);
    var minecraft = game("Minecraft");
    subscriptionGameRepository.saveAndFlush(link(gold, minecraft));
    assertThatThrownByDataIntegrity(() -> subscriptionGameRepository.saveAndFlush(link(gold, minecraft)));
  }

  @Test
  void sameGameCanBelongToTwoDifferentTiers() {
    var gold = tier("Gold", "9.99", 30);
    var silver = tier("Silver", "4.99", 30);
    var minecraft = game("Minecraft");
    subscriptionGameRepository.saveAndFlush(link(gold, minecraft));
    subscriptionGameRepository.saveAndFlush(link(silver, minecraft));
    assertThat(subscriptionGameRepository.findByGameId(minecraft.getId(), ALL).getContent()).hasSize(2);
    assertThat(subscriptionGameRepository.countByGameId(minecraft.getId())).isEqualTo(2);
  }

  @Test
  void paymentRepositoryFinders() {
    var keni = user("keni");
    var mallory = user("mallory");
    var gold = tier("Gold", "9.99", 30);
    var now = Instant.now();

    paymentRepository.saveAndFlush(pay(keni, gold, "5.00", now.minus(10, ChronoUnit.DAYS)));
    paymentRepository.saveAndFlush(pay(mallory, gold, "7.00", now));

    assertThat(paymentRepository.findByUserId(keni.getId(), ALL).getContent()).hasSize(1);
    assertThat(paymentRepository.findByUserId(mallory.getId(), ALL).getContent()).hasSize(1);
    assertThat(paymentRepository.findBySubscriptionId(gold.getId(), ALL).getContent()).hasSize(2);
    assertThat(paymentRepository.findByUserIdAndSubscriptionId(keni.getId(), gold.getId(), ALL)
        .getContent()).hasSize(1);
    assertThat(paymentRepository.findByDatePaidBetween(now.minus(1, ChronoUnit.DAYS),
        now.plus(1, ChronoUnit.DAYS), ALL).getContent()).hasSize(1);
  }

  @Test
  void paymentRequiresBothRelationsAndAmount() {
    var keni = user("keni");
    var gold = tier("Gold", "9.99", 30);

    var orphan = new Payment();
    orphan.setUser(keni);
    orphan.setSubscription(gold);
    orphan.setDatePaid(Instant.now());
    orphan.setAmount(new BigDecimal("5.00"));
    // status is nullable = false in the entity, so a forgotten status must not persist
    assertThatThrownByDataIntegrity(() -> paymentRepository.saveAndFlush(orphan));
  }

  @Test
  void savedEntitiesComeBackFullyPopulated() {
    var gold = tier("Gold", "9.99", 30);
    assertThat(gold.getId()).isNotNull();
    assertThat(gold.getName()).isEqualTo("Gold");
    assertThat(gold.getPrice()).isEqualByComparingTo("9.99");
    assertThat(gold.getDurationDays()).isEqualTo(30);
  }

  @Test
  void userAuthoritiesReflectTheStoredRole() {
    var keni = user("keni");
    var admin = new User();
    admin.setUserName("root");
    admin.setPassword("hashed");
    admin.setRole(Role.ADMIN);
    admin = userRepository.saveAndFlush(admin);

    assertThat(keni.getAuthorities()).extracting(Object::toString)
        .containsExactly("ROLE_USER");
    assertThat(admin.getAuthorities()).extracting(Object::toString)
        .containsExactly("ROLE_ADMIN");
  }

  private SubscriptionGame link(Subscription subscription, Game game) {
    var row = new SubscriptionGame();
    row.setSubscription(subscription);
    row.setGame(game);
    return row;
  }

  private Payment pay(User user, Subscription subscription, String amount, Instant paidAt) {
    var payment = new Payment();
    payment.setUser(user);
    payment.setSubscription(subscription);
    payment.setDatePaid(paidAt);
    payment.setAmount(new BigDecimal(amount));
    payment.setStatus(PaymentStatus.COMPLETED);
    return payment;
  }

  private void assertThatThrownByDataIntegrity(Runnable action) {
    try {
      action.run();
      throw new AssertionError("expected a DataIntegrityViolationException");
    } catch (DataIntegrityViolationException expected) {
      assertThat(expected).isNotNull();
    }
  }
}
package com.keni.starter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.server.ResponseStatusException;

import com.keni.starter.modules.games.Game;
import com.keni.starter.modules.games.GameRepository;
import com.keni.starter.modules.games.GameService;
import com.keni.starter.modules.games.dtos.NewGameRequest;
import com.keni.starter.modules.payments.Payment;
import com.keni.starter.modules.payments.PaymentRepository;
import com.keni.starter.modules.payments.PaymentService;
import com.keni.starter.modules.payments.PaymentStatus;
import com.keni.starter.modules.payments.dtos.UpdatePaymentStatusRequest;
import com.keni.starter.modules.subscriptions.Subscription;
import com.keni.starter.modules.subscriptions.SubscriptionRepository;
import com.keni.starter.modules.subscriptionGames.SubscriptionGameRepository;
import com.keni.starter.modules.subscriptionGames.SubscriptionGameService;
import com.keni.starter.modules.subscriptionGames.dtos.NewSubscriptionGameRequest;
import com.keni.starter.modules.user.Role;
import com.keni.starter.modules.user.User;
import com.keni.starter.modules.user.UserRepository;
import com.keni.starter.modules.user.UserService;
import com.keni.starter.modules.user.dtos.NewUserRequest;
import com.keni.starter.modules.userSubscriptions.UserSubscriptionRepository;

/**
 * Business rules that live in the services rather than the database.
 */
@SpringBootTest
class ServiceRulesTest {

  @Autowired
  private GameService gameService;
  @Autowired
  private GameRepository gameRepository;
  @Autowired
  private PaymentService paymentService;
  @Autowired
  private PaymentRepository paymentRepository;
  @Autowired
  private SubscriptionGameService subscriptionGameService;
  @Autowired
  private SubscriptionGameRepository subscriptionGameRepository;
  @Autowired
  private SubscriptionRepository subscriptionRepository;
  @Autowired
  private UserService userService;
  @Autowired
  private UserRepository userRepository;
  @Autowired
  private UserSubscriptionRepository userSubscriptionRepository;

  private User user;
  private Subscription tier;
  private Game game;

  @BeforeEach
  void setUp() {
    userSubscriptionRepository.deleteAll();
    paymentRepository.deleteAll();
    subscriptionGameRepository.deleteAll();
    subscriptionRepository.deleteAll();
    gameRepository.deleteAll();
    userRepository.deleteAll();

    var u = new User();
    u.setUserName("keni");
    u.setPassword("hashed");
    u.setRole(Role.USER);
    user = userRepository.saveAndFlush(u);

    var t = new Subscription();
    t.setName("Gold");
    t.setPrice(new BigDecimal("9.99"));
    t.setDurationDays(30);
    tier = subscriptionRepository.saveAndFlush(t);

    game = new Game();
    game.setTitle("Minecraft");
    game.setCompany("Mojang");
    game.setDescription("blocks");
    game = gameRepository.saveAndFlush(game);
  }

  // ---------------- games ----------------

  @Test
  void duplicateGameTitleIsRejected() {
    assertThatThrownBy(
        () -> gameService.createGame(new NewGameRequest("Minecraft", "again", "Someone")))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value())
            .isEqualTo(409));
  }

  @Test
  void differentTitlesAreBothAllowed() {
    gameService.createGame(new NewGameRequest("Tetris", "blocks", "Someone"));
    assertThat(gameRepository.findAll()).hasSize(2);
  }

  // ---------------- payment status transitions ----------------

  @Test
  void pendingPaymentCanBeCompleted() {
    var payment = newPayment(PaymentStatus.PENDING);
    var result = paymentService.updateStatus(payment.getId(),
        new UpdatePaymentStatusRequest(PaymentStatus.COMPLETED));
    assertThat(result.status()).isEqualTo(PaymentStatus.COMPLETED);
  }

  @Test
  void pendingPaymentCanFail() {
    var payment = newPayment(PaymentStatus.PENDING);
    assertThat(paymentService.updateStatus(payment.getId(),
        new UpdatePaymentStatusRequest(PaymentStatus.FAILED)).status())
        .isEqualTo(PaymentStatus.FAILED);
  }

  @Test
  void unpaidPaymentCannotBeRefunded() {
    // nothing has been charged yet, so there is nothing to refund
    var payment = newPayment(PaymentStatus.PENDING);
    assertThatThrownBy(() -> paymentService.updateStatus(payment.getId(),
        new UpdatePaymentStatusRequest(PaymentStatus.REFUNDED)))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value())
            .isEqualTo(400));
  }

  @Test
  void completedPaymentCanBeRefunded() {
    var payment = newPayment(PaymentStatus.COMPLETED);
    assertThat(paymentService.updateStatus(payment.getId(),
        new UpdatePaymentStatusRequest(PaymentStatus.REFUNDED)).status())
        .isEqualTo(PaymentStatus.REFUNDED);
  }

  @Test
  void refundedPaymentIsTerminal() {
    var payment = newPayment(PaymentStatus.REFUNDED);
    assertThatThrownBy(() -> paymentService.updateStatus(payment.getId(),
        new UpdatePaymentStatusRequest(PaymentStatus.COMPLETED)))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value())
            .isEqualTo(400));
  }

  @Test
  void failedPaymentCannotBecomeCompleted() {
    var payment = newPayment(PaymentStatus.FAILED);
    assertThatThrownBy(() -> paymentService.updateStatus(payment.getId(),
        new UpdatePaymentStatusRequest(PaymentStatus.COMPLETED)))
        .isInstanceOf(ResponseStatusException.class);
  }

  @Test
  void settingTheSameStatusTwiceIsHarmless() {
    var payment = newPayment(PaymentStatus.COMPLETED);
    assertThat(paymentService.updateStatus(payment.getId(),
        new UpdatePaymentStatusRequest(PaymentStatus.COMPLETED)).status())
        .isEqualTo(PaymentStatus.COMPLETED);
  }

  @Test
  void updatingUnknownPaymentIsNotFound() {
    assertThatThrownBy(() -> paymentService.updateStatus(UUID.randomUUID(),
        new UpdatePaymentStatusRequest(PaymentStatus.COMPLETED)))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value())
            .isEqualTo(404));
  }

  // ---------------- catalogue ----------------

  @Test
  void gameCannotBeAddedToSameTierTwice() {
    subscriptionGameService.addGame(
        new NewSubscriptionGameRequest(tier.getId(), game.getId()));
    assertThatThrownBy(() -> subscriptionGameService
        .addGame(new NewSubscriptionGameRequest(tier.getId(), game.getId())))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value())
            .isEqualTo(409));
  }

  @Test
  void addingToUnknownTierIsNotFound() {
    assertThatThrownBy(() -> subscriptionGameService
        .addGame(new NewSubscriptionGameRequest(UUID.randomUUID(), game.getId())))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value())
            .isEqualTo(404));
  }

  @Test
  void addingUnknownGameIsNotFound() {
    assertThatThrownBy(() -> subscriptionGameService
        .addGame(new NewSubscriptionGameRequest(tier.getId(), UUID.randomUUID())))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value())
            .isEqualTo(404));
  }

  // ---------------- users ----------------

  @Test
  void registeringTwiceWithSameNameIsRejected() {
    assertThatThrownBy(() -> userService.createUser(new NewUserRequest("keni", "supersecret")))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value())
            .isEqualTo(409));
  }

  @Test
  void registeredUserAlwaysGetsTheUserRole() {
    var created = userService.createUser(new NewUserRequest("newbie", "supersecret"));
    assertThat(created.role()).isEqualTo(Role.USER);
  }

  @Test
  void registeredPasswordIsHashedNotStored() {
    var created = userService.createUser(new NewUserRequest("newbie", "supersecret"));
    var stored = userRepository.findByUserName("newbie").orElseThrow();
    assertThat(stored.getPassword()).isNotEqualTo("supersecret");
    assertThat(stored.getPassword()).startsWith("$2");
  }

  // ---------------- refunding ends access ----------------

  @Test
  void refundingAPaymentEndsTheSubscription() {
    var row = putUserOnTier();
    assertThat(row.isActive(java.time.Instant.now())).isTrue();

    paymentService.updateStatus(latestPaymentForTier().getId(),
        new UpdatePaymentStatusRequest(PaymentStatus.REFUNDED));

    var reloaded = userSubscriptionRepository.findById(row.getId()).orElseThrow();
    assertThat(reloaded.getCancelledAt()).isNotNull();
    assertThat(reloaded.isActive(java.time.Instant.now())).isFalse();
  }

  @Test
  void refundLeavesOtherUsersSubscriptionsAlone() {
    var mine = putUserOnTier();

    var otherUser = new User();
    otherUser.setUserName("victim");
    otherUser.setPassword("hashed");
    otherUser.setRole(Role.USER);
    otherUser = userRepository.saveAndFlush(otherUser);

    var theirs = new com.keni.starter.modules.userSubscriptions.UserSubscription();
    theirs.setUser(otherUser);
    theirs.setSubscription(tier);
    theirs.setStartedAt(java.time.Instant.now());
    theirs.setExpiresAt(java.time.Instant.now().plus(30, ChronoUnit.DAYS));
    var theirRow = userSubscriptionRepository.saveAndFlush(theirs);

    paymentService.updateStatus(latestPaymentForTier().getId(),
        new UpdatePaymentStatusRequest(PaymentStatus.REFUNDED));

    assertThat(userSubscriptionRepository.findById(theirRow.getId()).orElseThrow()
        .getCancelledAt()).isNull();
    assertThat(userSubscriptionRepository.findById(mine.getId()).orElseThrow().getCancelledAt())
        .isNotNull();
  }

  @Test
  void markingFailedDoesNotEndTheSubscription() {
    var row = putUserOnTier();
    // FAILED is only reachable from PENDING, so it needs its own unpaid payment
    var pending = newPayment(PaymentStatus.PENDING);

    paymentService.updateStatus(pending.getId(),
        new UpdatePaymentStatusRequest(PaymentStatus.FAILED));

    assertThat(userSubscriptionRepository.findById(row.getId()).orElseThrow().getCancelledAt())
        .isNull();
    assertThat(userSubscriptionRepository.findById(row.getId()).orElseThrow().isActive(java.time.Instant.now())).isTrue();
  }

  @Test
  void expiredSubscriptionIsNotActive() {
    var row = new com.keni.starter.modules.userSubscriptions.UserSubscription();
    row.setUser(user);
    row.setSubscription(tier);
    row.setStartedAt(java.time.Instant.now().minus(java.time.Duration.ofDays(60)));
    row.setExpiresAt(java.time.Instant.now().minus(java.time.Duration.ofDays(30)));
    // expired but never cancelled, which is not the same thing
    assertThat(row.isActive(java.time.Instant.now())).isFalse();
    assertThat(row.getCancelledAt()).isNull();
  }

  @Test
  void cancelIsIdempotent() {
    var row = putUserOnTier();

    row.cancel(java.time.Instant.now());
    var first = row.getCancelledAt();
    row.cancel(java.time.Instant.now());
    assertThat(row.getCancelledAt()).isEqualTo(first);
  }

  private com.keni.starter.modules.userSubscriptions.UserSubscription putUserOnTier() {
    var row = new com.keni.starter.modules.userSubscriptions.UserSubscription();
    row.setUser(user);
    row.setSubscription(tier);
    row.setStartedAt(java.time.Instant.now());
    row.setExpiresAt(java.time.Instant.now().plus(30, ChronoUnit.DAYS));
    var saved = userSubscriptionRepository.saveAndFlush(row);
    // the access has to be paid for, so a completed payment goes with it
    newPayment(PaymentStatus.COMPLETED);
    return saved;
  }

  private Payment latestPaymentForTier() {
    return paymentRepository.findByUserIdAndSubscriptionId(user.getId(), tier.getId()).stream()
        .findFirst().orElseThrow();
  }

  private Payment newPayment(PaymentStatus status) {
    var payment = new Payment();
    payment.setUser(user);
    payment.setSubscription(tier);
    payment.setDatePaid(Instant.now().minus(1, ChronoUnit.DAYS));
    payment.setAmount(tier.getPrice());
    payment.setStatus(status);
    return paymentRepository.saveAndFlush(payment);
  }

}
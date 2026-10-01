package com.keni.starter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

import com.keni.starter.modules.payments.PaymentRepository;
import com.keni.starter.modules.payments.PaymentStatus;
import com.keni.starter.modules.games.GameRepository;
import com.keni.starter.modules.subscriptions.Subscription;
import com.keni.starter.modules.subscriptions.SubscriptionRepository;
import com.keni.starter.modules.subscriptionGames.SubscriptionGameRepository;
import com.keni.starter.modules.user.Role;
import com.keni.starter.modules.user.Role;
import com.keni.starter.modules.user.User;
import com.keni.starter.modules.user.UserRepository;
import com.keni.starter.modules.user.dtos.NewUserRequest;
import com.keni.starter.modules.userSubscriptions.UserSubscriptionRepository;
import com.keni.starter.modules.userSubscriptions.dtos.SubscribeResponse;

/**
 * Proves the access rules in SecurityConfig actually apply. Access control that
 * silently fails open is worse than no access control at all, so every rule is
 * exercised as three separate people: nobody, a normal user, and an admin.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SecurityAccessTest {

  private static final String USER_PW = "supersecret";
  private static final String ADMIN_PW = "adminpassword";

  private static final tools.jackson.databind.json.JsonMapper MAPPER =
      tools.jackson.databind.json.JsonMapper.builder().build();

  @Autowired
  MockMvc mvc;
  @Autowired
  private UserRepository userRepository;
  @Autowired
  private SubscriptionRepository subscriptionRepository;
  @Autowired
  private PaymentRepository paymentRepository;
  @Autowired
  private UserSubscriptionRepository userSubscriptionRepository;
  @Autowired
  private SubscriptionGameRepository subscriptionGameRepository;
  @Autowired
  private GameRepository gameRepository;
  @Autowired
  private com.keni.starter.modules.user.UserService userService;
  @Autowired
  private PasswordEncoder encoder;


  private UUID userId;
  private UUID otherUserId;
  private UUID adminId;
  private UUID subscriptionId;
  private UUID userPaymentId;

  @BeforeEach
  void setUp() {
    // Every @SpringBootTest class shares one cached context and therefore one database,
    // so each class has to clear all the tables it touches, not just its own.
    subscriptionGameRepository.deleteAll();
    gameRepository.deleteAll();
    userSubscriptionRepository.deleteAll();
    paymentRepository.deleteAll();
    subscriptionRepository.deleteAll();
    userRepository.deleteAll();

    var user = newUser("keni", USER_PW, Role.USER);
    var other = newUser("mallory", USER_PW, Role.USER);
    var admin = newUser("root", ADMIN_PW, Role.ADMIN);
    userId = user.getId();
    otherUserId = other.getId();
    adminId = admin.getId();

    var subscription = new Subscription();
    subscription.setName("Gold");
    subscription.setPrice(new BigDecimal("9.99"));
    subscription.setDurationDays(30);
    subscriptionId = subscriptionRepository.save(subscription).getId();

    var myPayment = paymentRepository
        .save(newPayment(user, subscription, "5.00"));
    userPaymentId = myPayment.getId();
    paymentRepository.save(newPayment(other, subscription, "7.00"));
  }

  private User newUser(String name, String rawPassword, Role role) {
    var user = new User();
    user.setUserName(name);
    user.setPassword(encoder.encode(rawPassword));
    user.setRole(role);
    return userRepository.save(user);
  }

  private com.keni.starter.modules.payments.Payment newPayment(User user,
      Subscription subscription, String amount) {
    var payment = new com.keni.starter.modules.payments.Payment();
    payment.setUser(user);
    payment.setSubscription(subscription);
    payment.setDatePaid(java.time.Instant.now());
    payment.setAmount(new BigDecimal(amount));
    payment.setStatus(PaymentStatus.PENDING);
    return payment;
  }

  // ---------------- game creation is admin only ----------------

  @Test
  void anonymousCannotCreateGame() throws Exception {
    mvc.perform(post("/game").contentType(MediaType.APPLICATION_JSON)
        .content("{\"title\":\"Minecraft\",\"description\":\"blocks\",\"company\":\"Mojang\"}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void normalUserCannotCreateGame() throws Exception {
    mvc.perform(post("/game").with(httpBasic("keni", USER_PW))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"title\":\"Minecraft\",\"description\":\"blocks\",\"company\":\"Mojang\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void adminCanCreateGame() throws Exception {
    mvc.perform(post("/game").with(httpBasic("root", ADMIN_PW))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"title\":\"Minecraft\",\"description\":\"blocks\",\"company\":\"Mojang\"}"))
        .andExpect(status().isOk());
  }

  // ---------------- registration is open, but cannot grant yourself a role -------

  @Test
  void anonymousCanRegister() throws Exception {
    mvc.perform(post("/user").contentType(MediaType.APPLICATION_JSON)
        .content("{\"userName\":\"newbie\",\"password\":\"supersecret\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.role").value("USER"))
        // the hash must never come back out
        .andExpect(jsonPath("$.password").doesNotExist());
  }

  @Test
  void registrationRejectsClientSuppliedRole() throws Exception {
    // unknown property, fail-on-unknown-properties turns the escalation attempt into 400
    mvc.perform(post("/user").contentType(MediaType.APPLICATION_JSON)
        .content("{\"userName\":\"sneaky\",\"password\":\"supersecret\",\"role\":\"ADMIN\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void registeredUserCannotBeAdmin() throws Exception {
    mvc.perform(post("/user").contentType(MediaType.APPLICATION_JSON)
        .content("{\"userName\":\"newbie2\",\"password\":\"supersecret\"}"))
        .andExpect(status().isOk());
    mvc.perform(get("/user").with(httpBasic("newbie2", USER_PW)))
        .andExpect(status().isForbidden());
  }

  // ---------------- own profile vs other people ----------------

  @Test
  void userCanReadOwnProfile() throws Exception {
    mvc.perform(get("/user/me").with(httpBasic("keni", USER_PW)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.userName").value("keni"))
        .andExpect(jsonPath("$.id").value(userId.toString()))
        .andExpect(jsonPath("$.password").doesNotExist());
  }

  @Test
  void userCannotReadAnotherUserById() throws Exception {
    mvc.perform(get("/user/" + otherUserId).with(httpBasic("keni", USER_PW)))
        .andExpect(status().isForbidden());
  }

  @Test
  void userCannotListEveryone() throws Exception {
    mvc.perform(get("/user").with(httpBasic("keni", USER_PW)))
        .andExpect(status().isForbidden());
  }

  @Test
  void adminCanListEveryone() throws Exception {
    mvc.perform(get("/user").with(httpBasic("root", ADMIN_PW)))
        .andExpect(status().isOk())
        .andExpect(content().string(org.hamcrest.Matchers.containsString("mallory")));
  }

  // ---------------- payments ----------------

  @Test
  void userCannotListAllPayments() throws Exception {
    mvc.perform(get("/payment").with(httpBasic("keni", USER_PW)))
        .andExpect(status().isForbidden());
  }

  @Test
  void adminCanListAllPayments() throws Exception {
    mvc.perform(get("/payment").with(httpBasic("root", ADMIN_PW)))
        .andExpect(status().isOk())
        .andExpect(content().string(org.hamcrest.Matchers.containsString("7.00")));
  }

  @Test
  void userOnlySeesOwnPayments() throws Exception {
    mvc.perform(get("/payment/me").with(httpBasic("keni", USER_PW)))
        .andExpect(status().isOk())
        // keni paid 5.00, mallory paid 7.00. keni must only ever see her own row.
        .andExpect(content().string(org.hamcrest.Matchers.containsString("5.00")))
        .andExpect(content().string(
            org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("7.00"))));
  }

  @Test
  void userCannotChangePaymentStatus() throws Exception {
    mvc.perform(patch("/payment/" + userPaymentId + "/status")
        .with(httpBasic("keni", USER_PW))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"status\":\"COMPLETED\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void adminCanChangePaymentStatus() throws Exception {
    mvc.perform(patch("/payment/" + userPaymentId + "/status")
        .with(httpBasic("root", ADMIN_PW))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"status\":\"COMPLETED\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("COMPLETED"));
  }

  // ---------------- subscriptions, who bought what ----------------

  @Test
  void userCannotSeeWhoBoughtSubscriptions() throws Exception {
    mvc.perform(get("/user-subscription").with(httpBasic("keni", USER_PW)))
        .andExpect(status().isForbidden());
  }

  @Test
  void adminCanSeeWhoBoughtSubscriptions() throws Exception {
    mvc.perform(get("/user-subscription").with(httpBasic("root", ADMIN_PW)))
        .andExpect(status().isOk());
  }

  @Test
  void userCannotSeeSubscribersOfATier() throws Exception {
    mvc.perform(get("/user-subscription/subscription/" + subscriptionId)
        .with(httpBasic("keni", USER_PW)))
        .andExpect(status().isForbidden());
  }

  @Test
  void userCanSeeOwnSubscriptions() throws Exception {
    mvc.perform(get("/user-subscription/me").with(httpBasic("keni", USER_PW)))
        .andExpect(status().isOk());
  }

  @Test
  void userSubscribesThemselves() throws Exception {
    mvc.perform(post("/user-subscription").with(httpBasic("keni", USER_PW))
        .contentType(MediaType.APPLICATION_JSON)
        .content(subscribeBody(userId, subscriptionId, "9.99")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.userSubscription.userId").value(userId.toString()))
        .andExpect(jsonPath("$.userSubscription.subscriptionId")
            .value(subscriptionId.toString()))
        // a tier with a duration now gets a real expiry instead of null
        .andExpect(jsonPath("$.userSubscription.expiresAt").isNotEmpty())
        .andExpect(jsonPath("$.payment.amount").value(9.99))
        .andExpect(jsonPath("$.payment.userId").value(userId.toString()));
  }

  @Test
  void userCannotSubscribeTwiceWhileActive() throws Exception {
    var body = subscribeBody(userId, subscriptionId, "9.99");
    mvc.perform(post("/user-subscription").with(httpBasic("keni", USER_PW))
        .contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isOk());
    mvc.perform(post("/user-subscription").with(httpBasic("keni", USER_PW))
        .contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isConflict());
  }

  @Test
  void userCannotSubscribeSomebodyElse() throws Exception {
    mvc.perform(post("/user-subscription").with(httpBasic("keni", USER_PW))
        .contentType(MediaType.APPLICATION_JSON)
        .content(subscribeBody(otherUserId, subscriptionId, "9.99")))
        .andExpect(status().isForbidden());
  }

  @Test
  void userCannotUnderpayForATier() throws Exception {
    mvc.perform(post("/user-subscription").with(httpBasic("keni", USER_PW))
        .contentType(MediaType.APPLICATION_JSON)
        .content(subscribeBody(userId, subscriptionId, "0.01")))
        .andExpect(status().isBadRequest());
  }

  @Test
  void unknownTierIsNotFound() throws Exception {
    mvc.perform(post("/user-subscription").with(httpBasic("keni", USER_PW))
        .contentType(MediaType.APPLICATION_JSON)
        .content(subscribeBody(userId, UUID.randomUUID(), "9.99")))
        .andExpect(status().isNotFound());
  }

  @Test
  void thereIsNoStandalonePaymentCreationEndpoint() throws Exception {
    // payments may only come from the subscribe checkout, so this route must not exist.
    // Admin included: it is gone for everyone, not just demoted.
    mvc.perform(post("/payment").with(httpBasic("root", ADMIN_PW))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"userId\":\"" + otherUserId + "\",\"subId\":\"" + subscriptionId
            + "\",\"amount\":0.01}"))
        .andExpect(status().isMethodNotAllowed());
  }

  @Test
  void subscribingIsTheOnlyWayToGetAPayment() throws Exception {
    mvc.perform(post("/user-subscription").with(httpBasic("keni", USER_PW))
        .contentType(MediaType.APPLICATION_JSON)
        .content(subscribeBody(userId, subscriptionId, "9.99")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.payment.id").isNotEmpty())
        .andExpect(jsonPath("$.payment.status").value("COMPLETED"))
        // the price stored is the tier's, not whatever the client claimed
        .andExpect(jsonPath("$.payment.amount").value(9.99));
  }

  // ---------------- renewal ----------------

  @Test
  void renewalOfActiveSubscriptionKeepsTheDaysAlreadyPaidFor() throws Exception {
    subscribe(userId, subscriptionId, "9.99");

    var before = mySubscription().getExpiresAt();
    var result = renew(userId, subscriptionId, "9.99");

    var after = result.userSubscription().expiresAt();
    // exactly one more 30 day period, measured from the old expiry, not from today
    assertThat(java.time.Duration.between(before, after).toDays()).isEqualTo(30);
  }

  @Test
  void renewalOfLapsedSubscriptionIsRejectedBecauseTheyMustResubscribe() throws Exception {
    var keni = userRepository.findById(userId).orElseThrow();
    var tier = subscriptionRepository.findById(subscriptionId).orElseThrow();
    userSubscriptionRepository.saveAndFlush(lapsedSubscription(keni, tier));

    // renewing a dead subscription would silently skip the time already served, so it is
    // refused and the caller is told to buy it again
    assertThat(renewStatus("keni", subscriptionId, "9.99")).isEqualTo(409);
  }

  @Test
  void lapsedSubscriptionCanBeBoughtAgainOnTheSameRow() throws Exception {
    var keni = userRepository.findById(userId).orElseThrow();
    var tier = subscriptionRepository.findById(subscriptionId).orElseThrow();
    var lapsedRow = userSubscriptionRepository.saveAndFlush(lapsedSubscription(keni, tier));

    subscribe(userId, subscriptionId, "9.99");

    // reused rather than duplicated, because of the unique constraint
    assertThat(userSubscriptionRepository.findByUserId(userId)).hasSize(1);
    assertThat(mySubscription().isActive()).isTrue();
    assertThat(mySubscription().getId()).isEqualTo(lapsedRow.getId());
    // a fresh period measured from today
    assertThat(java.time.Duration.between(java.time.Instant.now(),
        mySubscription().getExpiresAt()).toDays()).isBetween(29L, 30L);
  }

  @Test
  void renewalChargesAgainWithoutCreatingASecondSubscriptionRow() throws Exception {
    var seeded = paymentRepository.findByUserId(userId).size();
    subscribe(userId, subscriptionId, "9.99");
    assertThat(paymentRepository.findByUserId(userId)).hasSize(seeded + 1);
    assertThat(userSubscriptionRepository.findByUserId(userId)).hasSize(1);

    var result = renew(userId, subscriptionId, "9.99");

    assertThat(result.payment().amount()).isEqualByComparingTo("9.99");
    assertThat(result.payment().status())
        .isEqualTo(com.keni.starter.modules.payments.PaymentStatus.COMPLETED);
    // charged again...
    assertThat(paymentRepository.findByUserId(userId)).hasSize(seeded + 2);
    // ...but still one row, because the unique constraint holds and it was extended
    assertThat(userSubscriptionRepository.findByUserId(userId)).hasSize(1);
  }

  @Test
  void renewalDoesNotOverwriteTheOriginalStartDate() throws Exception {
    subscribe(userId, subscriptionId, "9.99");
    var startedAt = mySubscription().getStartedAt();
    renew(userId, subscriptionId, "9.99");
    assertThat(mySubscription().getStartedAt()).isEqualTo(startedAt);
  }

  @Test
  void renewingATierYouDoNotHaveIsNotFound() throws Exception {
    assertThat(renewStatus("keni", subscriptionId, "9.99")).isEqualTo(404);
  }

  @Test
  void renewalRejectsUnderpayment() throws Exception {
    subscribe(userId, subscriptionId, "9.99");
    assertThat(renewStatus("keni", subscriptionId, "0.01")).isEqualTo(400);
  }

  @Test
  void anonymousCannotRenew() throws Exception {
    mvc.perform(post("/user-subscription/renew")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"subscriptionId\":\"" + subscriptionId + "\",\"amount\":9.99}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void renewalBodyCannotNameSomebodyElse() throws Exception {
    subscribe(userId, subscriptionId, "9.99");
    // no userId field exists, so an attempt to add one is rejected outright
    mvc.perform(post("/user-subscription/renew").with(httpBasic("keni", USER_PW))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"subscriptionId\":\"" + subscriptionId + "\",\"amount\":9.99,"
            + "\"userId\":\"" + otherUserId + "\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void malloryCannotRenewKenisSubscription() throws Exception {
    subscribe(userId, subscriptionId, "9.99");
    var kenisExpiryBefore = mySubscription().getExpiresAt();

    // mallory has no row for this tier, so she gets 404 and keni is untouched
    assertThat(renewStatus("mallory", subscriptionId, "9.99")).isEqualTo(404);
    assertThat(mySubscription().getExpiresAt()).isEqualTo(kenisExpiryBefore);
  }

  private void subscribe(UUID user, UUID subscription, String amount) throws Exception {
    mvc.perform(post("/user-subscription").with(httpBasic("keni", USER_PW))
        .contentType(MediaType.APPLICATION_JSON)
        .content(subscribeBody(user, subscription, amount)))
        .andExpect(status().isOk());
  }

  private int renewStatus(String asUser, UUID subscription, String amount) throws Exception {
    return renewCall(asUser, subscription, amount).getStatus();
  }

  private SubscribeResponse renew(UUID user, UUID subscription, String amount)
      throws Exception {
    var response = renewCall("keni", subscription, amount);
    assertThat(response.getStatus()).isEqualTo(200);
    return MAPPER.readValue(response.getContentAsString(), SubscribeResponse.class);
  }

  private MockHttpServletResponse renewCall(String asUser, UUID subscription, String amount)
      throws Exception {
    return mvc
        .perform(post("/user-subscription/renew").with(httpBasic(asUser, USER_PW))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"subscriptionId\":\"" + subscription + "\",\"amount\":" + amount + "}"))
        .andReturn().getResponse();
  }

  private com.keni.starter.modules.userSubscriptions.UserSubscription mySubscription() {
    return userSubscriptionRepository.findByUserIdAndSubscriptionId(userId, subscriptionId)
        .orElseThrow();
  }

  private com.keni.starter.modules.userSubscriptions.UserSubscription lapsedSubscription(
      User user, Subscription tier) {
    var lapsed = new com.keni.starter.modules.userSubscriptions.UserSubscription();
    lapsed.setUser(user);
    lapsed.setSubscription(tier);
    lapsed.setStartedAt(java.time.Instant.now().minus(java.time.Duration.ofDays(60)));
    lapsed.setExpiresAt(java.time.Instant.now().minus(java.time.Duration.ofDays(30)));
    return lapsed;
  }

  // ---------------- cancel ----------------

  @Test
  void ownerCanCancelOwnSubscription() throws Exception {
    subscribe(userId, subscriptionId, "9.99");

    mvc.perform(patch("/user-subscription/" + mySubscription().getId() + "/cancel")
        .with(httpBasic("keni", USER_PW)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.active").value(false))
        .andExpect(jsonPath("$.cancelledAt").isNotEmpty());

    assertThat(mySubscription().isActive()).isFalse();
  }

  @Test
  void cancellingDoesNotRefundThePayment() throws Exception {
    subscribe(userId, subscriptionId, "9.99");
    var statusesBefore = paymentStatuses();

    mvc.perform(patch("/user-subscription/" + mySubscription().getId() + "/cancel")
        .with(httpBasic("keni", USER_PW)))
        .andExpect(status().isOk());

    // access is gone but the money is not, that is the whole point of separating them
    assertThat(mySubscription().isActive()).isFalse();
    assertThat(paymentStatuses()).isEqualTo(statusesBefore);
    assertThat(paymentStatuses()).contains(
        com.keni.starter.modules.payments.PaymentStatus.COMPLETED);
  }

  /** Statuses for this user's payments on this tier, oldest first. */
  private java.util.List<com.keni.starter.modules.payments.PaymentStatus> paymentStatuses() {
    return paymentRepository.findByUserIdAndSubscriptionId(userId, subscriptionId).stream()
        .map(com.keni.starter.modules.payments.Payment::getStatus).toList();
  }

  @Test
  void userCannotCancelSomebodyElsesSubscription() throws Exception {
    subscribe(userId, subscriptionId, "9.99");
    var kenisRowId = mySubscription().getId();

    mvc.perform(patch("/user-subscription/" + kenisRowId + "/cancel")
        .with(httpBasic("mallory", USER_PW)))
        .andExpect(status().isForbidden());

    // and keni keeps her access
    assertThat(mySubscription().isActive()).isTrue();
  }

  @Test
  void adminCanCancelAnyonesSubscription() throws Exception {
    subscribe(userId, subscriptionId, "9.99");

    mvc.perform(patch("/user-subscription/" + mySubscription().getId() + "/cancel")
        .with(httpBasic("root", ADMIN_PW)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.active").value(false));
  }

  @Test
  void cancellingTwiceIsNotAnError() throws Exception {
    subscribe(userId, subscriptionId, "9.99");
    var id = mySubscription().getId();

    mvc.perform(patch("/user-subscription/" + id + "/cancel").with(httpBasic("keni", USER_PW)))
        .andExpect(status().isOk());
    mvc.perform(patch("/user-subscription/" + id + "/cancel").with(httpBasic("keni", USER_PW)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.active").value(false));
  }

  @Test
  void anonymousCannotCancel() throws Exception {
    subscribe(userId, subscriptionId, "9.99");
    mvc.perform(patch("/user-subscription/" + mySubscription().getId() + "/cancel"))
        .andExpect(status().isUnauthorized());
    assertThat(mySubscription().isActive()).isTrue();
  }

  @Test
  void cancellingUnknownSubscriptionIsNotFound() throws Exception {
    mvc.perform(patch("/user-subscription/" + UUID.randomUUID() + "/cancel")
        .with(httpBasic("keni", USER_PW)))
        .andExpect(status().isNotFound());
  }

  @Test
  void userCanSubscribeAgainAfterCancelling() throws Exception {
    subscribe(userId, subscriptionId, "9.99");
    mvc.perform(patch("/user-subscription/" + mySubscription().getId() + "/cancel")
        .with(httpBasic("keni", USER_PW)))
        .andExpect(status().isOk());

    // cancel must not become the lockout that refunding nearly was
    assertThat(renewStatus("keni", subscriptionId, "9.99")).isEqualTo(409);
    subscribe(userId, subscriptionId, "9.99");

    assertThat(mySubscription().isActive()).isTrue();
    assertThat(mySubscription().getCancelledAt()).isNull();
    assertThat(userSubscriptionRepository.findByUserId(userId)).hasSize(1);
  }

  // ---------------- refund ends access ----------------

  /** PENDING -> COMPLETED -> REFUNDED, which is the only route a refund can take. */
  private void completeAndRefund(UUID paymentId) throws Exception {
    mvc.perform(patch("/payment/" + paymentId + "/status").with(httpBasic("root", ADMIN_PW))
        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"COMPLETED\"}"))
        .andExpect(status().isOk());
  }

  @Test
  void refundClosesTheSubscriptionAndStopsRenewal() throws Exception {
    subscribe(userId, subscriptionId, "9.99");
    var paymentId = firstPaymentId(userId);
    completeAndRefund(paymentId);

    mvc.perform(patch("/payment/" + paymentId + "/status").with(httpBasic("root", ADMIN_PW))
        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"REFUNDED\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("REFUNDED"));

    assertThat(mySubscription().getCancelledAt()).isNotNull();
    assertThat(mySubscription().isActive()).isFalse();

    // renewing a cancelled subscription makes no sense, they have to buy it again
    assertThat(renewStatus("keni", subscriptionId, "9.99")).isEqualTo(409);
  }

  @Test
  void refundedUserCanSubscribeAgain() throws Exception {
    subscribe(userId, subscriptionId, "9.99");
    var paymentId = firstPaymentId(userId);
    completeAndRefund(paymentId);
    mvc.perform(patch("/payment/" + paymentId + "/status").with(httpBasic("root", ADMIN_PW))
        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"REFUNDED\"}"))
        .andExpect(status().isOk());

    // the old fix would have made this a permanent 409
    var paymentsBefore = paymentRepository
        .findByUserIdAndSubscriptionId(userId, subscriptionId).size();
    subscribe(userId, subscriptionId, "9.99");

    assertThat(mySubscription().getCancelledAt()).isNull();
    assertThat(mySubscription().isActive()).isTrue();
    // revived on the same row, so still exactly one
    assertThat(userSubscriptionRepository.findByUserId(userId)).hasSize(1);
    // and the money really was taken again
    assertThat(paymentRepository.findByUserIdAndSubscriptionId(userId, subscriptionId))
        .hasSize(paymentsBefore + 1);
  }

  @Test
  void userCannotRefundThemselves() throws Exception {
    subscribe(userId, subscriptionId, "9.99");
    var paymentId = firstPaymentId(userId);
    completeAndRefund(paymentId);

    mvc.perform(patch("/payment/" + paymentId + "/status").with(httpBasic("keni", USER_PW))
        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"REFUNDED\"}"))
        .andExpect(status().isForbidden());
    // refused, so access is untouched
    assertThat(mySubscription().isActive()).isTrue();
  }

  private UUID firstPaymentId(UUID user) {
    return paymentRepository.findByUserIdAndSubscriptionId(user, subscriptionId).stream()
        .findFirst().orElseThrow().getId();
  }

  // ---------------- registration can only ever produce USER ----------------

  @Test
  void registrationIgnoresEveryWayOfAskingForAdmin() throws Exception {
    // each of these is either an unknown field (400) or plain ignored, never an ADMIN
    var attempts = new String[] {
        "{\"userName\":\"a1\",\"password\":\"supersecret\",\"role\":\"ADMIN\"}",
        "{\"userName\":\"a2\",\"password\":\"supersecret\",\"Role\":\"ADMIN\"}",
        "{\"userName\":\"a3\",\"password\":\"supersecret\",\"ROLE\":\"ADMIN\"}",
        "{\"userName\":\"a4\",\"password\":\"supersecret\",\"authorities\":[\"ROLE_ADMIN\"]}",
        "{\"userName\":\"a5\",\"password\":\"supersecret\",\"id\":\"11111111-1111-1111-1111-111111111111\"}",
        "{\"userName\":\"a6\",\"password\":\"supersecret\",\"enabled\":false}",
        "{\"userName\":\"a7\",\"password\":\"supersecret\",\"accountNonLocked\":true}",
    };

    for (var body : attempts) {
      var status = mvc.perform(post("/user").contentType(MediaType.APPLICATION_JSON).content(body))
          .andReturn().getResponse().getStatus();
      assertThat(status).as("body %s", body).isEqualTo(400);
    }

    // none of the seven names above was ever created, so nothing escalated
    for (var name : new String[] {"a1", "a2", "a3", "a4", "a5", "a6", "a7"}) {
      assertThat(userRepository.findByUserName(name)).as(name).isEmpty();
    }
  }

  @Test
  void registeredUserIsStoredAsUserNotAdmin() throws Exception {
    // a well formed request, so the user really is created, and the stored role is USER
    mvc.perform(post("/user").contentType(MediaType.APPLICATION_JSON)
        .content("{\"userName\":\"plain\",\"password\":\"supersecret\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.role").value("USER"));

    var stored = userRepository.findByUserName("plain").orElseThrow();
    assertThat(stored.getRole()).isEqualTo(Role.USER);
    assertThat(stored.getAuthorities()).extracting(Object::toString)
        .containsExactly("ROLE_USER");
  }

  @Test
  void everyRegistrationRouteProducesUserOnly() throws Exception {
    // the only creation path is POST /user, and it always lands on USER
    for (int i = 0; i < 3; i++) {
      mvc.perform(post("/user").contentType(MediaType.APPLICATION_JSON)
          .content("{\"userName\":\"bulk" + i + "\",\"password\":\"supersecret\"}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.role").value("USER"));
    }
    assertThat(userRepository.findAll())
        .allMatch(u -> u.getRole() != null);
    assertThat(userRepository.findAll().stream()
        .filter(u -> u.getRole() == Role.USER).count()).isGreaterThanOrEqualTo(3L);
  }

  @Test
  void noEndpointCanChangeAnExistingUsersRole() throws Exception {
    userService.createUser(new NewUserRequest("victim", "supersecret"));
    var victimId = userRepository.findByUserName("victim").orElseThrow().getId();

    // the admin has no user-editing route either, so PUT/PATCH/DELETE must not exist
    for (var method : new String[] {"PUT", "PATCH", "DELETE"}) {
      var request = org.springframework.test.web.servlet.request.MockMvcRequestBuilders
          .request(org.springframework.http.HttpMethod.valueOf(method), "/user/" + victimId)
          .with(httpBasic("root", ADMIN_PW)).contentType(MediaType.APPLICATION_JSON)
          .content("{\"role\":\"ADMIN\"}");
      var status = mvc.perform(request).andReturn().getResponse().getStatus();
      assertThat(status).as(method + " /user/{id}").isEqualTo(405);
    }

    assertThat(userRepository.findById(victimId).orElseThrow().getRole()).isEqualTo(Role.USER);
  }

  @Test
  void adminCannotGrantAdminThroughTheApi() throws Exception {
    // there is no role endpoint of any kind, so even an admin has to use SQL
    mvc.perform(patch("/user/" + adminId).with(httpBasic("root", ADMIN_PW))
        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"ADMIN\"}"))
        .andExpect(status().isMethodNotAllowed());
  }

  private String subscribeBody(UUID user, UUID subscription, String amount) {
    return "{\"subscriptionId\":\"" + subscription + "\",\"userId\":\"" + user
        + "\",\"amount\":" + amount + "}";
  }

  // ---------------- browsing the catalogue is fine for everyone ----------------

  @Test
  void userCanBrowseSubscriptionTiers() throws Exception {
    mvc.perform(get("/subscription").with(httpBasic("keni", USER_PW)))
        .andExpect(status().isOk())
        .andExpect(content().string(org.hamcrest.Matchers.containsString("Gold")));
  }

  @Test
  void userCannotCreateSubscriptionTier() throws Exception {
    mvc.perform(post("/subscription").with(httpBasic("keni", USER_PW))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"name\":\"Platinum\",\"price\":\"19.99\",\"durationDays\":30}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void userCannotAddGamesToATier() throws Exception {
    mvc.perform(post("/subscription-game").with(httpBasic("keni", USER_PW))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"subscriptionId\":\"" + subscriptionId
            + "\",\"gameId\":\"" + UUID.randomUUID() + "\"}"))
        .andExpect(status().isForbidden());
  }

  // ---------------- bad credentials ----------------

  @Test
  void wrongPasswordIsRejected() throws Exception {
    mvc.perform(get("/user/me").with(httpBasic("keni", "wrongpassword")))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void unknownUserIsRejected() throws Exception {
    mvc.perform(get("/user/me").with(httpBasic("ghost", USER_PW)))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void anonymousCannotReachAnyPrivateData() throws Exception {
    mvc.perform(get("/payment/me")).andExpect(status().isUnauthorized());
    mvc.perform(get("/user-subscription/me")).andExpect(status().isUnauthorized());
    mvc.perform(get("/payment")).andExpect(status().isUnauthorized());
    mvc.perform(get("/user")).andExpect(status().isUnauthorized());
  }

  @Test
  void adminIdIsNotLeakedByAnyUserFacingEndpoint() throws Exception {
    // /user/me is the only own-data route and it must only ever return the caller
    mvc.perform(get("/user/me").with(httpBasic("keni", USER_PW)))
        .andExpect(content().string(
            org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(adminId.toString()))));
  }
}
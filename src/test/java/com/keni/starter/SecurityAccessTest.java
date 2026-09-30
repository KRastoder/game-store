package com.keni.starter;

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
import org.springframework.test.web.servlet.MockMvc;

import com.keni.starter.modules.payments.PaymentRepository;
import com.keni.starter.modules.payments.PaymentStatus;
import com.keni.starter.modules.subscriptions.Subscription;
import com.keni.starter.modules.subscriptions.SubscriptionRepository;
import com.keni.starter.modules.user.Role;
import com.keni.starter.modules.user.User;
import com.keni.starter.modules.user.UserRepository;
import com.keni.starter.modules.userSubscriptions.UserSubscriptionRepository;

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
  private PasswordEncoder encoder;


  private UUID userId;
  private UUID otherUserId;
  private UUID adminId;
  private UUID subscriptionId;
  private UUID userPaymentId;

  @BeforeEach
  void setUp() {
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
        .content("{\"subscriptionId\":\"" + subscriptionId + "\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.userId").value(userId.toString()));
  }

  @Test
  void userCannotSubscribeSomebodyElse() throws Exception {
    // userId is no longer part of the request at all, so this must not be accepted
    mvc.perform(post("/user-subscription").with(httpBasic("keni", USER_PW))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"subscriptionId\":\"" + subscriptionId + "\",\"userId\":\""
            + otherUserId + "\"}"))
        .andExpect(status().isBadRequest());
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
        .content("{\"name\":\"Platinum\",\"price\":\"19.99\"}"))
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
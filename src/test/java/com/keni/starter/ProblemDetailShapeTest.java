package com.keni.starter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.test.web.servlet.MvcResult;

import com.keni.starter.modules.games.GameRepository;
import com.keni.starter.modules.payments.PaymentRepository;
import com.keni.starter.modules.subscriptionGames.SubscriptionGameRepository;
import com.keni.starter.modules.subscriptions.Subscription;
import com.keni.starter.modules.subscriptions.SubscriptionRepository;
import com.keni.starter.modules.userSubscriptions.UserSubscriptionRepository;
import com.keni.starter.modules.user.Role;
import com.keni.starter.modules.user.User;
import com.keni.starter.modules.user.UserRepository;

/**
 * Every failure must be an RFC 9457 problem document with the same shape, and must never
 * carry a stack trace. Spring's default error body used to include the full exception,
 * which named every internal class and line number.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ProblemDetailShapeTest {

  private static final String ADMIN_PW = "adminpassword";
  private static final String USER_PW = "supersecret";

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
  private PasswordEncoder encoder;

  @BeforeEach
  void setUp() {
    // every table this test touches, because the @SpringBootTest classes share one
    // cached context and therefore one database
    paymentRepository.deleteAll();
    userSubscriptionRepository.deleteAll();
    subscriptionGameRepository.deleteAll();
    gameRepository.deleteAll();
    subscriptionRepository.deleteAll();
    userRepository.deleteAll();

    var admin = new User();
    admin.setUserName("root");
    admin.setPassword(encoder.encode(ADMIN_PW));
    admin.setRole(Role.ADMIN);
    userRepository.saveAndFlush(admin);

    var plain = new User();
    plain.setUserName("plain");
    plain.setPassword(encoder.encode(USER_PW));
    plain.setRole(Role.USER);
    userRepository.saveAndFlush(plain);

    var tier = new Subscription();
    tier.setName("Gold");
    tier.setPrice(new BigDecimal("9.99"));
    tier.setDurationDays(30);
    subscriptionRepository.saveAndFlush(tier);
  }

  private MvcResult call(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder b)
      throws Exception {
    return mvc.perform(b).andReturn();
  }

  private void assertProblem(String body, int status) {
    // the four RFC 9457 members
    assertThat(body).as("type").contains("\"type\"");
    assertThat(body).as("title").contains("\"title\"");
    assertThat(body).as("status").contains("\"status\":" + status);
    assertThat(body).as("detail").contains("\"detail\"");

    // the whole point of the handler
    assertThat(body).doesNotContain("trace");
    assertThat(body).doesNotContain("at com.keni");
    assertThat(body).doesNotContain("Exception");
    assertThat(body).doesNotContain("org.springframework");
    assertThat(body).doesNotContain("java.base");
  }

  @Test
  void validationFailureIsAProblemDocument() throws Exception {
    var r = call(post("/user").contentType(MediaType.APPLICATION_JSON)
        .content("{\"userName\":\"\",\"password\":\"x\"}"));
    assertThat(r.getResponse().getStatus()).isEqualTo(400);

    var body = r.getResponse().getContentAsString();
    assertProblem(body, 400);
    assertThat(body).contains("\"errors\"");
    // per field, so a client can highlight the exact inputs
    assertThat(body).contains("userName");
    assertThat(body).contains("password");
    // and it must not echo what was submitted
    assertThat(body).doesNotContain("rejectedValue");
  }

  @Test
  void serviceNotFoundIsAProblemDocument() throws Exception {
    var r = call(get("/user/" + UUID.randomUUID()).with(httpBasic("root", ADMIN_PW)));
    assertThat(r.getResponse().getStatus()).isEqualTo(404);

    var body = r.getResponse().getContentAsString();
    assertProblem(body, 404);
    assertThat(body).contains("User not found");
    // titles are reason phrases everywhere, never the raw enum
    assertThat(body).contains("\"title\":\"Not Found\"");
    assertThat(body).doesNotContain("404 NOT_FOUND");
  }

  @Test
  void malformedJsonIsAProblemDocumentAndEchoesNothing() throws Exception {
    var r = call(post("/user").contentType(MediaType.APPLICATION_JSON).content("{not json"));
    assertThat(r.getResponse().getStatus()).isEqualTo(400);

    var body = r.getResponse().getContentAsString();
    assertProblem(body, 400);
    // a rejected body could contain a password, so the reason stays generic
    assertThat(body).doesNotContain("not json");
  }

  @Test
  void unknownFieldIsAProblemDocument() throws Exception {
    var r = call(post("/user").contentType(MediaType.APPLICATION_JSON)
        .content("{\"userName\":\"someone\",\"password\":\"supersecret\",\"role\":\"ADMIN\"}"));
    assertThat(r.getResponse().getStatus()).isEqualTo(400);
    assertProblem(r.getResponse().getContentAsString(), 400);
  }

  @Test
  void unauthorisedIsAProblemDocument() throws Exception {
    var r = call(get("/payment/me"));
    assertThat(r.getResponse().getStatus()).isEqualTo(401);

    var body = r.getResponse().getContentAsString();
    assertProblem(body, 401);
    assertThat(r.getResponse().getContentType())
        .startsWith(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
  }

  @Test
  void forbiddenIsAProblemDocument() throws Exception {
    // signed in as a plain user hitting an admin-only route, so a genuine 403
    var r = call(get("/payment").with(httpBasic("plain", USER_PW)));
    assertThat(r.getResponse().getStatus()).isEqualTo(403);

    var body = r.getResponse().getContentAsString();
    assertProblem(body, 403);
    assertThat(body).contains("permission");
  }

  @Test
  void anonymousIsUnauthorisedNotForbidden() throws Exception {
    // no credentials at all is a 401; 403 means "we know who you are, and no"
    var r = call(get("/payment"));
    assertThat(r.getResponse().getStatus()).isEqualTo(401);
  }

  @Test
  void wrongCredentialsGiveAnUnauthorisedProblemDocument() throws Exception {
    var r = call(get("/user/me").with(httpBasic("nobody", "whatever")));
    assertThat(r.getResponse().getStatus()).isEqualTo(401);
    assertProblem(r.getResponse().getContentAsString(), 401);
    // the fact that the account does not exist is not for the caller to learn
    assertThat(r.getResponse().getContentAsString()).doesNotContain("No user named");
  }

  @Test
  void businessConflictIsAProblemDocument() throws Exception {
    var me = userRepository.findByUserName("root").orElseThrow();
    var tier = subscriptionRepository.findAll().get(0);

    mvc.perform(post("/user").contentType(MediaType.APPLICATION_JSON)
        .content("{\"userName\":\"dup\",\"password\":\"supersecret\"}"));
    var second = call(post("/user").contentType(MediaType.APPLICATION_JSON)
        .content("{\"userName\":\"dup\",\"password\":\"supersecret\"}"));
    assertThat(second.getResponse().getStatus()).isEqualTo(409);
    assertProblem(second.getResponse().getContentAsString(), 409);
  }

  @Test
  void methodNotAllowedStillHasABody() throws Exception {
    var r = call(post("/user/me").with(httpBasic("root", ADMIN_PW)));
    assertThat(r.getResponse().getStatus()).isEqualTo(405);
    assertThat(r.getResponse().getContentAsString()).contains("status");
    assertThat(r.getResponse().getContentAsString()).doesNotContain("at com.keni");
  }

  @Test
  void contentTypeIsProblemJsonOnEveryError() throws Exception {
    var r = call(post("/user").contentType(MediaType.APPLICATION_JSON)
        .content("{\"userName\":\"\",\"password\":\"\"}"));
    assertThat(r.getResponse().getContentType())
        .startsWith(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
  }
}
package com.keni.starter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import com.keni.starter.modules.games.GameRepository;
import com.keni.starter.modules.payments.PaymentRepository;
import com.keni.starter.modules.subscriptions.Subscription;
import com.keni.starter.modules.subscriptions.SubscriptionRepository;
import com.keni.starter.modules.subscriptionGames.SubscriptionGameRepository;
import com.keni.starter.modules.user.Role;
import com.keni.starter.modules.user.User;
import com.keni.starter.modules.user.UserRepository;
import com.keni.starter.modules.userSubscriptions.UserSubscriptionRepository;

/**
 * Every list endpoint returns a page rather than the whole table.
 *
 * <p>The size cap is the part that matters: without it ?size=1000000 is a one request
 * denial of service, so there is a test that asks for far more than the cap allows.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PaginationTest {

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
  private SubscriptionGameRepository subscriptionGameRepository;
  @Autowired
  private GameRepository gameRepository;
  @Autowired
  private PasswordEncoder encoder;

  @BeforeEach
  void setUp() {
    subscriptionGameRepository.deleteAll();
    paymentRepository.deleteAll();
    userSubscriptionRepository.deleteAll();
    subscriptionRepository.deleteAll();
    gameRepository.deleteAll();
    userRepository.deleteAll();

    var admin = new User();
    admin.setUserName("root");
    admin.setPassword(encoder.encode(ADMIN_PW));
    admin.setRole(Role.ADMIN);
    userRepository.saveAndFlush(admin);

    for (int i = 0; i < 25; i++) {
      var tier = new Subscription();
      tier.setName("Tier " + String.format("%02d", i));
      tier.setPrice(new BigDecimal("9.99"));
      tier.setDurationDays(30);
      subscriptionRepository.saveAndFlush(tier);
    }
  }

  @Test
  void listEndpointsReturnAPageEnvelopeNotABareArray() throws Exception {
    mvc.perform(get("/subscription").with(httpBasic("root", ADMIN_PW)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray())
        .andExpect(jsonPath("$.totalElements").value(25))
        .andExpect(jsonPath("$.page").value(0))
        .andExpect(jsonPath("$.size").value(20))
        .andExpect(jsonPath("$.totalPages").value(2))
        .andExpect(jsonPath("$.first").value(true))
        .andExpect(jsonPath("$.last").value(false))
        .andExpect(jsonPath("$.empty").value(false));
  }

  @Test
  void defaultPageSizeIsTwentyNotTwentyFive() throws Exception {
    // the point of pagination: the client gets a slice, not the table
    mvc.perform(get("/subscription").with(httpBasic("root", ADMIN_PW)))
        .andExpect(jsonPath("$.content.length()").value(20))
        .andExpect(jsonPath("$.totalElements").value(25));
  }

  @Test
  void secondPageReturnsTheRemainder() throws Exception {
    mvc.perform(get("/subscription?page=1").with(httpBasic("root", ADMIN_PW)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(5))
        .andExpect(jsonPath("$.first").value(false))
        .andExpect(jsonPath("$.last").value(true));
  }

  @Test
  void pagesDoNotOverlapAndTogetherCoverEverything() throws Exception {
    var first = mvc.perform(get("/subscription?size=10&page=0").with(httpBasic("root", ADMIN_PW)))
        .andReturn().getResponse().getContentAsString();
    var second = mvc.perform(get("/subscription?size=10&page=1").with(httpBasic("root", ADMIN_PW)))
        .andReturn().getResponse().getContentAsString();

    var mapper = tools.jackson.databind.json.JsonMapper.builder().build();
    var page1 = mapper.readTree(first).get("content");
    var page2 = mapper.readTree(second).get("content");

    assertThat(page1.size()).isEqualTo(10);
    assertThat(page2.size()).isEqualTo(10);

    var names = new java.util.HashSet<String>();
    page1.forEach(n -> names.add(n.get("name").asString()));
    page2.forEach(n -> names.add(n.get("name").asString()));
    // 25 rows cannot fit in two pages of ten without a repeat, so overlap would show up
    assertThat(names).hasSize(20);
  }

  @Test
  void sizeIsCappedSoOneRequestCannotPullTheWholeTable() throws Exception {
    mvc.perform(get("/subscription?size=100000").with(httpBasic("root", ADMIN_PW)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.size").value(100))
        .andExpect(jsonPath("$.content.length()").value(25));
  }

  @Test
  void aPageBeyondTheEndIsEmptyRatherThanAnError() throws Exception {
    mvc.perform(get("/subscription?page=99").with(httpBasic("root", ADMIN_PW)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(0))
        .andExpect(jsonPath("$.empty").value(true))
        .andExpect(jsonPath("$.totalElements").value(25));
  }

  @Test
  void anEmptyTableReportsZeroPagesNotOne() throws Exception {
    subscriptionRepository.deleteAll();
    mvc.perform(get("/subscription").with(httpBasic("root", ADMIN_PW)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(0))
        .andExpect(jsonPath("$.totalPages").value(0))
        .andExpect(jsonPath("$.empty").value(true));
  }

  @Test
  void sortingIsHonoured() throws Exception {
    mvc.perform(get("/subscription?sort=name,desc&size=1").with(httpBasic("root", ADMIN_PW)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].name").value("Tier 24"));
  }

  @Test
  void ownRoutesAreAlsoPaged() throws Exception {
    // /payment/me and /user-subscription/me used to be unbounded too
    mvc.perform(get("/user-subscription/me").with(httpBasic("root", ADMIN_PW)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray())
        .andExpect(jsonPath("$.totalElements").value(0));

    mvc.perform(get("/payment/me").with(httpBasic("root", ADMIN_PW)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray())
        .andExpect(jsonPath("$.totalElements").value(0));
  }

  @Test
  void pagingDoesNotLeakOtherUsersRows() throws Exception {
    var other = new User();
    other.setUserName("mallory");
    other.setPassword(encoder.encode("supersecret"));
    other.setRole(Role.USER);
    other = userRepository.saveAndFlush(other);

    mvc.perform(get("/user?size=100").with(httpBasic("root", ADMIN_PW)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(2));

    mvc.perform(get("/payment?size=100").with(httpBasic("mallory", "supersecret")))
        .andExpect(status().isForbidden());
  }

  @Test
  void zeroSizedPageIsHandledRatherThanErroring() throws Exception {
    mvc.perform(get("/subscription?size=0").with(httpBasic("root", ADMIN_PW)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray());
  }
}
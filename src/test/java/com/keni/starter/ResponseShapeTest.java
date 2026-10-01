package com.keni.starter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.ClassUtils;
import org.springframework.web.bind.annotation.RestController;

import com.keni.starter.modules.games.GameRepository;
import com.keni.starter.modules.payments.PaymentRepository;
import com.keni.starter.modules.subscriptions.SubscriptionRepository;
import com.keni.starter.modules.subscriptionGames.SubscriptionGameRepository;
import com.keni.starter.modules.user.Role;
import com.keni.starter.modules.user.User;
import com.keni.starter.modules.user.UserRepository;
import com.keni.starter.modules.userSubscriptions.UserSubscriptionRepository;

import jakarta.persistence.Entity;

/**
 * Every endpoint returns a DTO, never a JPA entity.
 *
 * <p>Serialising an entity ties the API to the table: adding a column changes the
 * response without anyone touching a controller, and a field that should never be public
 * goes public by default. POST /game used to return the Game entity while every other
 * endpoint returned a record.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ResponseShapeTest {

  private static final String ADMIN_PW = "adminpassword";

  @Autowired
  MockMvc mvc;
  @Autowired
  private org.springframework.context.ApplicationContext context;
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
  }

  @Test
  void noControllerMethodReturnsAJpaEntity() {
    var offenders = new ArrayList<String>();

    for (var name : context.getBeanNamesForAnnotation(RestController.class)) {
      var bean = context.getBean(name);
      // the bean may be a CGLIB proxy, whose class hides the real annotations
      var target = ClassUtils.getUserClass(bean);

      for (Method method : target.getMethods()) {
        if (!isEndpoint(method)) {
          continue;
        }
        var returnType = method.getReturnType();
        if (returnType.isAnnotationPresent(Entity.class)) {
          offenders.add(target.getSimpleName() + "." + method.getName() + " -> "
              + returnType.getSimpleName());
        }
      }
    }

    assertThat(offenders)
        .as("these endpoints return a JPA entity instead of a DTO")
        .isEmpty();
  }

  /** Any method carrying a request mapping annotation is an endpoint. */
  private static boolean isEndpoint(Method method) {
    return MAPPING_ANNOTATIONS.stream().anyMatch(method::isAnnotationPresent);
  }

  private static final List<Class<? extends java.lang.annotation.Annotation>>
      MAPPING_ANNOTATIONS = List.of(
          org.springframework.web.bind.annotation.PostMapping.class,
          org.springframework.web.bind.annotation.GetMapping.class,
          org.springframework.web.bind.annotation.PutMapping.class,
          org.springframework.web.bind.annotation.PatchMapping.class,
          org.springframework.web.bind.annotation.DeleteMapping.class,
          org.springframework.web.bind.annotation.RequestMapping.class);

  @Test
  void createGameReturnsExactlyTheDtoFields() throws Exception {
    var response = mvc.perform(post("/game").with(httpBasic("root", ADMIN_PW))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"title\":\"Minecraft\",\"description\":\"blocks\",\"company\":\"Mojang\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").isNotEmpty())
        .andExpect(jsonPath("$.title").value("Minecraft"))
        .andExpect(jsonPath("$.description").value("blocks"))
        .andExpect(jsonPath("$.company").value("Mojang"))
        .andReturn().getResponse().getContentAsString();

    var node = tools.jackson.databind.json.JsonMapper.builder().build().readTree(response);
    var fields = new ArrayList<String>();
    for (var name : node.propertyNames()) {
      fields.add(name);
    }

    // exactly four, no more. an entity would leak any column added later
    assertThat(fields).containsExactlyInAnyOrder("id", "title", "description", "company");
  }

  @Test
  void everyResponseIsAJsonObjectNotAnEntityDump() throws Exception {
    var response = mvc.perform(post("/game").with(httpBasic("root", ADMIN_PW))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"title\":\"Tetris\",\"company\":\"Someone\",\"description\":null}"))
        .andExpect(status().isOk())
        .andReturn().getResponse();

    assertThat(response.getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
    var body = response.getContentAsString();
    // a Hibernate serialised entity would be a deep nested graph keyed by field name
    assertThat(body).doesNotContain("\"hibernate");
    assertThat(body).startsWith("{");
    assertThat(body).endsWith("}");
  }
}
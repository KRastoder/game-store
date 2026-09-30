# Game Renting Store

A Spring Boot backend for a subscription video-game rental service. Users sign up,
pick a subscription tier, and that tier grants access to a catalogue of games.
Payments are recorded against the user's subscription.

Built as a learning project with the goal of getting closer to production-shaped
Spring code rather than tutorial-shaped Spring code.

## Features

- Users register with a BCrypt-hashed password and can never read it back over the API
- Subscription tiers with a price
- Subscription-to-game catalogue (many-to-many join entity)
- User-to-subscription subscriptions with start and expiry timestamps
- Payments recorded per user + subscription, with a status lifecycle
- Request validation that returns `400` with the offending field, not a `500`

## Tech stack

| | |
|---|---|
| Java | 21 |
| Spring Boot | 4.1.1 |
| Spring Web MVC | REST controllers |
| Jackson 3 | `tools.jackson`, the Jackson 2 successor Spring Boot 4 ships |
| Spring Data JPA | Hibernate 7 |
| Spring Security | BCrypt, config-ready for JWT later |
| Bean Validation | `jakarta.validation` on every request DTO |
| Postgres | 17 |
| Docker | multi-stage build, Compose for the whole stack |
| Lombok | boilerplate reduction |

---

## Quick start

Requires Docker Desktop or Docker Engine + Compose.

```bash
docker compose up --build
```

That starts Postgres and the app. First boot waits for the database to report
healthy, then Hibernate creates the tables.

- API: http://localhost:8080
- Postgres: `localhost:5432`, db `game_renting`, user `keni`, password `keni`

Stop it, keeping your data:

```bash
docker compose down
```

Stop it and **delete the database volume**:

```bash
docker compose down -v
```

## Running without Docker

You need your own Postgres running and a `game_renting` database:

```bash
createdb game_renting
./mvnw spring-boot:run
```

Or run it from your IDE — start `StarterApplication`. Connection settings live in
`src/main/resources/application.properties`.

## Configuration

| Property | Default | Notes |
|---|---|---|
| `spring.datasource.url` | `jdbc:postgresql://localhost:5432/game_renting` | Compose overrides to `db:5432` |
| `spring.datasource.username` | `keni` | |
| `spring.datasource.password` | `keni` | Override via env in any real deployment |
| `spring.jpa.hibernate.ddl-auto` | `update` | See below |
| `spring.jpa.open-in-view` | `false` | Stops Hibernate leaking lazy loads into the view layer |

> **`ddl-auto=update` is a learning-project setting.** It lets Hibernate create tables
> so you can skip migrations while you build. The moment you care about real data, switch
> to `validate` and manage the schema with [Flyway](https://flywaydb.org/) —
> `update` will happily drop columns to "fix" them and take your data with it.

---

## API

All request bodies are JSON and validated. Unknown fields are rejected with a `400`,
so a client typo like `"userNam"` fails loudly instead of being silently dropped.

### Users

| Method | Path | Body | Returns |
|---|---|---|---|
| `POST` | `/user` | `{userName, password}` | `UserResponse` |
| `GET` | `/user` | | `List<UserResponse>` |
| `GET` | `/user/{id}` | | `UserResponse` |

### Subscriptions

| Method | Path | Body | Returns |
|---|---|---|---|
| `POST` | `/subscription` | `{name, price}` | `SubscriptionResponse` |
| `GET` | `/subscription` | | `List<SubscriptionResponse>` |
| `GET` | `/subscription/{id}` | | `SubscriptionResponse` |

### Games

| Method | Path | Body | Returns |
|---|---|---|---|
| `POST` | `/game` | `{title, description, company}` | `Game` |

### Subscribing a user

| Method | Path | Body | Returns |
|---|---|---|---|
| `POST` | `/user-subscription` | `{userId, subscriptionId}` | `UserSubscriptionResponse` |
| `GET` | `/user-subscription/user/{userId}` | | `List<UserSubscriptionResponse>` |
| `GET` | `/user-subscription/subscription/{subscriptionId}` | | `List<UserSubscriptionResponse>` |

### Adding games to a subscription

| Method | Path | Body | Returns |
|---|---|---|---|
| `POST` | `/subscription-game` | `{subscriptionId, gameId}` | `SubscriptionGameResponse` |
| `GET` | `/subscription-game/subscription/{subscriptionId}` | | `List<SubscriptionGameResponse>` |
| `GET` | `/subscription-game/game/{gameId}` | | `List<SubscriptionGameResponse>` |

### Payments

| Method | Path | Body | Returns |
|---|---|---|---|
| `POST` | `/payment` | `{userId, subId, datePaid, amount}` | `PaymentResponse` |
| `PATCH` | `/payment/{id}/status` | `{status}` | `PaymentResponse` |
| `GET` | `/payment/user/{userId}` | | `List<PaymentResponse>` |

`status` is one of `PENDING`, `COMPLETED`, `FAILED`, `REFUNDED`. New payments are
always created as `PENDING` — the client cannot set it, otherwise anyone could mark
their own payment as completed without paying. `datePaid` is optional and defaults
to now.

```bash
curl -X POST localhost:8080/user \
  -H 'Content-Type: application/json' \
  -d '{"userName":"keni","password":"supersecret"}'
```

---

## Project layout

```
src/main/java/com/keni/starter/
├── config/          SecurityConfig
└── modules/
    ├── user/            entity, repository, service, controller, dtos/
    ├── subscriptions/   entity, repository, service, controller, dtos/
    ├── games/           entity, repository, service, controller, dtos/
    ├── userSubscriptions/      join entity + service + controller
    ├── subscriptionGames/      join entity + service + controller
    └── payments/         entity, repository, service, controller, dtos/
```

Each module owns its own DTOs and is independent of the others except through
foreign keys.

## Design decisions worth explaining

These are the things that come up in interviews, and the reasoning behind them:

**Entities are never returned from controllers.** Returning a JPA entity as a
response couples your API to your schema, and serializes every field including
sensitive ones. Every endpoint returns a record DTO instead. `UserResponse`
deliberately has no password field, because `User` implements `UserDetails` and
returning it would leak the hash into the JSON.

**Junction tables are entities, not just IDs.** `UserSubscription` and
`SubscriptionGame` carry their own surrogate UUID primary key plus a unique
constraint on the pair, rather than a composite key. That lets the join row
carry its own data later — `UserSubscription` already holds `startedAt` and
`expiresAt`.

**Uniqueness is enforced in the database, not just the service.** `UserService`
checks `existsByUserName` and returns `409`, but that check races under
concurrent requests. The unique constraint on the column is what actually
guarantees it; the service check just produces a nicer error message.

**Money is `BigDecimal`.** `Subscription.price` and `Payment.amount` are both
`BigDecimal` because `double` loses precision on decimal values and `int` cannot
represent cents.

**`open-in-view=false`.** By default Hibernate holds the persistence context open
for the whole request so lazy loading "just works". That hides N+1 query bugs
until they bite you in production, so it is turned off and services map to DTOs
inside the transaction instead.

**Unknown JSON fields are rejected.** Jackson 3 defaults to silently ignoring
properties it does not recognise, which means a client sending `"userNam"`
instead of `"userName"` gets a success response and a silently broken account.
`spring.jackson.deserialization.fail-on-unknown-properties=true` turns that into
a `400`.

**Passwords are BCrypt-hashed at rest.** `SecurityConfig` exposes a
`PasswordEncoder` bean; `UserService` encodes on the way in and the hash is never
readable through the API.

## Not done yet

Honest list of what is missing, roughly in priority order:

- **No tests.** The single biggest gap. Worth `@DataJpaTest` for repositories and
  `@WebMvcTest` for controllers.
- **Authentication is disabled.** `SecurityConfig` permits all requests; auth is
  a deliberate later step (JWT), not an oversight.
- **`ddl-auto=update` instead of Flyway migrations.**
- **No pagination.** Every list endpoint returns everything.
- **No global exception handler.** Services throw `ResponseStatusException`, which
  works, but a `@RestControllerAdvice` returning `ProblemDetail` would be better.
- **Inconsistent response shape.** `POST /game` returns the entity while
  everything else returns a DTO.
- **Payment status has no transition rules.** A payment can go straight from
  `PENDING` to `REFUNDED`.
- **No CI.** A GitHub Actions workflow running the build on every push.

## Troubleshooting

**`permission denied while trying to connect to the Docker API socket`**
Your user is not in the `docker` group:

```bash
sudo usermod -aG docker $USER
newgrp docker        # or log out and back in
```

**App exits immediately, `Failed to determine a suitable driver class`**
`spring.datasource.url` is missing or malformed.

**App exits, `Connection refused`**
Postgres is not up yet or the port/credentials are wrong. With Compose, check
`docker compose logs db`.

**Port 5432 or 8080 already in use**
Change the host side of the mapping in `docker-compose.yml`, e.g. `"5433:5432"`.

**Forgot a column when changing an entity**
`ddl-auto=update` adds columns but will not remove them. If you want a clean
schema during development: `docker compose down -v && docker compose up --build`.

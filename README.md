# Game Renting Store

A Spring Boot backend for a subscription video-game rental service. Users sign up,
pick a subscription tier, and that tier grants access to a catalogue of games.
Payments are recorded against the user's subscription.

Built as a learning project with the goal of getting closer to production-shaped
Spring code rather than tutorial-shaped Spring code.

## Features

- Users register with a BCrypt-hashed password and can never read it back over the API
- Two roles, `ADMIN` and `USER`, with HTTP Basic authentication
- Only admins create games, subscription tiers, catalogue entries, payments and see
  who bought what
- Users see their own payments and subscriptions and nobody else's
- Subscription-to-game catalogue (many-to-many join entity)
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
| Spring Security | HTTP Basic, BCrypt, role-based access |
| Bean Validation | `jakarta.validation` on every request DTO |
| Postgres | 17 |
| H2 | test scope only, for the security tests |
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

## Authentication and roles

Authentication is HTTP Basic — send `Authorization: Basic base64(user:password)`.
There are exactly two roles.

Every new registration is forced to `USER`. The create-user request has **no role
field at all**, and unknown JSON fields are rejected, so nobody can self-register
as an admin.

### Making the first admin

Registration always produces a `USER`, so promote the first admin by hand:

```bash
docker compose exec db psql -U keni -d game_renting \
  -c "UPDATE users SET role='ADMIN' WHERE user_name='keni';"
```

Restart is not needed. Sign in again with your existing password.

### Who can do what

| Endpoint | Anonymous | User | Admin |
|---|:---:|:---:|:---:|
| `POST /user` | yes | yes | yes |
| `GET /user/me` | 401 | **own only** | own only |
| `GET /user` | 401 | 403 | yes |
| `GET /user/{id}` | 401 | 403 | yes |
| `GET /subscription`, `GET /subscription/{id}` | 401 | yes | yes |
| `POST /subscription` | 401 | 403 | yes |
| `POST /game` | 401 | 403 | yes |
| `POST /subscription-game` | 401 | 403 | yes |
| `GET /subscription-game/**` | 401 | yes | yes |
| `POST` `/user-subscription` | 401 | **subscribes self + pays** | subscribes self |
| `GET /user-subscription/me` | 401 | **own only** | own only |
| `GET /user-subscription` | 401 | 403 | yes |
| `GET /user-subscription/user/{userId}` | 401 | 403 | yes |
| `GET /user-subscription/subscription/{id}` | 401 | 403 | yes |
| `GET /payment/me` | 401 | **own only** | own only |
| `GET /payment` | 401 | 403 | yes |
| `PATCH` `/payment/{id}/status` | 401 | 403 | yes |

Note the shape of the `/me` routes. There is no
`GET /payment/user/{userId}` any more on purpose — putting a user id in a path
variable and then checking it against the caller is how IDOR bugs happen.
The id comes from the security context via `@AuthenticationPrincipal`, so
there is nothing for a caller to tamper with.

### Trying it out

```bash
# register, becomes a USER
curl -X POST localhost:8080/user -H 'Content-Type: application/json' \
  -d '{"userName":"keni","password":"supersecret"}'

# refused: 403
curl -i -u keni:supersecret -X POST localhost:8080/game \
  -H 'Content-Type: application/json' \
  -d '{"title":"Minecraft","description":"blocks","company":"Mojang"}'

# allowed once promoted to ADMIN
curl -u root:adminpassword -X POST localhost:8080/game \
  -H 'Content-Type: application/json' \
  -d '{"title":"Minecraft","description":"blocks","company":"Mojang"}'
```

---

## API

All request bodies are JSON and validated. Unknown fields are rejected with a `400`,
so a client typo like `"userNam"` fails loudly instead of being silently dropped.

### Users

| Method | Path | Body | Returns |
|---|---|---|---|
| `POST` | `/user` | `{userName, password}` | `UserResponse` |
| `GET` | `/user/me` | | own `UserResponse` |
| `GET` | `/user` | | `List<UserResponse>` — admin |
| `GET` | `/user/{id}` | | `UserResponse` — admin |

### Subscriptions

| Method | Path | Body | Returns |
|---|---|---|---|
| `POST` | `/subscription` | `{name, price, durationDays}` | `SubscriptionResponse` |
| `GET` | `/subscription` | | `List<SubscriptionResponse>` |
| `GET` | `/subscription/{id}` | | `SubscriptionResponse` |

`durationDays` is how long a subscription to the tier lasts. It is what
`expiresAt` gets computed from when someone subscribes.

### Games

| Method | Path | Body | Returns |
|---|---|---|---|
| `POST` | `/game` | `{title, description, company}` | `Game` |

### Subscribing a user

`POST /user-subscription` is the checkout. Any signed in user can call it, and it
creates the subscription **and** its payment in one transaction.

| Method | Path | Body | Returns |
|---|---|---|---|
| `POST` | `/user-subscription` | `{subscriptionId, userId, amount, datePaid}` | `SubscribeResponse` |
| `GET` | `/user-subscription/me` | | own `List<UserSubscriptionResponse>` |
| `GET` | `/user-subscription` | | `List<UserSubscriptionResponse>` — admin, who bought what |
| `GET` | `/user-subscription/user/{userId}` | | admin |
| `GET` | `/user-subscription/subscription/{subscriptionId}` | | admin |

```bash
curl -u keni:supersecret -X POST localhost:8080/user-subscription \
  -H 'Content-Type: application/json' \
  -d '{"subscriptionId":"6f1c...","userId":"e4f2...","amount":9.99}'
```

```json
{
  "userSubscription": {
    "id": "a91b...", "userId": "e4f2...", "subscriptionId": "6f1c...",
    "startedAt": "2026-10-01T10:15:30Z", "expiresAt": "2026-10-31T10:15:30Z"
  },
  "payment": {
    "id": "c72e...", "userId": "e4f2...", "subId": "6f1c...",
    "datePaid": "2026-10-01T10:15:30Z", "amount": 9.99, "status": "COMPLETED"
  }
}
```

Rules it enforces:

| Situation | Result |
|---|---|
| `userId` in the body is not the caller | `403` |
| `subscriptionId` does not exist | `404` |
| `amount` does not match the tier price | `400` |
| caller already has that tier | `409` |
| everything valid | `200` |

`amount` is accepted from the client but cross-checked against
`Subscription.price`, and the stored payment always uses the tier's own price. If
the client's number were trusted as-is, a user could buy the Gold tier for €0.01.

### Adding games to a subscription

| Method | Path | Body | Returns |
|---|---|---|---|
| `POST` | `/subscription-game` | `{subscriptionId, gameId}` | `SubscriptionGameResponse` |
| `GET` | `/subscription-game/subscription/{subscriptionId}` | | `List<SubscriptionGameResponse>` |
| `GET` | `/subscription-game/game/{gameId}` | | `List<SubscriptionGameResponse>` |

### Payments

| Method | Path | Body | Returns |
|---|---|---|---|
| `PATCH` | `/payment/{id}/status` | `{status}` | `PaymentResponse` — admin |
| `GET` | `/payment/me` | | own `List<PaymentResponse>` |
| `GET` | `/payment` | | `List<PaymentResponse>` — admin, every payment |

**There is no `POST /payment`.** Payments are only ever created by
`POST /user-subscription`, in the same transaction as the subscription they pay for.
A standalone create would let anyone record a payment against a user who has no
subscription for it, so the route does not exist. `POST /payment` returns `405`.

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

**Own data is identified from the token, not from the request.** The first cut of
this API had `GET /payment/user/{userId}` and `GET /user-subscription/user/{userId}`,
and any signed in user could swap in someone else's id. Accepting an identifier
from the client and then comparing it to the caller is exactly how IDOR bugs get
shipped. Both routes are now `/me`, and the id is read off the
`@AuthenticationPrincipal`, so there is no attacker-controlled input to check.

**Payments have exactly one entry point.** `POST /user-subscription` is the only
thing that creates a `Payment`. There is no `POST /payment`, because a payment with
no subscription behind it is just a number someone typed — it would let a payment
be recorded against a user who never bought anything. Refunds and failures are a
separate concern handled by the admin status endpoint, which enforces a state
machine (`PENDING → COMPLETED | FAILED`, `COMPLETED → REFUNDED`, the rest
terminal).

**Subscribe and pay are one transaction.** `POST /user-subscription` writes the
`UserSubscription` and the `Payment` inside a single `@Transactional`. Splitting
them into two calls means you can end up with an active subscription that was
never paid for, which is exactly the kind of hole that only shows up in
production.

**Expiry is derived from the tier, not the client.** `Subscription` carries
`durationDays`, and `expiresAt = startedAt + durationDays`. The client never gets
to say how long their own subscription lasts.

**The price is checked, then discarded.** `SubscribeRequest` accepts `amount`
because a real client does send what it thinks it paid, but the service rejects
the request if it disagrees with `Subscription.price` and then stores the tier's
own price. Taking the client's number at face value would mean a €9.99 tier is
available for €0.01.

**Registration cannot grant itself a role.** `NewUserRequest` has no role field,
`UserService` hardcodes `Role.USER`, and unknown JSON fields are rejected — so
posting `"role":"ADMIN"` is a `400`, not a silent privilege escalation.

**Role rules live in `SecurityConfig`, ordered most specific first.** Request
matchers are evaluated top to bottom and the first hit wins, so every `/me` rule
has to be declared above the `/something/*` wildcard that would otherwise swallow
it. That ordering bug is exactly what the security tests guard against.

**The access rules are tested, not assumed.** `SecurityAccessTest` drives every
rule in the table above through MockMvc as three different people — anonymous, a
normal user, and an admin — using real BCrypt hashes through the real
`UserDetailsService`. It runs on H2, so `./mvnw test` needs no database.

**Passwords are BCrypt-hashed at rest.** `SecurityConfig` exposes a
`PasswordEncoder` bean; `UserService` encodes on the way in and the hash is never
readable through the API.

## Tests

```bash
./mvnw test
```

29 tests, no database required — `src/test/resources/application.properties` points
at an in-memory H2 so it shadows the Postgres config.

`SecurityAccessTest` covers the whole access matrix: every rule is checked from all
three angles (no credentials, a normal user, an admin), plus the privilege
escalation attempts — registering with `"role":"ADMIN"`, and trying to subscribe
another user.

## Not done yet

Honest list of what is missing, roughly in priority order:

- **HTTP Basic, not tokens.** Every call resends credentials and browsers cache them
  for the whole realm. JWT is the real answer for a SPA or mobile client.
- **`ddl-auto=update` instead of Flyway migrations.**
- **No renewal.** `user_subscriptions` has a unique constraint on
  `(user_id, subscription_id)`, so one row per user per tier is permanent — even
  after it expires. Supporting renewal means either extending the existing row or
  dropping that constraint and keeping a history of rows. Not decided yet.
- **The first admin has to be promoted by hand** with a SQL update. A seed user or a
  `CommandLineRunner` bootstrap would be cleaner.
- **No pagination.** Every list endpoint returns everything.
- **No global exception handler.** Services throw `ResponseStatusException`, which
  works, but a `@RestControllerAdvice` returning `ProblemDetail` would be better.
- **Inconsistent response shape.** `POST /game` returns the entity while
  everything else returns a DTO.
- **Payment status has no transition rules.** A payment can go straight from
  `PENDING` to `REFUNDED`.
- **No CI.** A GitHub Actions workflow running the build on every push.
- **Tests cover access control and validation, not business logic.** No repository
  tests and no service unit tests yet.

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

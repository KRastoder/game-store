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
as an admin. There is no user-editing endpoint either, so an admin cannot promote
anyone through the API — promotion is a database operation, deliberately.

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
| `POST` `/user-subscription/renew` | 401 | **renews self + pays** | renews self |
| `PATCH` `/user-subscription/{id}/cancel` | 401 | **own row, or 403** | any row |
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
| `POST` | `/user-subscription/renew` | `{subscriptionId, amount}` | `SubscribeResponse` |
| `PATCH` | `/user-subscription/{id}/cancel` | none | `CancelSubscriptionResponse`, with `refundedAmount` |
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

### Renewal

`POST /user-subscription/renew` charges again and pushes `expiresAt` out by another
`durationDays`.

Renewing **early keeps the time already paid for** and adds the new period to the
current expiry. Renewing a lapsed subscription restarts from today:

```
subscribe   Oct  1  ->  Oct 31
renew       Oct 31  ->  Nov 30     (not Oct 31 again)
renew       Nov 30  ->  Dec 30
```

`startedAt` is never overwritten — it is when the user first subscribed to the tier,
so it doubles as "customer since".

The subscription **row is extended, not duplicated**, because `user_subscriptions`
has a unique constraint on `(user_id, subscription_id)`. What was paid is still fully
recorded: each renewal writes a payment, so "how much has this user paid for Gold"
is answerable from `payments` even though there is only ever one subscription row.

| Situation | Result |
|---|---|
| no subscription to that tier | `404` |
| `amount` differs from the tier price, either way | `400` |
| subscription already ended | `409`, subscribe again instead |
| valid | `200`, plus a new `COMPLETED` payment |

### Refunds end access

`PATCH /payment/{id}/status` with `REFUNDED` also cancels the subscription the
payment was for. Money going back and access staying on were two separate facts
that should never disagree.

`user_subscriptions.cancelled_at` records when access was cut, which is different
from `expires_at` — a cancelled subscription ended early, an expired one ran its
course. `UserSubscriptionResponse` exposes `cancelledAt` and a computed `active`
flag, so a client never has to work that out itself.

```
subscribe      active: true   cancelledAt: null
refund         active: false  cancelledAt: 2026-10-01T01:06Z
```

Because the table has a unique constraint on `(user_id, subscription_id)`, a
cancelled row is **revived in place** when the user buys the tier again rather than
inserted anew. Without that, closing a subscription on refund would have made the
tier permanently unpurchasable for that user — the fix would have become its own
lockout.

| Situation | Result |
|---|---|
| renewing a cancelled or expired subscription | `409`, buy it again |
| subscribing to a tier you hold but which has ended | `200`, row revived, charged again |
| subscribing to a tier you already hold and is active | `409` |

### Cancelling

`PATCH /user-subscription/{id}/cancel` ends access early. No request body — the row id
in the path is the whole request.

```bash
curl -u mallory:supersecret -X PATCH localhost:8080/user-subscription/<id>/cancel
```

**Cancelling prorates a refund.** A customer walking away gets back the unspent
fraction of the period, so cancelling on day one refunds almost everything and
cancelling on the last day refunds nothing:

```
30 day tier at 9.99, cancelled 15 days in  ->  4.99 back, 5.00 kept
30 day tier at 9.99, cancelled at once      ->  9.99 back
30 day tier at 9.99, cancelled after expiry ->  0.00 back
```

The refund attaches to the **newest** payment, because that is the one covering the
current period. Payments now carry `refundedAmount` and `netAmount`, since a single
status flag cannot express "9.99 paid, 4.99 back, 5.00 still held". A refund is refused
if it would exceed what was paid, so a bug cannot invent money.

| Situation | Result |
|---|---|
| the row belongs to someone else and you are not an admin | `403` |
| unknown row | `404` |
| already cancelled | `200`, `refundedAmount: 0` — nothing left to prorate |
| valid | `200` with the refund and the updated payment |

### Expiry reminders

`ExpiryReminderJob` runs on a schedule and warns holders whose subscription ends within
three days:

```properties
app.reminders.cron=0 0 7 * * *   # 07:00 daily by default
```

It goes through the `ExpiryNotifier` interface. The shipped implementation logs, so
there is no SMTP dependency and no side effects in tests — define your own
`ExpiryNotifier` bean and the job picks it up. Each subscription is warned **once**:
`reminder_sent_at` is stamped in the same transaction as the send, and renewing clears
it so the next period warns again. Cancelled and already-expired subscriptions are
never contacted.

### Time is injected

`TimeConfig` provides a `Clock` bean, and nothing calls `Instant.now()` directly.
Proration is a function of "now", and it is exactly the kind of arithmetic that is wrong
in a way a test at the current moment cannot catch. `ProrationTest` pins it at an exact
moment, including halfway through a period.

Owner or admin only. The owner check lives in the service, not the route matcher,
because no path pattern can express "the caller owns this id".

### Adding games to a subscription

| Method | Path | Body | Returns |
|---|---|---|---|
| `POST` | `/subscription-game` | `{subscriptionId, gameId}` | `SubscriptionGameResponse` |
| `GET` | `/subscription-game/subscription/{subscriptionId}` | | `List<SubscriptionGameResponse>` |
| `GET` | `/subscription-game/game/{gameId}` | | `List<SubscriptionGameResponse>` |

### Payments

| Method | Path | Body | Returns |
|---|---|---|---|
| `PATCH` | `/payment/{id}/status` | `{status}` | `PaymentResponse` — admin. `REFUNDED` also ends the subscription |
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

**Every list endpoint is capped, not just paginated.** A page size is a number the client
controls, so without an upper bound `?size=1000000` is a one request denial of service.
The cap lives in configuration, and a test asserts that an oversized request is reduced
rather than honoured.

**Errors have one shape.** Spring's default error body carries the exception class, the
message and a full stack trace — this API was handing its entire internal call stack to
the caller on a failed validation. `GlobalExceptionHandler` returns an RFC 9457
`ProblemDetail` instead, so clients can rely on one structure and no internal class name
or line number is ever sent out. `401` and `403` are written by hand in `SecurityConfig`,
because they happen before any controller exists to throw.

**Own data is identified from the token, not from the request.** The first cut of
this API had `GET /payment/user/{userId}` and `GET /user-subscription/user/{userId}`,
and any signed in user could swap in someone else's id. Accepting an identifier
from the client and then comparing it to the caller is exactly how IDOR bugs get
shipped. Both routes are now `/me`, and the id is read off the
`@AuthenticationPrincipal`, so there is no attacker-controlled input to check.

**A refund takes the access with it.** Handing money back while leaving access on
is a contradiction, so `PaymentService` cancels the subscription in the same
transaction. Partial refunds are deliberately not supported — refunding any payment
for a tier closes that user's access to it — because that is the safe direction to
be wrong in, and it keeps `REFUNDED` the one irreversible transition.

**Ownership is checked in the service, not in the route matcher.** Cancelling is
addressed by row id, and no `requestMatchers` pattern can express "the caller owns this
id". A rule like `hasRole("ADMIN")` would either lock owners out of cancelling their
own subscription or hand everyone else's subscription to every user.

**Closing a subscription revives it rather than adding a row.** The unique
constraint on `(user_id, subscription_id)` means one row per user per tier, so a
cancelled row is reused on the next purchase. The alternative — refusing — would
turn a refund into a permanent lockout of that tier for that user.

**Renewal takes a row lock.** Renewal reads `expiresAt`, adds a period and writes it
back. Without `PESSIMISTIC_WRITE` two concurrent renewals both read the same
`expiresAt`, both add a period, and the second write discards the first — the
customer pays twice and receives one period, which is a refund someone has to
issue by hand. `UserSubscriptionRepository.findForRenewal` locks the row for the
duration of the transaction.

**Renewing early does not steal paid-for time.** The new period is added to the
current `expiresAt` when the subscription is still active, and to today when it has
lapsed. Adding to today unconditionally would silently discard the remainder of a
period somebody already paid for.

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

**Registration cannot grant a role, and nothing else can either.** `NewUserRequest`
has no `role` field, `UserService` hardcodes `Role.USER`, and unknown JSON fields are
rejected — so `"role":"ADMIN"` is a `400`, not a silent escalation. There is also no
`PUT`/`PATCH`/`DELETE` on `/user/{id}`, so **no endpoint, admin included, can change
an existing user's role.** Every `ADMIN` comes from the SQL promotion step above.

The database backs this up too: `role` is `NOT NULL DEFAULT 'USER'`, so a row that
somehow skipped the application still lands as a plain user.

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

139 tests, no database required — `src/test/resources/application.properties` points
at an in-memory H2 so it shadows the Postgres config. That file replaces the main one
wholesale rather than merging, so settings like the page size cap have to be repeated
there or tests silently run on Spring's defaults.

`SecurityAccessTest` covers the whole access matrix: every rule is checked from all
three angles (no credentials, a normal user, an admin), plus the privilege
escalation attempts — registering with `"role":"ADMIN"` in several spellings and
casings, sending `authorities` or `enabled`, trying to `PUT`/`PATCH`/`DELETE` a user
to change their role, and trying to subscribe somebody else.

`RepositoryQueryTest` exercises every derived query and database constraint.
`ServiceRulesTest` covers the business rules, including the payment state machine and
the refund-ends-access behaviour. `ProblemDetailShapeTest` checks every error path
returns the same RFC 9457 shape and leaks no stack trace. `ProrationTest` pins the
refund arithmetic at exact moments, and `ExpiryReminderJobTest` drives the scheduler's
query and its once-only guard. `PaginationTest` checks the envelope, the size cap, and
that pages neither overlap nor drop rows.

## Pagination

Every list endpoint returns a page, not the whole table. Clients send
`?page=`, `?size=` and `?sort=`:

```bash
curl -u root:adminpassword 'localhost:8080/subscription?page=1&size=20&sort=name,desc'
```

```json
{
  "content": [ { "id": "...", "name": "Gold", "price": 9.99, "durationDays": 30 } ],
  "page": 0,
  "size": 20,
  "totalElements": 25,
  "totalPages": 2,
  "first": true,
  "last": false,
  "empty": false
}
```

| Parameter | Default | Notes |
|---|---|---|
| `page` | `0` | zero based |
| `size` | `20` | **capped at 100** |
| `sort` | per endpoint | `sort=name` or `sort=name,desc` |

**The size cap is the point.** Without it a single request for `?size=1000000` loads the
entire table into memory, so `spring.data.web.pageable.max-page-size` is set to 100 and
asked-for sizes above it are silently reduced. Spring Boot's own default is 2000, which is
still far too generous to be safe.

**This is a breaking change.** List endpoints used to return a bare JSON array; they now
return the envelope above. Anything already consuming them needs updating.

`PageResponse` is a record rather than Spring's `Page`, because `Page`'s JSON layout is an
implementation detail that has shifted between releases — serialising it directly would
pin the API to a library version.

**Pages are scoped, not just paged.** `/payment/me` and `/user-subscription/me` paginate
too, so a user with thousands of payments cannot ask for all of them at once, and the
size cap applies to the admin endpoints equally.

## Errors

Every failure returns an RFC 9457 problem document with the same shape, whatever
caused it:

```json
{
  "type": "about:blank",
  "title": "Validation failed",
  "status": 400,
  "detail": "One or more fields are invalid.",
  "instance": "/user",
  "errors": { "userName": "must not be blank" }
}
```

| Status | When |
|---|---|
| `400` | `@Valid` failure, malformed body, unknown field, price mismatch |
| `401` | no credentials, or wrong credentials |
| `403` | signed in, but not allowed — or cancelling someone else's subscription |
| `404` | no such row, or no such route |
| `405` | wrong HTTP method on a real route |
| `409` | duplicate username, duplicate game title, already subscribed, illegal payment transition |
| `500` | unhandled, logged in full, generic message to the caller |

**No stack traces reach the client.** Spring's default error body includes the exception
class, the message and a full trace — this API was returning its entire internal call
stack on a failed validation, naming every class and line number. `GlobalExceptionHandler`
replaces that, and `ProblemDetailShapeTest` asserts no response body ever contains
`at com.keni`, `Exception`, or a `java.base` frame.

**Validation errors name the field.** `errors` maps each rejected field to its message,
so a client can highlight the exact inputs instead of parsing a sentence.

**Rejected bodies are never echoed.** A malformed request could contain a password, so
the reason stays generic rather than quoting what was sent.

**401 and 403 are written by hand in `SecurityConfig`.** They are raised before any
controller runs, so `@RestControllerAdvice` never sees them. Note that `.httpBasic()`
installs its own entry point, which wins over `.exceptionHandling()` and would leave the
body empty — the custom entry point is configured *inside* `httpBasic` for that reason.

## Not done yet

Honest list of what is missing, roughly in priority order:

- **HTTP Basic, not tokens.** Every call resends credentials and browsers cache them
  for the whole realm. JWT is the real answer for a SPA or mobile client.
- **`ddl-auto=update` instead of Flyway migrations.**
- **Reminders are logged, not emailed.** `LoggingExpiryNotifier` stands in for SMTP.
  A real deployment supplies an `ExpiryNotifier` bean.
- **The reminder job is single-node.** Two app instances would both run the cron. The
  `reminder_sent_at` guard stops duplicate emails, but the job needs a distributed lock
  or a dedicated scheduler to be safe at scale.
- **The first admin has to be promoted by hand** with a SQL update. A seed user or a
  `CommandLineRunner` bootstrap would be cleaner.
- **Offset pagination, not cursor pagination.** `?page=` walks an offset, so deep pages
  slow down and a row inserted mid-walk can appear on two pages. Keyset pagination needs
  stable sort keys and is the right answer for a busy table.
- **Inconsistent response shape.** `POST /game` returns the entity while
  everything else returns a DTO.
- **No CI.** A GitHub Actions workflow running the build on every push.
- **No integration test against Postgres.** Everything runs on H2.


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




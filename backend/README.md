# RateMyUjProfessor — Backend (Jev Moderation)

Spring Boot 3.3 / Java 21 / PostgreSQL backend. Review comments are classified
by [Jev](https://openrouter.ai/docs/guides/community/jev), TypeSafe's System One
decision model on OpenRouter — not by an LLM. Jev answers typed questions and
returns probabilities, so there is no prompt text to parse and no output-token
bill.

## The moderation flow

```
POST /api/reviews
   └─> ReviewService.submit()
         1. validate professor + course
         2. per-student hourly rate limit (429 past the cap)
         3. one-review-per-student-per-course check
            (a REJECTED attempt reuses the same slot on resubmit)
         4. save as PENDING_MODERATION
         5. ModerationService.moderate()  ──HTTP──>  OpenRouter /api/alpha/decisions
         6. Jev answers one "choice" question (APPROVE/REJECT/ESCALATE)
            plus one "noul" per policy category (probability of each violation)
         7. verdict:
              APPROVE  -> PUBLISHED  + atomic professor stats update
              REJECT   -> REJECTED   + category feedback returned to student
              ESCALATE -> MANUAL_REVIEW (admin queue)
```

Fail-safe: any timeout, contract failure, or API error becomes ESCALATE, and a
contradictory answer (APPROVE with a violation above the threshold, or REJECT
with none) also escalates. A review is **never** published without a confident
APPROVE and zero flags.

## Endpoints

| Endpoint | Auth | Purpose |
|---|---|---|
| `GET /api/professors?q=&page=&size=` | public | search by name, department, college, or course code/name |
| `GET /api/professors/{id}` | public | detail with rating breakdown and the professor's courses |
| `GET /api/professors/{id}/reviews?page=&size=` | public | published reviews, newest first; `isMine` when signed in |
| `POST /api/reviews` | session cookie | submit a review (rate limited per student) |
| `DELETE /api/reviews/{id}` | session cookie | delete your own review, rolls stats back |
| `POST /api/auth/request` / `verify` / `logout`, `GET /api/auth/me` | - | OTP sign-in |
| `GET /api/admin/reviews?status=MANUAL_REVIEW&page=&size=` | `X-Admin-Key` | moderation queue |
| `PATCH /api/admin/reviews/{id}` `{ "status": "PUBLISHED" }` | `X-Admin-Key` | publish/reject/hide/remove; stats adjust exactly once |

Admin targets: `PUBLISHED`, `REJECTED`, `HIDDEN`, `REMOVED`. Publishing a held
review adds its rating to the professor's stats; hiding/removing a published
review rolls it back; repeating the same status is a no-op.

## Running locally

```bash
docker compose up -d          # Postgres 16 on localhost:5432
export OPENROUTER_API_KEY=sk-or-...
export HASH_PEPPER=change-me
export JWT_SECRET=change-me-at-least-32-bytes-long
mvn spring-boot:run
```

Add `SPRING_PROFILES_ACTIVE=dev` to seed a small demo catalog (courses +
professors) the first time you boot an empty database. Admin endpoints also
need `ADMIN_API_KEY`, e.g. `ADMIN_API_KEY=local-admin`.

Flyway creates the schema on first start; `spring.jpa.hibernate.ddl-auto` is
`validate`, so entity/schema drift fails fast.

## Environment variables

| Var | Purpose |
|---|---|
| `DATABASE_URL` | JDBC URL, default `jdbc:postgresql://localhost:5432/ratemyuj` |
| `DB_USERNAME` / `DB_PASSWORD` | default `ratemyuj` / `ratemyuj` (matches compose.yaml) |
| `OPENROUTER_API_KEY` | OpenRouter key (required); authenticates the Jev Decisions API |
| `MODERATION_MODEL` | default `typesafe/jev-1.13` (`~typesafe/jev-latest` tracks releases) |
| `MODERATION_ESCALATE_BELOW_CONFIDENCE` | default `0.6`. Choice verdicts under this go to MANUAL_REVIEW |
| `MODERATION_CATEGORY_THRESHOLD` | default `0.5`. Noul probability at which a category is flagged |
| `HASH_PEPPER` | secret for HMAC of student emails. Not the JWT key |
| `JWT_SECRET` | HS256 key, at least 32 bytes |
| `JWT_TTL` | session lifetime, default `7d` |
| `COOKIE_SECURE` | default `true`. `http://localhost` still stores the cookie |
| `REVIEWS_PER_HOUR` | per-student submission cap, default `10`. `0` disables it |
| `ADMIN_API_KEY` | unlocks `/api/admin/**`; empty disables the admin API |
| `MAIL_MODE` | `log` (default, prints the code) or `smtp` |
| `SMTP_HOST` / `SMTP_PORT` / `SMTP_USERNAME` / `SMTP_PASSWORD` | required when `MAIL_MODE=smtp` |

## Authentication

Passwordless. `POST /api/auth/request` with `{ "email" }` accepts only
`@ju.edu.jo` (no `+` tags) and emails a 6-digit code. `POST /api/auth/verify`
with `{ "email", "code" }` sets an `HttpOnly`, `Secure`, `SameSite=Lax`
cookie named `session`. The JWT subject is `HMAC(email)`, the same value
stored on reviews. The token does not contain the email.

`GET /api/auth/me` returns `{ "authenticated": true|false }`.
`POST /api/auth/logout` clears the cookie. Review submit and delete require
the cookie. `GET /api/professors/**` stays public. With `MAIL_MODE=log` the
code is printed in the server log; a real deploy sets `MAIL_MODE=smtp`.

The per-address and per-IP code limits use the direct remote address. Expired
OTP rows are purged on a timer (the Mongo TTL index replacement).

## Still open

- **Moderation tuning loop** — no scheduled report over REJECTED/MANUAL_REVIEW
  rows yet, though every decision is stored for it.
- **Live Jev smoke test** — the client contract is tested against a mock and a
  real boot reaches the endpoint, but a real verdict needs an OpenRouter key.

## Running the tests

```bash
mvn test
```

> The project targets Java 21. If your default JDK is newer than Mockito
> supports (Java 26+), point Maven at a 21 JDK, e.g.
> `JAVA_HOME=/usr/lib/jvm/java-21-openjdk mvn test`.

104 tests. The Jev client and moderation service are unit-tested with a mocked
HTTP layer (`MockRestServiceServer`); the persistence, catalog, and
submission-flow tests run against a real Postgres 16 via Testcontainers (Docker
required, image `postgres:16-alpine`):

| Class | Covers |
|---|---|
| `ModerationServiceTest` | choice mapping, category ordering, contradiction/low-confidence escalation, fail-safe, question shape |
| `JevClientTest` | decisions request shape (model, state, typed questions), response parsing, contract failure |
| `ReviewServiceTest` | publish/reject/escalate flows, uniqueness rules, rejected-slot reuse, race -> 409, rate limit, catalog validation, deletion |
| `ProfessorStatsServiceIntegrationTest` | atomic counters + average, replace swaps, 80 concurrent ratings with no lost updates |
| `ReviewSubmissionFlowIntegrationTest` | full submit flow on real Postgres, row reuse on resubmit, 409 duplicate, MANUAL_REVIEW hold, admin publish applies stats once |
| `ProfessorCatalogIntegrationTest` | search by name/department/course, paging, detail breakdown + courses, inactive exclusion |
| `ReviewPersistenceIntegrationTest` | unique constraint, jsonb moderation round-trip, paging, OTP purge |
| `AdminServiceTest` | status transitions, stats adjust exactly once, invalid targets, 404 |
| `AdminSecurityTest` | admin key missing/wrong -> 403, valid key -> 200, invalid status -> 400 |
| `ReviewControllerTest` | validation errors, enum handling, paging clamps, 409 mapping, 204 delete |
| `AuthSecurityTest` | anonymous 401, cookie principal, public listing, session cookie flags, logout, `/me`, domain rejection |
| `EmailPolicyTest` | `@ju.edu.jo` only, plus-tags, subdomains |
| `OtpServiceTest` | rate limit, hashed code, attempt lockout, mail failure |
| `JwtServiceTest` | subject round-trip, expiry, wrong secret, wrong audience, short secret |
| `HashServiceTest` | determinism, normalization, pepper enforcement, fail-fast startup |

Note: Docker 25+ rejects the API version docker-java pins by default, so
Surefire passes `-Dapi.version=1.44` to the test JVM. Docker 24 or older is
not supported by the integration tests.

## Hardening applied (v0.3)

- **Postgres migration** — JPA entities + Flyway migrations; the compound
  unique index on `(student_hash, professor_id, course_id)` is a real DB
  constraint, and `DataIntegrityViolationException` maps to 409.
- **Race-safe rating aggregates** — one atomic `UPDATE` adjusts counters,
  star bucket, and average together. Concurrent reviews can't lose an
  increment or persist a stale average (covered by a 16-thread test).
- **Decision-model moderation** — no generated prose, no JSON parsing;
  Jev returns typed answers with probabilities. Contradictory answers are
  escalated rather than published.
- **Prompt-injection defense** — the comment is only ever a value in the
  Decisions `state`; the model's questions are instructions, the data is not.
  Comments addressed to the moderator are flagged OFF_TOPIC.
- **`HashService`** — single funnel for HMAC(student email); refuses to boot
  without `HASH_PEPPER`.
- **Admin API key** — constant-time `X-Admin-Key` check in front of
  `/api/admin/**`; the API stays disabled when no key is configured.
- **Submission rate limit** — counts a student's attempts in the last hour
  (create and resubmit both count) and returns 429 past the cap, so the Jev
  bill can't be run up on purpose.
- **Error hygiene** — malformed JSON/enums and bad query params -> 400,
  paging is clamped.

## Question-tuning tips

The policy lives in `ModerationService` as the `verdict` choice criteria plus
one `noul` question per category. Every decision is stored on the review
(`moderation_*` columns, categories in `jsonb`) with the model's probabilities,
confidence, version, and latency — query REJECTED/MANUAL_REVIEW rows
periodically to find question gaps. Student feedback is generated from
per-category templates in English and Arabic, so it never echoes the offensive
content back.

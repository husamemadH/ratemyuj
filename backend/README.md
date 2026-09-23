# RateMyUjProfessor — Backend (AI Moderation)

Spring Boot 3.3 / Java 21 / MongoDB backend with LLM comment moderation via OpenRouter.

## The moderation flow

```
POST /api/reviews
   └─> ReviewService.submit()
         1. validate professor + course
         2. one-review-per-student-per-course check
            (a REJECTED attempt reuses the same slot on resubmit)
         3. save as PENDING_MODERATION
         4. ModerationService.moderate()  ──HTTP──>  OpenRouter
         5. verdict:
              APPROVE  -> PUBLISHED  + atomic professor stats update
              REJECT   -> REJECTED   + feedback returned to student
              ESCALATE -> MANUAL_REVIEW (admin queue)
```

Fail-safe: any timeout, parse failure, or API error becomes ESCALATE.
A review is **never** published without an explicit APPROVE.

## Environment variables

| Var | Purpose |
|---|---|
| `MONGODB_URI` | Mongo connection string |
| `OPENROUTER_API_KEY` | OpenRouter key (required) |
| `MODERATION_MODEL` | default `openai/gpt-4o-mini` |
| `MODERATION_FALLBACK_MODEL` | tried once if primary errors |
| `HASH_PEPPER` | secret for HMAC of student emails. Not the JWT key |
| `JWT_SECRET` | HS256 key, at least 32 bytes |
| `JWT_TTL` | session lifetime, default `7d` |
| `COOKIE_SECURE` | default `true`. `http://localhost` still stores the cookie |
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

The per-address and per-IP code limits use the direct remote address.

## What is deliberately NOT in this module yet

- **Admin endpoints** — the MANUAL_REVIEW queue needs
  `GET /api/admin/reviews?status=MANUAL_REVIEW` and
  `PATCH /api/admin/reviews/{id}/status` (approve/reject with stats update).
- **Rate limiting** — cap review submissions per student per hour so the
  moderation API bill can't be run up on purpose.

## Running the tests

```bash
mvn test
```

74 tests, no external infrastructure needed
(Mongo and OpenRouter are mocked; the HTTP layer is exercised via
`MockRestServiceServer`):

| Class | Covers |
|---|---|
| `ModerationServiceTest` | verdict parsing, fence stripping, fail-safe escalation, confidence threshold |
| `ReviewServiceTest` | publish/reject/escalate flows, uniqueness rules, rejected-slot reuse, insert-race -> 409, catalog validation, deletion + stats rollback |
| `ProfessorStatsServiceTest` | $inc counter updates, guarded average write, divide-by-zero, bucket swaps |
| `OpenRouterClientTest` | request shape (model, temp 0, json mode), fallback ordering, failure propagation |
| `ReviewControllerTest` | validation errors, enum handling, paging clamps, 409 mapping, 204 delete |
| `AuthSecurityTest` | anonymous 401, cookie principal, public listing, session cookie flags, logout, `/me`, domain rejection |
| `EmailPolicyTest` | `@ju.edu.jo` only, plus-tags, subdomains |
| `OtpServiceTest` | rate limit, hashed code, attempt lockout, mail failure |
| `JwtServiceTest` | subject round-trip, expiry, wrong secret, wrong audience, short secret |
| `HashServiceTest` | determinism, normalization, pepper enforcement, fail-fast startup |

Suggested next step once you have Docker: a `@Testcontainers` integration test
that runs the full submit flow against a real Mongo (verifies the unique
index + TTL behavior that mocks can't).

## Hardening applied (v0.2)

- **Race-safe rating aggregates** — `findAndModify(returnNew)` + optimistic
  guarded average write; concurrent reviews can no longer persist a stale avg.
- **Concurrent duplicate submits** — `DuplicateKeyException` now maps to 409.
- **Prompt-injection defense** — comments are wrapped in data tags and the
  system prompt treats embedded instructions as a REJECT signal.
- **`HashService`** — single funnel for HMAC(student email); refuses to boot
  without `HASH_PEPPER`.
- **Error hygiene** — malformed JSON/enums -> 400, paging is clamped,
  fallback logic only catches transport/contract failures.

## Prompt-tuning tips

The policy lives in `ModerationService.SYSTEM_PROMPT`. Every decision is stored
on the review (`moderation` sub-document) with the model's reasoning, confidence,
and latency — query REJECTED/MANUAL_REVIEW docs periodically to find prompt gaps.
The prompt already handles English, Arabic, and Arabizi.

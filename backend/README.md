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
| `HASH_PEPPER` | secret for HMAC of student emails |

## What is deliberately NOT in this module yet

- **OTP + JWT auth** — `ReviewController` reads `X-Student-Hash` from a header
  as a dev placeholder. Replace with `@AuthenticationPrincipal` once the
  OTP email flow and `JwtAuthFilter` are implemented.
- **Admin endpoints** — the MANUAL_REVIEW queue needs
  `GET /api/admin/reviews?status=MANUAL_REVIEW` and
  `PATCH /api/admin/reviews/{id}/status` (approve/reject with stats update).
- **Rate limiting** — cap review submissions per student per hour so the
  moderation API bill can't be run up on purpose.

## Running the tests

```bash
mvn test
```

62 assertions across 6 test classes, no external infrastructure needed
(Mongo and OpenRouter are mocked; the HTTP layer is exercised via
`MockRestServiceServer`):

| Class | Covers |
|---|---|
| `ModerationServiceTest` | verdict parsing, fence stripping, fail-safe escalation, confidence threshold |
| `ReviewServiceTest` | publish/reject/escalate flows, uniqueness rules, rejected-slot reuse, insert-race -> 409, catalog validation, deletion + stats rollback |
| `ProfessorStatsServiceTest` | $inc counter updates, guarded average write, divide-by-zero, bucket swaps |
| `OpenRouterClientTest` | request shape (model, temp 0, json mode), fallback ordering, failure propagation |
| `ReviewControllerTest` | validation errors, enum handling, paging clamps, 409 mapping, 204 delete |
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

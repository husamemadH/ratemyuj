# RateMyUjProfessor

Anonymous professor-review site for university students. Students verify
via a one-time code sent to their student email (no passwords), search for
professors, and leave one rating + review per course. Every review is
checked by an LLM (via OpenRouter) for constructive tone before it's
published.

## Structure

```
backend/    Spring Boot 3 + MongoDB API, AI moderation via OpenRouter
frontend/   React + Tailwind UI (Arabic, RTL)
```

See each folder's own README for setup and run instructions.

## Status

- ✅ Domain model, review submission flow, AI moderation pipeline, test suite
- ✅ UI (mocked data — not yet wired to the live API)
- ⬜ OTP + JWT auth (backend currently takes a dev placeholder header)
- ⬜ Admin endpoints for the MANUAL_REVIEW queue
- ⬜ Rate limiting on submissions

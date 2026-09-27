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


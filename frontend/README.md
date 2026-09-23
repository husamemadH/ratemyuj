# RateMyUjProfessor — Frontend

React + Tailwind UI (Arabic, RTL). Currently wired to an in-file mock API —
see `API:` comments in `src/App.jsx` for the exact Spring Boot endpoints
each mock stands in for.

## Run locally

```bash
npm install
npm run dev
```

## Connecting to the real backend

The Vite dev server proxies `/api` to `http://localhost:8080`, so the
session cookie is first-party. Sign-in (`/api/auth/request`, `/api/auth/verify`,
`/api/auth/me`, `/api/auth/logout`) already uses that proxy.

Professor search and review submission still use the in-file mock data.
When those calls move to the API, send the cookie and do not set
`X-Student-Hash`:

```js
const res = await fetch("/api/reviews", {
  method: "POST",
  credentials: "include",
  headers: { "Content-Type": "application/json" },
  body: JSON.stringify({ professorId, courseId, rating, comment, grade, difficulty, wouldTakeAgain }),
}).then(r => r.json());
```

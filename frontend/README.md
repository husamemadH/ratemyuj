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

Replace the `mockSubmitReview` call and the hardcoded `PROFESSORS` /
`COURSES` data with `fetch` calls to the endpoints documented inline,
e.g.:

```js
const res = await fetch("/api/reviews", {
  method: "POST",
  headers: { "Content-Type": "application/json", Authorization": `Bearer ${token}` },
  body: JSON.stringify({ professorId, courseId, rating, comment, grade, difficulty, wouldTakeAgain }),
}).then(r => r.json());
```

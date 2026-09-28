# RateMyUjProfessor — Frontend

React + Tailwind UI (Arabic, RTL), wired to the Spring Boot API.

## Run locally

The Vite dev server proxies `/api` to `http://localhost:8080`, so the session
cookie stays first-party. Start the backend first (see `../backend/README.md`).

```bash
npm install
npm run dev
```

## What it calls

| UI | Endpoint |
|---|---|
| بحث الصفحة الرئيسية | `GET /api/professors?q=&page=&size=` |
| ملف الدكتور (المعدل + التوزيع) | `GET /api/professors/{id}` |
| تقييمات الدكتور (مع paging) | `GET /api/professors/{id}/reviews?page=&size=` |
| إرسال تقييم | `POST /api/reviews` (cookie required) |
| الدخول بالرمز | `POST /api/auth/request`, `POST /api/auth/verify` |
| الجلسة/الخروج | `GET /api/auth/me`, `POST /api/auth/logout` |

All requests send `credentials: "include"` and never send an identity header —
the server derives the student from the HttpOnly session cookie.

## Admin queue

Open `/#/admin` (or click «الإدارة» in the header) and enter the backend's
`ADMIN_API_KEY`. The queue lists reviews by status (MANUAL_REVIEW, REJECTED,
PUBLISHED, HIDDEN, REMOVED) with the full moderation audit trail (verdict,
flags, confidence), and actions publish/reject/hide/remove. The key lives in
`sessionStorage` only and is sent as the `X-Admin-Key` header; professor stats
adjust server-side.

## Production build

```bash
npm run build
```

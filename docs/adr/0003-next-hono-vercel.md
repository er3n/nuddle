# 0003 Next.js + Hono on Vercel for API and landing

Status: Accepted (2026-10-11).

## Context
We need a landing page now, a REST API for the mobile app, and later a web page for senders. Agents are most fluent in TypeScript. Next.js App Router changes often and agents make mistakes with its server/client conventions; a pure JSON API does not need any of that.

## Decision
- One Next.js app in `apps/web`, deployed on Vercel (`fra1`).
- Next serves pages (landing, later the sender page).
- The API is a Hono app mounted in a catch-all route handler under `/api`. Routes are defined with `@hono/zod-openapi`, which gives request validation and the OpenAPI document from the same zod schemas.
- Versioned under `/api/v1`. Internal endpoints (`/api/internal/tick`, OTP hook) are authenticated with a shared secret. Test endpoints exist only in E2E mode.
- HTTP tests use `app.request()` without starting a server.

## Consequences
- Hobby plan is non-commercial; switch to Pro before charging money or at launch.
- No Server Actions, no React Server Components in API code. API code is plain Hono.

## Rejected
- **Plain Next route handlers for the API:** no OpenAPI generation, validation by hand.
- **Separate API deployment (Hono on Workers, Ktor):** see ADR 0002.

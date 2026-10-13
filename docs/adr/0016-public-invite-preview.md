# 0016 Public invite preview before sign-in

Status: Accepted (2026-10-13). Refines ADR 0011 (identity) and open-questions #13 (invite links); decided by the human in NUD-38.

## Context
An invite link (`https://nuddle.app/i/{code}`) opened while signed out should show who invites (0b, "Selin seni dürt'e davet etti") and, after sign-in, prefill the name the inviter typed (0e). Before sign-in the app has no token, so it needs an endpoint that answers without one, and anyone holding the code learns the inviter's name.

## Decision
- `GET /api/v1/invites/{code}/preview` needs no token and returns only `inviterName` (the inviter's display name) and `prefilledName` (what the inviter typed for the invitee).
- It answers only for an open invite. A code that is unknown, accepted, declined, revoked or expired gets `not_found` (DRT-1004), so a closed invite looks like one that never existed and a declined invite stays invisible.
- Nothing else is returned: no phone numbers, no ids, no relationship.
- No separate rate limit. The code is 72 random bits (`randomBytes(9)`), so guessing one by trying is not practical, and the link itself is the secret the inviter chose to share.
- The app keeps the preview in memory only; after sign-in the invite still reaches the person by their number (ADR 0011), and the consent screen (0f) opens as before.

## Consequences
- Anyone the link is forwarded to sees the inviter's name and the name typed for the invitee, as long as the invite is open. The link goes out from the inviter's own share sheet (WhatsApp, SMS), so whoever gets it directly already knows who sent it; only a forwarded link reveals something new.
- The inviter can end it at any time by revoking the invite (1g).

## Rejected
- **Keep 0b and the 0e prefill for later:** the invited person gets the plain welcome and has to type a name the inviter already typed.
- **Show the greeting only after sign-in:** no public endpoint, but 0b is meant to explain why to sign in at all.
- **A rate limit in the API:** needs shared state (a table or an external store) for no real gain with a 72-bit code.

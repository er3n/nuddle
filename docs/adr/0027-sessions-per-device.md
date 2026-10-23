# 0027 Our API keeps which device holds each Supabase session

Status: Accepted (2026-10-23). Refines ADRs 0011 and 0014; NUD-142.

## Context
On 2026-10-22 a server bug (NUD-139) handed one person's Supabase session to another phone. We could not tell which phone held which session: Supabase's `auth.sessions` sees only our API, so every row shows the Vercel IP and the user agent "node", and our `devices` table is not tied to a session. A sync naming a device that belongs to someone else touched nothing and left no trace. People also have no way to see where they are signed in or to end the other sessions.

## Decision
- **One row per session, in our own table.** `session_devices(session_id, person_id, device_id, app_version, created_at, last_seen_at, last_seen_via, revoked_at)`, keyed by the access token's `session_id` claim. `person_id` is the token's `sub`, with no foreign key: a session starts before `register` creates the `people` row (ADR 0014). `device_id` is our `devices.id`, also without a foreign key, since log out deletes the device row and the session row outlives it.
- **Written on verify, refresh, device registration and sync.** `auth/verify` and `auth/refresh` create or update the row from the new access token; their bodies take an optional `appVersion` and `deviceId`. `POST /devices` ties the caller's session to the device it registers and its app version. `GET /sync` with `deviceId` ties it too and moves `last_seen_at`. `last_seen_via` says which of `verify`, `refresh`, `device` or `sync` it was. A `deviceId` is only taken when it belongs to the caller.
- **Never IPs, user agents or tokens.** Only ids, the app version, the phone model the app reports with its device (`devices.model`, `Build.MANUFACTURER` and `Build.MODEL`) and times. The privacy notice names the model and the app version.
- **A device that is not the caller's is recorded.** When a request names a `deviceId` that does not belong to the caller (sync, `report_fired`, `log_out`, verify, refresh), a row goes to `foreign_device_uses(person_id, session_id, device_id, owner_id, via, at)`: ids only, `owner_id` null when no device has that id. The request still does nothing to that device, as before.
- **`GET /api/v1/sessions`** lists the caller's sessions that are not revoked and were seen in the last 30 days (TODO(open-questions#65)), newest first: device model, app version, last seen, and which one is the session making the call. Sessions are not listed by id.
- **`POST /api/v1/commands/sign_out_others`** (an idempotency key, as every command) ends every other session of the caller through the Supabase admin API (`auth.admin.signOut(token, "others")`), marks their rows revoked and sends the caller's devices a `sync` hint. The call to Supabase comes before the write; a replay returns the stored result without calling Supabase again.
- **A revoked session is refused at once.** The auth middleware answers 401 for an access token whose session row is revoked, so the other phone does not keep working for the rest of its token's hour. Its refresh then fails too, and the phone wipes itself (NUD-141).
- **Account deletion** deletes the person's `session_devices` and `foreign_device_uses` rows with the devices.

## Consequences
- Every authenticated request does one primary-key lookup for the revoked check; sync and device registration do one more write each.
- Sessions that existed before this change have no row until their next refresh or sync; until then they cannot be listed and are not refused by the middleware, but `sign_out_others` still ends them in Supabase.
- Rows of sessions that were never revoked and went quiet stay in the table until the account is deleted. Pruning them is left for later.
- The contract grows by two optional fields on the auth bodies, one on device registration, one route and one command (ADR 0008).

## Rejected
- **Reading `auth.sessions` directly:** it holds only our server's IP and user agent, test databases have no `auth` schema, and our API would read Supabase's internal tables.
- **Storing the IP or the user agent the phone would send:** not needed to tell devices apart once the device id is tied to the session, and more personal data to explain.
- **A device id in a header on every request:** the session is tied on the few calls that already carry a device id or start a session; a header would touch every call and the generated client.
- **Deleting the other sessions' rows instead of marking them revoked:** the middleware could no longer refuse their access tokens for the rest of the hour.

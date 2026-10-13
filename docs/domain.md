# Domain

Entities, task states, timing rules and poke mechanics. Product intent is in `product.md`, architecture in `architecture.md`.

Items marked **(proposed)** are reasonable defaults that were not explicitly decided. Implement them, but keep them easy to change and list any change in `open-questions.md`.

## Entities

| Entity | Table | Notes |
|---|---|---|
| Person | `people` | id, phone (E.164, unique), display name, created/deleted timestamps, quiet hours (default 23:00-07:00, stored as local times + IANA time zone), the two human channel switches (both default `true`). The user is a person. |
| Connection | `edges` | Directed pair (from, to) with `type` (`spouse`, `parent`, `child`, `sibling`, `roommate`, `other`) and learned statistics/weights. A 1:1 connection is two directed edges created together, because each side picks its own relationship ("Sibel senin neyin?") and weights are per direction. Status: `pending` (invite out), `active`, `removed`. `notify_me` (default `true`, **proposed**) is the from-person's switch on 5b, "Sibel'in işleri için bana haber verilebilir": the from-person may be told about the to-person's tasks. It narrows the person's own "Başkalarının işleri için bana haber verilebilir" switch for this one connection. |
| Invite | `invites` **(proposed table)** | Inviter, invitee phone, chosen relationship, prefilled name, code for the link, status (`open`, `accepted`, `declined`, `revoked`, `expired`). Declining is stored but never shown to the inviter. |
| Task | `tasks` | Sender, recipient (may equal sender), title, detail (e.g. amount), deadline, finish mode (`done` or `photo`), raw text the sender typed, status, plan version. |
| Task event | `task_events` | Append-only log of everything that happens to a task. It is also the sync feed (see `architecture.md`). |
| Poke step | `poke_steps` **(proposed table)** | The plan: ordered steps with planned time and level, versioned per task. Never exposed to clients except the recipient's device, which needs times and levels to set alarms. |
| Scheduled action | `scheduled_actions` | Server-side timers: deadline check, approval timeout, human channel check, lost poke check, missed card expiry. Executed by `tick(now)`. |
| Device | `devices` **(proposed table)** | Person, Firebase Installation ID (FID, ADR 0015), platform, app version, last seen, permission summary (notifications, exact alarm, full screen, channel states). Source of reachability. |
| Idempotency key | `idempotency_keys` **(proposed table)** | Command key, caller, stored result. Replayed commands return the stored result (ADR 0006). |
| Photo | Supabase Storage + row on the task event | Private bucket, path per task. Deleted when the task closes. |

There is no group, household or role table, and there must never be one (ADR 0010).

## Task states

Server statuses: `SCHEDULED`, `AWAITING_APPROVAL`, `REJECTED`, `DONE`, `DROPPED`, `MISSED`, `CANCELLED`.
Client-only status: `QUEUED` (written offline, not yet on the server).

| From | Event | To | Who |
|---|---|---|---|
| (new) | create | `SCHEDULED` | sender |
| `SCHEDULED` | done (finish mode `done`) | `DONE` | recipient |
| `SCHEDULED` | photo sent (finish mode `photo`) | `AWAITING_APPROVAL` | recipient |
| `SCHEDULED` | can't today | `DROPPED` | recipient |
| `SCHEDULED` | deadline passed | `MISSED` | server |
| `SCHEDULED` | cancel | `CANCELLED` | sender |
| `SCHEDULED` | edit (deadline, detail, finish mode) | `SCHEDULED`, plan regenerated | sender |
| `AWAITING_APPROVAL` | approve | `DONE` | sender |
| `AWAITING_APPROVAL` | 3 hours without approval | `DONE` (auto) | server |
| `AWAITING_APPROVAL` | re-request (optional note) | `REJECTED` | sender |
| `AWAITING_APPROVAL` | cancel | `CANCELLED` | sender |
| `REJECTED` | photo sent | `AWAITING_APPROVAL` | recipient |
| `REJECTED` | can't today / deadline passed / cancel | `DROPPED` / `MISSED` / `CANCELLED` | as above |
| `MISSED` | "Sonradan yaptım" | `DONE` with `late = true` **(proposed)** | recipient |
| `MISSED` | photo sent that was taken before the deadline (`takenAt`), upload delayed (6e) | `AWAITING_APPROVAL` | recipient |
| `MISSED` | done tapped offline before the deadline (`doneAt`), sent late (6a) | `DONE` | recipient |
| `MISSED` | "Süreyi uzat" (new deadline) | `SCHEDULED` | sender |
| `MISSED` | cancel | `CANCELLED` | sender |
| `DROPPED` | "Yeni zaman ver" (new deadline) | `SCHEDULED` | sender |
| `DROPPED` | "Ben hallederim" | `CANCELLED` with reason `sender_handles` **(proposed)** | sender |
| any status but `DONE` and `CANCELLED` | either side deletes the account | `CANCELLED` with reason `account_deleted`, the other side is told | server |
| any status but `DONE` and `CANCELLED` | either side removes the connection (5b) | `CANCELLED` with reason `connection_removed`, no notification to the other side **(proposed)** | server |
| `SCHEDULED`, `REJECTED` | a setting the plan depends on changed (quiet hours, a human channel switch) | same status, plan regenerated from the level already reached **(proposed)** | server |

Invariants:
- A task awaiting approval can never become `MISSED`. The deadline applies to sending the photo, not to approval.
- The deadline counts from the send attempt (docs/product.md, Errors): `photo_sent` and the upload URL carry `takenAt`, the moment Gönder was tapped on the device. A missed task takes a photo whose `takenAt` is at or before the deadline and not after the server's now; a later one is refused. `done` carries `doneAt` the same way: a Bitti tapped offline before the deadline closes a missed task as done (not late) when it reaches the server.
- Every transition stops or regenerates the plan in the same transaction and appends a `task_events` row.
- A task to self has the same sender and recipient. Photo mode for self tasks: approval is skipped and the photo closes the task **(proposed)**.
- Tasks cannot be written to a pending (not yet joined) person.
- If the camera permission is denied, a photo task falls back to plain "Bitti".
- The state machine is one exhaustive `switch` over a union type in TS and a `sealed` hierarchy in Kotlin. Adding a state must break the build everywhere it is not handled.

UI mapping of each status (pill text, color, what each side sees) is in `ux/screens.md`.

## Time rules

All of these live in pure functions that receive `now` from an injected `Clock` (ADR 0009).

| Rule | Value |
|---|---|
| Quiet hours | Default 23:00-07:00 in the recipient's time zone, editable. No channel fires inside the window, full screen included. Checked when planning on the server and again on the device right before ringing. |
| Deadline inside quiet hours | The plan moves pokes earlier so the last one lands before quiet hours start. |
| Auto-approve | 3 hours after the photo is sent. |
| Missed card | Removed from the recipient's list 1 day after `MISSED`. |
| Undo toast | 5 seconds after send, 10 seconds when TalkBack is on. Timeout confirms the task. |
| Done note | 4 seconds. |
| OTP | 6 digits. 3 wrong codes per phone, the remaining ones shown, then a 15 minute wait in which no code is checked or sent (open-questions #41). A sign-in clears the count; a new code does not. Resend countdown on the code screen. |
| Social permission request | Same permission, same requester: at most once per week. |
| Snooze | Limited count, the last one is labeled "son hakkın". Count and lengths are open (design shows 15 min in the notification and 10 min on full screen). |
| Lost poke | Planned time + grace with no fired report while the device was seen online counts as lost **(proposed grace: 5 minutes)**. |
| Human channel trigger | Deadline is close and the full screen step went unanswered. Exact threshold is part of the plan heuristic. |
| Invite expiry | Open question. |

## Poke levels

| Level | Channel | Needs |
|---|---|---|
| `SILENT` | Silent notification. Only ever the first step, never alone. | Notifications |
| `SOUND` | Alarm audio stream, `FLAG_INSISTENT`, rings until silenced, pierces Do Not Disturb. | Notifications |
| `FULL_SCREEN` | `PokeActivity` over the lock screen with alarm sound. | Notifications + full screen intent + exact alarm |
| `HUMAN` | "Eren'e ulaşamıyorum" notification to the sender (MVP) or later another connection. | Server side only |

If full screen is not permitted, `FULL_SCREEN` silently degrades to `SOUND` on the device and the event is reported as `permission_missing`.

## Poke plan

- Generated on the server by a pure function, roughly `planPokes(task, recipient, reachability, history, now) -> PokeStep[]`.
- Constraints: escalation is monotonic (never drops a level within a plan), no step inside quiet hours, `SILENT` is never the only step, the last device step lands before the deadline, a `HUMAN` step may follow when the deadline is close.
- The MVP heuristic (step count, spacing, when to escalate) is designed in bootstrap stage 3 with scenario tests. Learning from history comes later; the function signature already takes history so learning does not change callers.
- Every regeneration bumps the plan version. Devices ignore steps from older versions.
- In E2E mode the plan is compressed to seconds (see `testing.md`).

### Poke plan v0

The MVP heuristic, implemented in `apps/web/src/domain/plan-pokes.ts` (open-questions #18). It ignores history; learning replaces it later without changing callers.

1. **Window end:** the deadline, or the start of quiet hours if the deadline falls inside them.
2. **Window:** the last 60 minutes before the end. It never starts before now. If it overlaps quiet hours it starts when they end; if less than 2 minutes would remain (a deadline at 07:00), the window moves before quiet hours instead.
3. **Ladder** at nominal offsets before the end: `SILENT` -60 min, `SOUND` -30, `FULL_SCREEN` -15, `HUMAN` -5. A shorter window shrinks every offset proportionally.
4. **Resume:** after a re-request the plan starts at the highest device level that already fired (no new `SILENT`).
5. **`HUMAN`** is planned only when someone else can be told: not a task to self, both human channel switches allow it (the recipient's "Benim işlerimde tanıdıklarımı kullanabilirsin", the sender's "Başkalarının işleri için bana haber verilebilir"), and the sender's `notify_me` on their edge to the recipient is on. It is always the last step. The server fires it through a `human_check` timer only if the task is still open, and it does not stop poking.
6. **Very short window** (under 2 minutes): a single `SOUND` step, no `HUMAN`.
7. **No time left** outside quiet hours before the deadline: no steps; the deadline timer still marks the task missed.
8. Reachability does not remove steps: a level the device cannot reach stays in the plan and degrades on the device.

Each device step also gets a `lost_poke_check` timer at planned time + 5 minutes.

## Device execution

1. Server sends the plan to the recipient's device as a high priority FCM data message and also exposes it through sync.
2. The device stores steps in Room and sets one `AlarmManager.setAlarmClock()` per step. A step that arrives up to 2 minutes after its planned time rings at once (the first step of a plan is planned at the moment the task is written, NUD-41); an older one is not rung late, and the lost poke check covers it.
3. On `BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`, `TIME_SET` and `TIMEZONE_CHANGED`, all pending alarms are re-set from Room.
4. When an alarm fires, a receiver re-checks quiet hours and plan version, shows the notification for the level, and for `FULL_SCREEN` opens `PokeActivity` via `setFullScreenIntent` (`showWhenLocked`, `turnScreenOn`).
5. Every fired step reports planned and actual time to the server.
6. Done, Snooze and Can't today are applied locally first (remaining alarms cancelled immediately, even offline), then sent to the server with WorkManager. Done carries the moment it was tapped (`doneAt`).
7. A cancelled task can still ring if the cancel push is delayed. Accepted edge case in the MVP; the fired report lets the server answer with a cancel and the ring stops on the next sync.

## Reachability

- The device sends its permission summary on every launch and whenever a permission or notification channel changes. A channel disabled in system settings counts as a missing permission.
- Server derives the highest level it can reach the person with: `FULL_SCREEN`, `SOUND`, `SILENT` or `NONE` (logged out, notifications off).
- The sender sees one sentence, never a setting name: "Eren'e şu an en fazla tam ekranla / sesli bildirimle / sessiz bildirimle ulaşabilirim."

## Task events (proposed vocabulary)

`created`, `edited`, `plan_generated`, `poke_fired` (planned, actual, level), `poke_lost`, `snoozed`, `done`, `photo_sent`, `approved` (manual or auto), `re_requested` (note), `dropped` (note), `missed`, `cancelled` (reason), `human_notified`, `permission_missing`.

## LLM task extraction

- One call per written text. Input: the raw text, the sender's local time and time zone, and the sender's connection subgraph (names, relationship types). Output (structured): recipient, title, detail, deadline, suggested finish mode, and a confidence or missing-field list.
- Confident result: create the task directly and show the undo toast. Missing or ambiguous: open the preview and ask exactly one question.
- Example: "eren yarın 9dan önce temizlikçi için 1000 çeksin" becomes recipient Eren, title "Temizlikçi için para çek", detail "1.000 TL", deadline tomorrow 09:00, finish mode photo (money task).
- Provider OpenRouter, model `deepseek/deepseek-v4.1-flash` (set in `OPENROUTER_MODEL`). No model comparison in the MVP; the eval set guards prompt quality. The extractor sits behind a `TaskExtractor` interface with a fixture implementation for tests.

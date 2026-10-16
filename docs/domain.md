# Domain

Entities, task states, timing rules and poke mechanics. Product intent is in `product.md`, architecture in `architecture.md`.

Items marked **(proposed)** are reasonable defaults that were not explicitly decided. Implement them, but keep them easy to change and list any change in `open-questions.md`.

## Entities

| Entity | Table | Notes |
|---|---|---|
| Person | `people` | id, phone (E.164, unique), display name, created/deleted timestamps, quiet hours (default 23:00-07:00, stored as local times + IANA time zone), the two human channel switches (both default `true`), the poke profile with when it was written and how many closed tasks it had seen (ADR 0020), the profile picture's path in the private bucket (`avatar_path`, NUD-72; the file goes with the account). The user is a person. |
| Connection | `edges` | Directed pair (from, to) with `type` (`spouse`, `parent`, `child`, `sibling`, `roommate`, `other`) and learned statistics/weights. A 1:1 connection is two directed edges created together, because each side picks its own relationship ("Sibel senin neyin?") and weights are per direction. Status: `pending` (invite out), `active`, `removed`. `notify_me` (default `true`, **proposed**) is the from-person's switch on 5b, "Sibel'in işleri için bana haber verilebilir": the from-person may be told about the to-person's tasks. It narrows the person's own "Başkalarının işleri için bana haber verilebilir" switch for this one connection. |
| Invite | `invites` **(proposed table)** | Inviter, invitee phone, chosen relationship, prefilled name, code for the link, status (`open`, `accepted`, `declined`, `revoked`, `expired`). Declining is stored but never shown to the inviter. |
| Task | `tasks` | Sender, recipient (may equal sender), title, detail (e.g. amount), deadline, timing (`by`: done by the deadline; `at`: a moment, deadline is the moment plus a grace; ADR 0017), finish mode (`done` or `photo`), raw text the sender typed, status, plan version, and for a repeating task its rule and the series id its occurrences share (ADR 0021). |
| Task event | `task_events` | Append-only log of everything that happens to a task. It is also the sync feed (see `architecture.md`). |
| Poke step | `poke_steps` **(proposed table)** | The plan: ordered steps with planned time and level, versioned per task. Never exposed to clients except the recipient's device, which needs times and levels to set alarms. |
| Scheduled action | `scheduled_actions` | Server-side timers: deadline check, approval timeout, human channel check, lost poke check, missed card expiry. Executed by `tick(now)`. |
| Device | `devices` **(proposed table)** | Person, Firebase Installation ID (FID, ADR 0015), platform, app version, last seen, permission summary (notifications, exact alarm, full screen, channel states). Source of reachability. |
| LLM call | `llm_calls` | One extraction or planner call: the person, the task once there is one, the input, the prompts as sent, the answer, the outcome, the error and the latency (ADR 0019). Never exposed to clients; deleted with the account. |
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

## Repeating tasks

A repeating task (ADR 0021) is a series of one-off occurrences: the rule (`every` day, given ISO weekdays, or weekdays, at a local `time`) sits on every occurrence with the series id, and only one occurrence is open at a time. When an occurrence closes as done, approved, missed or dropped, the next one is created at once at the rule's first time after the closed one's own time (after now when it closed late), with the same title, detail, timing, grace, lead and finish mode. The sender's cancel ends the series; so does a removed connection or a deleted account. The extractor turns "her gün 9'da", "her salı", "pazartesi ve perşembe", "hafta içi her sabah" into the rule, and the server sets the first occurrence to the rule's next time after now (never a question about the time); anything more often than once a day gets a reply instead.
## Recipient's start

The recipient may move when the pokes begin without touching the sender's deadline (`set_start`, NUD-78): a time between now and the task's time. On an `at` task it becomes the lead (the moment minus the start, within the lead limit); on a `by` task it is stored as `poke_from`, the earliest the plan window may open, with quiet hours still applying. A new plan version follows at once (v0, then the LLM with `startAt` from it) and `task_events` gets `start_set`. The sender is not told; the deadline and the missed rule are unchanged.
## One-time messages

A message to pass on with nothing to do ("erene 3'te seni seviyorum de", "baban seni alabilir de") is a task with finish mode `notice` (NUD-79): the title is the message, timing `at`, the time given or the next five-minute mark, grace 60 minutes. Its plan is one SOUND step at the time (at once when the time is within five minutes): no escalation, no HUMAN, no LLM plan. "Tamam" (the done command) closes it; the deadline passing closes it as done, never missed; snooze, can't today and a photo are refused (`wrong_finish_mode`). The sender sees the delivery ticks as for any task.

## Time rules

All of these live in pure functions that receive `now` from an injected `Clock` (ADR 0009).

| Rule | Value |
|---|---|
| "Yarın" after midnight | Between 00:00 and 05:00 in the sender's time zone, "yarın" means the day that starts when they wake up, which is today's date (NUD-68). The extractor applies it; the 2b chips and the detail keep calendar days. |
| Quiet hours | Default 23:00-07:00 in the recipient's time zone, editable. No channel fires inside the window, full screen included, for a `by` task. An `at` task's own window (from its start to its deadline) is exempt: the sender asked for that time, so a 06:30 wake-up rings at 06:30 (NUD-70, open-questions #47). Checked when planning on the server and again on the device right before ringing. |
| Lead | An `at` task may carry a lead (`lead_minutes`, 0 to 6 hours): how long before the moment the person must start getting ready or travelling. The extractor reads it from the task ("diş randevusu" about 45, "uçak" about 150, "yürüyüş 20 dakika sürüyor" 20; 0 for something done on the spot). Poking starts at the moment minus the lead; the moment and the deadline stay what the person sees (NUD-70). |
| Deadline inside quiet hours | The plan moves pokes earlier so the last one lands before quiet hours start. |
| Auto-approve | 3 hours after the photo is sent. |
| Missed card | Removed from the recipient's list 1 day after `MISSED`, or at once when the recipient taps "Kaldır" on it (NUD-49). Removing only hides it on the recipient's side: the task stays `MISSED`, the sender still sees it. |
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

- Planned by an LLM (`PokePlanner` port, ADR 0017) from the task, the people and their relationship, the recipient's connections, the recipient's **profile** (a rolling LLM-written summary of how they respond to pokes, ADR 0020) and their last 8 closed tasks (how each ended, which pokes rang, which one woke them, how fast they acted). The profile and the history are what make the plan learn.
- Every LLM plan passes a pure rule check (`checkPlan`) before it is stored. The rules: steps in time order, not before `now` and not after the deadline, for an `at` task not before its start (the moment minus the lead), no step inside quiet hours except inside an `at` task's own window, escalation never drops a level, `SILENT` is never the only step, at most 8 device steps, at least 3 minutes between two device steps. At most one `HUMAN` step, only when allowed (rule 5 below), after the last device step and within the last hour before the deadline: one the model puts earlier is moved there, so the sender is told late rather than not at all (NUD-70). Any other breaking step is dropped; a plan with no valid device step is rejected.
- **Poke plan v0** (below) is the first plan and the fallback: writing a task stores v0 at once, the planner runs after the response and its checked plan replaces v0 as the next version. Planner error, a 10 s timeout or a rejected plan keep v0. The same on every regeneration.
- Every regeneration bumps the plan version. Devices ignore steps from older versions.
- In E2E mode the plan is compressed to seconds (see `testing.md`).

### Poke plan v0

The MVP heuristic, implemented in `apps/web/src/domain/plan-pokes.ts` (open-questions #18). It ignores history; learning replaces it later without changing callers.

For an `at` task the ladder starts at the task's start (the moment minus the lead) instead of ending at the deadline: `SOUND` at the start, `FULL_SCREEN` 5 and 20 minutes after it, `HUMAN` 30 minutes after it or 5 minutes before the deadline when that is sooner, all before the deadline and regardless of quiet hours (open-questions #43, #47). The rules below describe `by` tasks.

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

`created`, `edited`, `plan_generated`, `poke_fired` (planned, actual, level), `poke_lost`, `snoozed`, `done`, `photo_sent`, `approved` (manual or auto), `re_requested` (note), `dropped` (note), `missed`, `cancelled` (reason), `human_notified`, `permission_missing`, `delivered`, `seen`.

`delivered` and `seen` are the sender's tick (NUD-68). The recipient's phone sends the `acknowledge` command once a sync stored an open task to them (`seen: false`) and once the task was on screen there (`seen: true`, ux/screens.md, Quality pass). The server logs each at most once per task, `seen` adds `delivered` when it is missing, and the sender's phone syncs. Nothing is logged for a task to self or a closed task. Neither changes the status, the plan or a timer.

## LLM task extraction

- One call per written text. Input: the raw text, the sender's local time and time zone, and the sender's connection subgraph (names, relationship types). Output (structured): recipient, title, detail, deadline, timing, suggested finish mode, and a confidence or missing-field list.
- **Timing** (ADR 0017): `by` for "9'a kadar", "akşama kadar", "2 saat içinde", "cumaya kadar", and for an hour when something else happens that the task must precede: "en geç 7de", "7de işe gitmeden önce", "6da çıkmadan önce" (NUD-71); `at` for a moment: "2 dakika sonra", "9'da ilacını iç", "beni 8'de uyandır", "hatırlat". For `at` the extracted time is the moment, and `missAfterMinutes` says how long after it the task still makes sense (a glass of water 30, a medicine 120); the stored deadline is the moment plus that, kept between 15 minutes and 12 hours, 60 when missing (open-questions #43). The preview shows "18:42'de" or "18:42'ye kadar" and the sender can switch it.
- Confident result: create the task directly and show the undo toast. Missing or ambiguous: open the preview and ask exactly one question.
- Not a task at all (a greeting, thanks, small talk, a question about the app): the extractor returns a one-sentence `reply` instead, `write_task` answers kind `chat`, nothing is created and the phone shows the reply above the compose bar (NUD-52). A short or unclear request ("ekmek", "yarın") is still a task with a question, never a reply.
- The sender waits for this call, so the model runs without reasoning (about 1.5 s instead of 7-10 s, NUD-52); the eval guards the accuracy.
- Example: "eren yarın 9dan önce temizlikçi için 1000 çeksin" becomes recipient Eren, title "Temizlikçi için para çek", detail "1.000 TL", deadline tomorrow 09:00, finish mode photo (money task).
- Provider OpenRouter, model `deepseek/deepseek-v4.1-flash` (set in `OPENROUTER_MODEL`). No model comparison in the MVP; the eval set guards prompt quality. The extractor sits behind a `TaskExtractor` interface with a fixture implementation for tests.

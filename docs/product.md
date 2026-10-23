# Product

Source of truth for what we are building and why. Decided on 2026-10-10 and 2026-10-11.
If code, design or another doc disagrees with this file, this file wins until it is changed.

## Promise

Someone says it once. The person who forgets gets poked until the job is done.
The system learns how each person responds and pokes them better over time.

Turkish UI tagline: "Bir kere söylenir. Gerisini dürt halleder."

## Principles

- **Simple on day one, smart over time.** Features are not shown up front. They appear as the system learns the person.
- **Three concepts in the MVP:** person, task, poke. Concepts such as "backup poker", "household" or "role" never appear in the UI as concepts.
- **The poke plan is hidden.** Neither the poked person nor the sender sees the plan. Learning is revealed only after the fact ("This time I caught you on the 2nd poke").
- **Never a scorecard.** Counts are kept only to poke better. They are never shown to anyone as a report card.
- **Consent is social.** Being pokeable by someone is a yes/no the poked person gives. Declining is never reported to the other side.
- **The app informs, it never makes people call.** It can tell the sender "I can't reach Eren, a call would help", it never phones anyone by itself in the MVP.

## Names and identifiers

| Thing | Value |
|---|---|
| Global brand | **Nuddle** (nudge + cuddle, invented word). Beta is ~20 families, then straight to global. |
| Turkey | Brand **dürt** on Turkish devices. Same app, same code, only the localized store name differs. |
| Play store name | `Nuddle: Reminders That Nag` (en-US, the default listing) and `dürt: Hatırlatıcı ve Alarm` (tr-TR). The part after the colon carries search keywords; the brand stays Nuddle / dürt everywhere else (decided 2026-10-16). |
| Domain | `nuddle.app` (canonical). `durtmobil.com` redirects to it. `nuddle.com` is taken. `durt.app` is free if wanted. |
| Android `applicationId` | `com.dokukoza.nuddle` for the Play production app (ADR 0028). `app.nuddle` is the earlier testing app on the dokukoza@ Play account. Kotlin `namespace` stays `app.nuddle`. Debug builds use `app.nuddle.debug`. Permanent after the first Play upload. |
| GitHub | public monorepo `er3n/nuddle` on the personal account (see ADR 0001). The `nuddle-app` org is kept as an option for a later transfer. |
| Rejected names | Poke (poke.com, funded AI assistant in the same space), "dürt" everywhere ("dirt", the ü problem), nudge, bump, prod, jog, tug, elbow, psst (domains taken), "Done Yet" (a very similar "Done?" app exists on Android). |

In code and docs the domain verb is **poke** (`Poke`, `PokeLevel`, `PokeActivity`). Never `Durt` or `Nudge` in identifiers except the theme objects already named in the design handoff (`DurtTheme`, `DurtButton` etc. may be renamed to `Nuddle*` in stage 4, see `docs/open-questions.md`).

## MVP scope

### Identity and sign-in
- The phone number is the only identity. Sign-up and sign-in are the same flow: number, code, the server decides new or existing.
- OTP: WhatsApp authentication template first, SMS as fallback: at once when WhatsApp cannot deliver, after 12 seconds without delivery, or when the person taps "SMS ile gönder". A number without WhatsApp gets SMS directly for 30 days (ADR 0029). The WhatsApp sender is named Nuddle in every market.
- Name is asked only on first sign-up. If an inviter typed a name, it comes prefilled.
- No social login (so Apple's Sign in with Apple requirement is never triggered).
- **Log out:** pokes stop ringing on that phone, the other side sees the person as unreachable. Data is kept and returns on the next sign-in with the same number.
- **Delete account:** the phone number, name and profile picture, connections and invites, tasks, photos, learned poke behavior and the LLM call records are deleted (the privacy notice promises this, NUD-85): the identity at once, the tasks a week later so the other side syncs the cancels (`domain.md`, Account deletion; NUD-89). The other side's open tasks are cancelled and they are told. Irreversible.

### Connections (graph)
- The only building block is a 1:1 connection. There is no group data structure. Assignments like "one of you", "the kids" are derived from the sender's own connection list.
- Under the hood it is a graph: people are nodes, connections are edges. An edge has a type (spouse, parent, child, sibling, roommate, other), a direction and weights learned from behavior.
- Invites are always 1:1. The inviter must choose the relationship, nothing is preselected. The invitee sees it on the consent screen and can change it.
- The relationship never grants permissions. It is an input to the AI's "whom to tell, when" decision. All channels are on by default.
- An invite is bound to a phone number (single contact pick, no contacts permission). Even if the link is lost, the invite lands in the person's notifications when they register with that number.
- Sharing uses the system share sheet. The UI says "shared", never "sent".
- Consent comes after sign-in: "Sibel seni dürtebilsin mi?" (Can Sibel poke you?) with "İzin ver" / "Şimdi değil". Declining is never reported.
- A pending (not yet joined) person cannot be given tasks.
- MVP is 1:1. Graph logic (inference, weights, third parties) is written when a third person arrives. The data model is a graph from day one.

### Human channel (formerly "backup poker")
- No separate role or choice. Every connection is a poke channel by default.
- The AI decides whom to tell for which task. Rules: only the poked person's own connections, and only where relationship and content fit.
- Two switches in the profile, both on by default:
  - "Benim işlerimde tanıdıklarımı kullanabilirsin" (You may use people I know for my tasks)
  - "Başkalarının işleri için bana haber verilebilir" (I may be told about other people's tasks)
- The app cannot make anyone call. Copy reflects that: "I'll let them know, they will probably call you".
- In the MVP (1:1) the human channel is the sender: when the deadline is close and other pokes failed, the sender gets "Eren'e ulaşamıyorum, bir arasan iyi olur" with Call, Message, View. Poking continues.

### Writing a task
- One input box. Who, what and when are extracted from free text by the LLM. "To whom" is never asked while typing.
- Voice input is in the MVP: a tap on the mic dictates into the box (open-questions #40, NUD-48).
- Share to Nuddle (NUD-127): text shared from another app (a WhatsApp message, a school e-mail) opens home with it in the box, after anything already typed. It is never sent by itself; the person edits it and sends it, and the confident / preview rules below apply. Signed out, the text waits through sign-in. An image shared the same way (NUD-138, the real WhatsApp path, since WhatsApp offers the system share for media only) lands in the box as the attached example photo below, with its text, if any, in the box. The app never reads WhatsApp on its own (see "Deliberately out of the MVP").
- **Example photo** (NUD-138): the sender may attach one picture that shows what to do ("fotodakileri al", a shopping list, the right medicine box), from the gallery next to the box or by sharing an image to dürt. Optional, one per task, and the text is still what is understood: the picture is not read (open-questions #63). It goes out with the task on both the direct and the preview path, and is uploaded with retries like a photo. The recipient sees it on the task, on the full screen poke and in the poke notification. It is never proof (see Photo and approval).
- If the AI is confident, the task goes out immediately and a 5 second "Undo / Edit" toast appears. A preview opens only when information is missing or ambiguous, and it asks at most one question at a time.
- A photo is never mandatory. For suitable tasks (money, payments) it is suggested and preselected, one tap changes it.
- Tasks to yourself exist. A user without any connection can still use the app.
- Repeating tasks ("her gün 9'da", "her salı", "hafta içi") are in, as a series of one-off occurrences (ADR 0021).

### Sender side and task lifecycle
- The sender sees an ongoing task's detail: status, reachability level, recipient, deadline, detail, how it should finish. Tapping a row edits it; the change reaches the recipient with a short note.
- **Cancel:** the sender can cancel any time, pokes stop immediately. Reason is optional ("Ben hallettim", "Gerek kalmadı"). The recipient sees "Sibel ... iptal etti" on their home screen.
- **Done without photo:** no separate push to the sender, it sits as text in the bell. Photo tasks go to approval.
- **"Bugün yapamayacağım" (Can't today):** optional note, preset choices or free text, "Notsuz kapat" (close without note) always available.
- **Missed:** shown to the recipient as a "Kaçırıldı" card with "Sonradan yaptım" (I did it later) and "Sibel'i ara" (Call Sibel). Removed from the list after one day.
- A poke notification has three actions: Done, Snooze, Not today.

### Poking
- Plan is hidden (see Principles). The sender only sees reachability: "Eren'e şu an en fazla sesli bildirimle ulaşabilirim" (Right now I can reach Eren at most with a sound notification).
- Channels escalate step by step: notification, sound, full screen (MVP). SMS and voice call / IVR (Premium). Human channel.
- The sound step uses the alarm audio stream and rings until silenced. It needs only the notification permission and pierces Do Not Disturb by default. A silent notification is only ever the first step, never used alone.
- The sound step (and full screen, which rings the same way) also says the task aloud on the phone (NUD-124): "Sibel diyor ki: ilacını iç" for a task from someone else, only "İlacını iç" for a task to self (pattern in `glossary.md`). It is part of the sound step, not a later level. It speaks a few times between the tone, in a Turkish voice; a phone without a Turkish voice rings the tone only, as before. Never before the first unlock after a reboot, since the task cannot be read then. On for everyone, no switch in the MVP (open-questions #56).
- MVP triggers are time based. Location triggers are a later phase.
- **Quiet hours** default 23:00 to 07:00, editable in the profile. No channel works in that window. A task whose deadline falls inside it is poked earlier.
- Full screen actions: Done (closes directly), Snooze (limited, the consequence is stated), Can't today (closes directly, reason optional, recorded as "dropped", not counted as a failure).

### Photo and approval
- Photos from the in-app camera (either lens) or picked from the gallery (decided 2026-10-15, open-questions #48). They stay between the two people and are deleted when the task closes.
- The sender's example photo (Writing a task) is a different thing and never proof: it does not close the task, does not start approval, and a photo task still needs the recipient's own photo. It follows the same privacy rules: only the two people see it, it is deleted when the task closes and with the account.
- Sending a photo stops poking, the task becomes "awaiting approval".
- The sender approves. They can ask again with a note. After a re-request, poking resumes where it stopped.
- If approval is forgotten, the task auto-approves after 3 hours. The deadline applies to sending the photo, not to the approval.
- AI pre-check of photos is out of the MVP.

### Permissions and setup
- **When:** for an invited user, right after "İzin ver" on the consent screen (social consent and technical permission are the same decision). For a self-only user, at their first task. On an install that never ran setup (a reinstall, or the move from a sideloaded build to Play), once someone is connected (NUD-103).
- **Show first:** "Bir dene" plays a sample full screen poke after 3 seconds (labeled "DENEME"), then settings follow. "Denemeden kur" skips the demo.
- **Steps:** notifications (system dialog, cannot be skipped), alarms and reminders (exact alarm, labeled "Gerekli", effectively mandatory; skipping only via a small link "Alarmsız devam et, dürtmeler geç çalabilir"), full screen.
- **No battery setting in setup.** On stock Android `setAlarmClock` is not held back by Doze. Only aggressive OEM managers (Xiaomi, Huawei, Oppo, Vivo, partly Samsung) cause trouble.
- **Missed-ring detection:** every alarm reports planned vs actual time. If no report arrives while the device is online, the poke counts as lost. On launch `ApplicationExitInfo` detects force stops. When a loss is detected the bell shows "Son dürtmen çalmadı" with brand specific steps.
- On every launch a silent check runs. Missing permissions show in the bell, not on the home screen, and state their concrete effect.
- Reachability is stored on the server. The sender sees it as a level, never as a setting name.
- A disabled channel stays in the plan as "permission needed". When someone wants to poke harder, a social request goes out ("Sibel'in dürtmesi tam ekran gelebilsin diye..."), at most once a week per permission.

### Bell and communication
- The bell is the in-app inbox: invites, approvals, permission warnings, pokes that did not ring, finished tasks, "can't reach" news, event history.
- No in-app chat. Cards have Call and Message icons. Message opens the system app chooser. WhatsApp is not assumed. A one-time message ("sibele 3'te seni seviyorum de", NUD-79) is not chat: it has a time, is delivered once as a poke, and ends with "Tamam" or when its time passes.

### Error states
- No internet: pokes still ring locally, changes are queued and sent when the connection returns.
- Send failed: typed text is never lost, "Tekrar dene".
- Wrong code: remaining attempts are stated, then a waiting period (SMS pumping protection).
- Invalid invite: used or expired, a new link can be requested.
- Photo upload failed: kept on the phone, retried automatically, the deadline counts from the send attempt.
- Generic error: says installed pokes will still ring, shows an error code.

## Deliberately out of the MVP

WhatsApp notification listening, widgets, AI photo check, location triggers, group concept, role based permissions, showing the poke plan, in-app chat, a separate dispute flow, human channel to a third person (comes with graph logic), setting alarms in the system Clock app, dark theme, iOS.

## Premium (later, not designed)

SMS and voice call / IVR channels, task by e-mail. Price and scope are open.

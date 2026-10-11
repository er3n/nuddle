# Open questions

Things not yet decided. If you hit one while building, do not decide silently: pick the option marked *default*, leave a `TODO(open-questions#N)` comment, and mention it in the PR.

## Product

1. **Snooze limits.** How many snoozes per task and how long? Design shows 15 min (notification) and 10 min "son hakkın" (full screen). *Default:* 2 snoozes, 15 then 10 minutes, never past the deadline.
2. **Invite expiry and cancel rules.** *Default:* invite open until accepted, declined or revoked by the inviter; no expiry in the beta.
3. **Plan visibility in 4g.** "Dürtme kaldığı yerden devam ediyor · sıradaki 08:40" reveals the next poke time, which contradicts the hidden plan rule. *Default:* drop the time, keep "Dürtme kaldığı yerden devam ediyor".
4. **Statistics on person detail (5b).** "Zamanında biten %88" is a number both people see. Check against "never a scorecard". *Default:* show only "Birbirinize yazdığınız N görev", hide the percentage.
5. **Setup copy for self-only users (2c).** Current copy mentions Selin. *Default:* "İşlerini kaçırmaya başlarsan böyle dürterim."
6. **Voice input cancel gesture.** Component spec says slide up, screen 4h says slide left. *Default:* slide left (matches the screen).
7. **Self tasks with photo mode.** *Default:* the photo closes the task, no approval step.
8. **"Ben hallederim" on a dropped task (5d).** *Default:* closes as `CANCELLED` with reason `sender_handles`.
9. **"Sonradan yaptım" after missed.** *Default:* `DONE` with `late = true`.
10. **Premium price and scope** (SMS, voice call / IVR, task by e-mail). Not needed for the MVP.

## Brand and naming

11. **In-app logo and English UI.** Store name is "dürt" in Turkey and Nuddle elsewhere. Does the in-app logo follow the locale ("dürt." vs "nuddle.")? *Default:* follow the locale via a string resource.
12. **Theme and component prefix.** Handoff uses `DurtTheme`, `DurtButton`. *Default:* keep `Durt*` in the MVP, rename in one PR before the global launch if wanted.
13. **Invite link host and path.** Handoff used `durtmobil.com/d/{inviteCode}`. *Default:* `https://nuddle.app/i/{inviteCode}` as the App Links host, with `durtmobil.com` redirecting.
14. **Landing page.** Canvas landing is Turkish and branded dürt. *Default:* Next serves `/` in English (Nuddle) and `/tr` in Turkish (dürt).
15. **Trademark check** for Nuddle in the US/EU and the status of nuddle.com, before the global launch.

## Technical

16. **LLM provider and model.** *Decided 2026-10-11:* OpenRouter with `deepseek/deepseek-v4.1-flash` (`OPENROUTER_API_KEY`, `OPENROUTER_MODEL`). Models are not compared in the MVP; `mise run eval` checks the prompt against this model. Kept here so the number stays stable.
17. **Speech recognition:** on-device Android `SpeechRecognizer` vs server-side, Turkish accuracy and cost. *Default for MVP:* on-device, send the transcript as text.
18. **Poke plan heuristic** (step count, spacing, escalation, when the human channel fires). Designed with scenario tests in stage 3.
19. **Play policy** for `USE_EXACT_ALARM` and `USE_FULL_SCREEN_INTENT` as an alarm app. Submit an early closed test build.
20. **Avatar color palette:** six colors, each 4.5:1 with white text.
21. **Turkish suffixes after user content** (e.g. `Eren "Çöpü at"ı bitirdi`). *Default:* rephrase strings so user content is never suffixed (`Eren bitirdi: "Çöpü at"`).
22. **Brand fix JSON** content for Xiaomi, Samsung, Huawei, Oppo, Vivo (dontkillmyapp.com as reference).
23. **Pricing confirmations:** Supabase and Vercel plan limits and prices at launch time.
24. **Dark theme:** after the MVP. Tokens are structured for it.
25. **License for the public repo.** Without a LICENSE file the code is visible but all rights are reserved. *Default:* a short `LICENSE` stating "Copyright (c) 2026 Eren Öztürk. All rights reserved." until an open source license is chosen.

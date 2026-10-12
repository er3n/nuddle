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
18. **Poke plan heuristic** (step count, spacing, escalation, when the human channel fires). *v0 (stage 3):* `domain.md`, "Poke plan v0". Revisit the offsets and the human step with real usage.
19. **Play policy** for `USE_EXACT_ALARM` and `USE_FULL_SCREEN_INTENT` as an alarm app. Submit an early closed test build.
20. **Avatar color palette:** six colors, each 4.5:1 with white text.
21. **Turkish suffixes after user content** (e.g. `Eren "Çöpü at"ı bitirdi`). *Default:* rephrase strings so user content is never suffixed (`Eren bitirdi: "Çöpü at"`).
22. **Brand fix JSON** content for Xiaomi, Samsung, Huawei, Oppo, Vivo (dontkillmyapp.com as reference).
23. **Pricing confirmations:** Supabase and Vercel plan limits and prices at launch time.
24. **Dark theme:** after the MVP. Tokens are structured for it.
25. **License for the public repo.** Without a LICENSE file the code is visible but all rights are reserved. *Default:* a short `LICENSE` stating "Copyright (c) 2026 Eren Öztürk. All rights reserved." until an open source license is chosen.
26. **OTP SMS text.** No copy exists for the code message. *Default:* "dürt kodun: 123456", followed by the SMS Retriever app hash on its own line. Under 140 bytes.
27. **Next-up card copy not in the design** (screen 2f). The design shows only "Yarın 09:00" and a done task. *Decided 2026-10-12:* "Bugün 18:00" and "Yarın 09:00", otherwise day and month name ("14 Ekim 09:00"); for a photo task to self "Bitince | Fotoğrafını çek". Kept here so the number stays stable.
28. **Quick deadline picks in the preview** (screen 2b). *Decided 2026-10-12:* "Bugün akşam" is 19:00 today and only shown while that is ahead, "Yarın sabah" is 09:00 tomorrow (the parts of the day the extraction prompt uses). "Saat seç" picks a time of day; the deadline is the next time the clock shows it. Kept here so the number stays stable.
29. **Undo toast for a task to self** (2a). The design has it only for a task to someone else (4i: "Eren'e gitti"). *Decided 2026-10-12:* title "Kuruldu", sub line "Para çek · yarın 09:00" (plus "bitince fotoğraf" for a photo task); Geri al cancels the task; Düzenle opens the preview to choose a new deadline (`edit`). Kept here so the number stays stable.
30. **Setup copy and timing not in the design** (2c to 2e, self-only user). The design shows the invited flow with notifications already allowed and both settings left. *Default:* setup opens once the first task to self stands (the undo window is over, or Kur in the preview), only when it never ran on this install and a permission is missing. Demo label "Kendine · örnek". Title "Seni zamanında dürtebilmem için N ayar" (N counted when the steps open), or "Seni dürtebilmem için bildirim izni gerekiyor" when only notifications are missing; body for one setting "Bildirim iznini verdin. Kalanı Android'in ayar sayfasında, tek düğme.", while notifications are missing "Önce bildirim izni, kalanlar Android'in ayar sayfasında, her biri tek düğme.". Notifications step: "Dürtmeler bildirim olarak gelir. Bu izin olmadan sana hiç ulaşamam." with "İzin ver". Full screen step: "Telefonun kilitliyken de dürtmenin ekranı açması için. Bu olmadan dürtmeler sadece bildirim olarak gelir." and "Açılan sayfada "Tam ekran bildirimlere izin ver"i aç, sonra geri dön."; it can be skipped with "Tam ekransız devam et, dürtmeler bildirim olarak gelir". A skipped step shows "Atlandı". The demo poke does not play a sound.

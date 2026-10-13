# Android platform notes

Min SDK 26, target the current SDK (35+). The app is declared to Play as an alarm app. If accepted, setup shrinks to the notification dialog; if not, the fallback flow below runs. The code is the same in both cases: each setup step checks its permission first and is skipped when already granted.

## Permissions

| Permission | Manifest | How it is requested | Without it |
|---|---|---|---|
| Notifications | `POST_NOTIFICATIONS` | System dialog, setup step 1. Cannot be skipped. | App cannot work, step shown again |
| Exact alarms | `USE_EXACT_ALARM` (declared) + `SCHEDULE_EXACT_ALARM` | If `canScheduleExactAlarms()` is false, `ACTION_REQUEST_SCHEDULE_EXACT_ALARM`, setup step 2 | Approximate alarm with `setWindow`, reachability drops, warning in the bell |
| Full screen | `USE_FULL_SCREEN_INTENT` (declared) | If `canUseFullScreenIntent()` is false, `ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT`, setup step 3 | `FULL_SCREEN` degrades to `SOUND` |
| Camera | `CAMERA` | System dialog when the photo screen first opens | Photo step falls back to plain "Bitti" |
| Microphone | `RECORD_AUDIO` | On the first mic press | Text only |
| Boot | `RECEIVE_BOOT_COMPLETED` | Automatic | Alarms lost on reboot |

- No contacts permission: the invitee is picked with `ActivityResultContracts.PickContact` (single contact).
- No battery optimization exemption in setup. On stock Android `setAlarmClock` is not deferred by Doze.
- Play policy for `USE_EXACT_ALARM` and `USE_FULL_SCREEN_INTENT` is still to be confirmed; submit the first closed test build early.

## Alarms and pokes

- The server pushes plan steps with a high priority FCM data message. The device writes them to Room and calls `AlarmManager.setAlarmClock()` per step.
- On `BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`, `TIME_SET` and `TIMEZONE_CHANGED`, all pending alarms are re-set from Room.
- On fire, a `BroadcastReceiver` re-checks quiet hours and plan version, then shows the notification for the level. For `FULL_SCREEN` the notification opens `PokeActivity` via `setFullScreenIntent` (`showWhenLocked`, `turnScreenOn`).
- Bitti, Ertele and Bugün olmaz apply locally first (remaining alarms cancelled), then go to the server via the WorkManager outbox. Poking stops immediately even offline.
- Quiet hours are checked when the server plans and again on the device before ringing.

## Notification channels

| Channel id | Name (user visible) | Importance | Sound |
|---|---|---|---|
| `poke_silent` | Sessiz dürtmeler | DEFAULT | none |
| `poke_alarm` | Dürtme alarmları | HIGH | alarm sound, `USAGE_ALARM` (pierces DND by default) |
| `tasks` | Görevler ve onaylar | DEFAULT | default |
| `reach_out` | Ulaşılamayan kişi haberleri | HIGH | default |
| `system` | İzin ve kurulum uyarıları | LOW | none |

A channel disabled in system settings is treated like a missing permission: reachability drops and the bell shows a warning.

## Missed-ring detection

- Every fired alarm reports planned and actual time.
- The server counts a poke as lost when the report does not arrive while the device appears online.
- On launch, `ActivityManager.getHistoricalProcessExitReasons()` (API 30+) tells whether the previous exit was a force stop.
- On a loss, the bell shows "Son dürtmen çalmadı" (2g) and brand-specific steps (2h). Steps come from a server JSON keyed by `Build.MANUFACTURER` (`GET /api/v1/brand-fix`), so they change without an app update; any other phone gets a default step. First version covers Xiaomi, Samsung, Huawei, Oppo, Vivo (dontkillmyapp.com as reference).

## Other

- Phone sign-in: SMS Retriever API reads the code without any SMS permission.
- Photo: CameraX, in-app camera only, no gallery. Upload to a Supabase Storage signed URL through WorkManager with retries. (The handoff said R2; replaced by ADR 0002.)
- Voice: press and hold to record, release to send. Recognition runs on the phone (`SpeechRecognizer`, Turkish) and the transcript is written like typed text (open-questions #17, #40). `RECORD_AUDIO` is asked on the first hold.
- Share: `Intent.ACTION_SEND` system sheet. Call: `ACTION_DIAL`. Message: `ACTION_SENDTO` with `smsto:` through the system chooser.

## Navigation

Single Activity with Navigation Compose, plus `PokeActivity` for the lock screen. Route groups and the canvas screens they cover are in `screens.md`.

- An existing user goes from the code screen straight to `home`. A new user without an invite goes from name to `home`; `setup` opens at their first task.
- Back never returns to sign-in steps. `home` is the root of the back stack.
- Error screens (6a to 6f) are states of their screen, not routes. The generic error (6f) is full screen only when nothing can be recovered.
- Deep links: invite links are `https://nuddle.app/i/{inviteCode}` (open-questions #13), verified App Links (`/.well-known/assetlinks.json` from the web app). Signed in, the link opens `consent/{inviteId}` for an open invite to me, otherwise the invalid invite screen 6d (open-questions #39). Signed out, the app asks for the invite preview (ADR 0016) and starts at the invited welcome (0b); the name screen (0e) is prefilled with the name the inviter typed. When the preview fails (invite no longer open, offline), the plain welcome (0a) shows. After sign-in the link opens the consent screen as above. Without the app, the link opens a page on the web. Notifications open `task/{id}` or `inbox`.

## Accessibility

- Every tappable element is at least 44 x 44 dp. Visually small ones (links, chevrons) use `minimumInteractiveComponentSize`.
- Text contrast at least 4.5:1. Text on `accent` is always `ink`. `textMuted` only for decoration.
- TalkBack: icon buttons have content descriptions ("Bildirimler, 1 okunmamış", "Profil", "Basılı tutup konuş", "Geri"). A TaskRow reads as one element: "Gelirken ekmek al, Sibel, bu akşam, Kurulu". ToggleRow is `Role.Switch`, ChoiceChip is `Role.RadioButton` with selection state.
- Font scale up to 200%. Fixed-height boxes use `heightIn(min = ...)` so they grow.
- Animations 150-250 ms; with system animations off, toasts and sheets appear instantly. Poke rings stay static.
- With TalkBack on, the undo toast lasts 10 s and takes focus.

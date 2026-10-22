# Glossary and copy rules

Code and docs are in English. The UI is Turkish first (`values/strings.xml`), English is added before the global launch (`values-en/strings.xml`). Every user-facing string lives in resources, never in Kotlin code.

## Domain terms

| Code / docs (English) | Turkish UI | Notes |
|---|---|---|
| Person | kişi | A node in the graph. The user is a person too. |
| Connection, edge | bağlantı | Always 1:1. Has a type, a direction and learned weights. |
| Connection list | **Bizimkiler** | The only name for the list of people. Never "ev", "hane", "aile grubu". |
| Relationship type | "Senin neyin?" options: Eşim, Annem / Babam, Çocuğum, Kardeşim, Ev arkadaşım, Diğer | `spouse`, `parent`, `child`, `sibling`, `roommate`, `other`. Never called a "role" in the UI. |
| Invite | davet | 1:1, bound to a phone number. |
| Consent | "X seni dürtebilsin mi?" | İzin ver / Şimdi değil (or Reddet from the bell). |
| Task | görev | |
| Sender | görevi yazan | The person who wrote the task. Approves photos. |
| Recipient | görev yazılan, dürtülen | The person who gets poked. Can be the sender (task to self). |
| Poke | dürtme | One delivery attempt at one level. |
| Poke level | (never shown) | `SILENT`, `SOUND`, `FULL_SCREEN`, `HUMAN`. |
| Poke plan | (never shown) | Server generated ladder of pokes for one task. |
| Reachability | ulaşılabilirlik | Shown as one sentence: "Eren'e şu an en fazla tam ekranla ulaşabilirim". |
| Deadline | son zaman | |
| Snooze | ertele | Limited count, the result is stated ("10 dk ertele · son hakkın"). |
| Drop ("can't today") | Bugün yapamayacağım / Bugün olmaz | Status `DROPPED`, UI pill "Bırakıldı". Not a failure. |
| Missed | Kaçırıldı | Deadline passed without done or photo. |
| Photo | fotoğraf | **Never** "kanıt" (proof). |
| One-time message, notice | mesaj | Finish mode `notice` (NUD-79): the card shows the message with "Tamam" only. Never "bildiri" or "duyuru". |
| Awaiting approval | Onayını bekliyor / Onay bekleniyor | |
| Re-request | Tekrar iste / Tekrar istendi | |
| Cancel | İptal et / İptal edildi | Sender only. On a repeating task it ends the series. |
| Repeating task, series, occurrence | tekrarlayan görev, "Her gün 09:00" | ADR 0021. The UI never says "seri" or "tekrar kuralı"; it shows the rule as "Her gün 09:00", "Her salı 19:00", "Hafta içi 07:30". |
| Quiet hours | Sessiz saatler | Default 23:00-07:00. |
| Bell, inbox | zil, Bildirimler | In-app inbox. |
| Announcement | "Nuddle'dan", channel "Nuddle'dan haberler" | A message from us to people, not about a task (ADR 0025). The UI shows its own title; never "duyuru" or "bildiri". |
| Contact (to call or message) | Ara / Mesaj at | NUD-128: the person a task is about calling or messaging ("annemi ara"), always one of the recipient's connections or the sender. Only the buttons on the full screen poke show it; the UI never says "kişi" or "rehber" for it. |
| Already done? (heads-up before a loud poke) | "Zaten yaptın mı?" + the task title, Bitti | NUD-129, domain.md Already done?. A quiet notification, not a poke: no time, no count, never "dürtme". Resource `heads_up_title`. |
| Slide to finish (lock screen) | "Kaydırarak bitir", one-time message "Kaydırarak kapat" | NUD-131, screens.md 3b: Bitti over a locked phone is a slide. TalkBack hears the plain "Bitti" / "Tamam". Never "kaydır ve kapat" or "sürükle". Resources `poke_slide_done`, `poke_slide_ok`. |
| Human channel | (never named in UI) | Telling another person when pokes fail. In MVP: the sender. |
| Setup | Bir kerelik kurulum | Permission flow. |
| Missed ring, brand steps | "Son dürtmen çalmadı" (detected), "Dürtmem çalmadı" (profile row), "Dürtmen çalmadı mı?" (2h opened from it) | 2g, 2h. The person's own report uses "çalmadı", never "kayboldu" or "hata". |
| Re-install pokes | "Dürtmelerimi yeniden kur", then "Bu telefondaki dürtmelerin yeniden kuruldu." | Last action of 2h (NUD-126): every alarm of this phone set again. Never "yeniden planla" (the plan is the server's). |

## Spoken poke

The sound and full screen steps say the task aloud (NUD-124, product.md Poking). The spoken text follows the same rules as written copy: the user content (name, title) takes no suffix (open-questions #21).

| Case | Spoken (Turkish) | Resource |
|---|---|---|
| Task from someone else | "Sibel diyor ki: ilacını iç" | `poke_speech_from` = `%1$s diyor ki: %2$s` |
| Task to self, or the sender's name is not on the phone | "İlacını iç" (the title as written) | none, the title itself |

Only the title is read, never the detail. A one-time message reads the same way ("Sibel diyor ki: seni seviyorum").

## Poke titles

A poke's title is a short line from the app (NUD-130, product.md Principles), and the task's title is the text under it ("Hâlâ buradayım" over "Para çek · 1.000 TL"). Each level has its own lines, and each level is a step more insistent than the one before: `SILENT` asks gently, `SOUND` says it is still here, `FULL_SCREEN` says it will not give up. The lines are warm and a little funny, in the app's first person voice. They never blame, never count ("üçüncü kez" is out, the "N. dürtme" sub text is the only count), and never tell the plan or the next time.

| Level | Titles (Turkish) | Resource |
|---|---|---|
| `SILENT` | Bir şey unutmadın mı? / Küçük bir hatırlatma / Aklında olsun / Usulca dürtüyorum | `poke_titles_silent` |
| `SOUND` | Ben buradayım / Selam, ben geldim / Hâlâ buradayım / Biraz ses çıkarayım dedim | `poke_titles_sound` |
| `FULL_SCREEN` | Pes etmem / Hadi, şimdi tam sırası / Ben gitmiyorum / İnatçıyımdır, bilirsin | `poke_titles_full_screen` |

- The phone picks the line (`PokeTitles`, pure) from the task, the plan version and the step's number, so showing the same poke again (the full screen after the notification, a reboot) keeps its line, and two pokes in a row of one plan never share a line. The level is the one that rings (a full screen step that rings as sound takes a `SOUND` line). A new plan (a snooze, an edit) moves the start of the rotation; open-questions #60.
- Tasks to self and tasks from someone else use the same lines. The sender's picture stays the large icon.
- A one-time message (NUD-79) keeps the message as its title: the lines are about a task to do, a message has none.
- Before the first unlock a poke still says "Bir dürtmen var" (platform.md, Direct boot).

## Words the UI never uses

- "ev", "hane" (household)
- "kanıt" (proof). Say "fotoğraf".
- "yedek dürtücü" (backup poker), "rol" (role), "grup" (group)
- "gönderildi" for invites. Say "paylaşıldı" (shared).
- "aratır" (makes someone call). Say "haber veririm" (I'll let them know).
- Any description of the plan: no "3 kez daha dürteceğim". Poke counts appear only after the fact (done note) or in the "can't reach" message to the sender.

## Voice

- The app speaks in first person singular and addresses the user as "sen": "Seni zamanında dürterim", "Eren'e ulaşamıyorum".
- Button text is a verb that says the result: "Kur", "İptal et", "Notla gönder". "Tamam" only for acknowledgements.
- Error text says what happened and what is safe: "Yazdığın duruyor", "Dürtmeler yine zamanında çalar".
- The em dash (U+2014) is never used, neither in UI copy nor in docs. Use a period, comma or colon. Ranges use a hyphen ("23:00-07:00") or words ("23:00 ile 07:00").

## Example names

Design and tests use the same cast: **Sibel** (sender, avatar color #C83C08) and **Eren** (recipient, the user himself is always `ink`). Sample task: "Temizlikçi için para çek · 1.000 TL · yarın 09:00 · fotoğrafla". Keep using them in previews, fixtures and E2E scenarios so screenshots and tests match the design.

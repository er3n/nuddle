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
| Awaiting approval | Onayını bekliyor / Onay bekleniyor | |
| Re-request | Tekrar iste / Tekrar istendi | |
| Cancel | İptal et / İptal edildi | Sender only. |
| Quiet hours | Sessiz saatler | Default 23:00-07:00. |
| Bell, inbox | zil, Bildirimler | In-app inbox. |
| Human channel | (never named in UI) | Telling another person when pokes fail. In MVP: the sender. |
| Setup | Bir kerelik kurulum | Permission flow. |

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

Design and tests use the same cast: **Selin** (sender, avatar color #C83C08) and **Eren** (recipient, the user himself is always `ink`). Sample task: "Temizlikçi için para çek · 1.000 TL · yarın 09:00 · fotoğrafla". Keep using them in previews, fixtures and E2E scenarios so screenshots and tests match the design.

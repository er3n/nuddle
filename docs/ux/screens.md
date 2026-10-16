# Screens

Source: the "Dürt Mobil UX" design canvas (2026-10-10). Each screen has a code (e.g. `2e`) that matches the canvas title, a render in `screens/<File>.png` (390 dp wide, rendered at 2x), and its HTML source in `source/<File>.dc.html` with exact inline values.

How to use this when building a screen:
1. Read the entry below and open the PNG.
2. Take exact sizes and colors from the HTML source, mapped to tokens in `tokens.json` (px = dp).
3. Build it from `components.md` components. Put every string in `strings.xml` exactly as written in the **Copy** line (Turkish is the source language).
4. Render it with Roborazzi and compare with the PNG.

If a screen and `product.md` disagree on behavior, `product.md` wins. Copy written for the invited flow mentions "Sibel"; real strings take names as arguments.

## Routes

| Route | Canvas screens | Notes |
|---|---|---|
| `welcome` | 0a, 0b | 0b when opened from an invite link (inviter shown on top) |
| `auth/phone`, `auth/code` | 0c, 0d, 6c | Sign-up and sign-in are one flow |
| `onboarding/name` | 0e | New users only |
| `consent/{inviteId}` | 0f, 1d, 0g | Right after sign-in when invited; later from the invite in the bell |
| `setup` | 2c, 2d, 2e, 2h | After consent or at the first task. Granted steps are skipped |
| `home` | 1a, 1b, 1g, 2a, 2f, 4c, 4e, 4g, 5c, 5d, 6a, 6b, 7e | One screen, state derived from the lists. Two tabs and no action buttons since NUD-67 (Home tabs, below) |
| `task/{id}` | 2b, 4a, 7c, 7d, 7f | Preview (missing info) and the detail of any open task, from either side, with the actions for its state (Task detail, below). Cancel and can't today are bottom sheets |
| `task/{id}/photo`, `task/{id}/approve` | 4d, 6e, 4f | Camera and approval |
| `inbox` | 1c, 2g, 7b | The bell |
| `invite`, `invite/ready` | 1e, 1f | Ends in the system share sheet |
| `profile`, `person/{id}` | 5a, 5b, 7g, 7h, 7i | Settings sheets on top of profile |
| `PokeActivity` | 3b, 2d (demo), 7f | Opened from a notification or alarm |
| System notification | 3a, 4b, 7a | `NotificationCompat`, not Compose |

## Home tabs and task detail (NUD-67)

Changed on 2026-10-15 after the phone test: long titles wrapped into the buttons, and three sections ("Sana yazılanlar", "Kendine", "Senin yazdıkların") in one scroll were hard to read. This section wins over the layout of 2f, 4c, 4e, 4g, 5c, 5d, 7c and 7e below; their copy still applies. The PNGs of those screens show the old layout.

**Home.** Under the greeting, once there is someone to write to (`connected`), a segmented control with two tabs; without anyone there is one list and no tabs.
- **Bana**: tasks to self and to me in one list. "Sıradaki" (the card of 2f, 4c, 4e, 4g) on top, then "Sonra": missed tasks to me first (7e), then every other open task due first, then the "iptal etti" rows. The tab label carries the number of open tasks to me.
- **Gönderdiklerim**: my tasks to others. "Cevabını bekliyor" first (dropped, missed, a photo waiting for my approval), then "Devam eden" (scheduled, asked again) and the line "Bitenler zilde yazar." Texts written offline ("Sırada", 6a) show on both tabs, since only the server can tell whom they are for. The tab label carries a dot while something waits for my answer.
- A tap on a tab or a horizontal swipe switches, with a short slide. Home opens on Bana; writing a task to someone switches to Gönderdiklerim so the "Yeni" row is in view, writing one to self switches to Bana.
- No action buttons on home. The Sıradaki card and every row open the task: the detail below, or the approval (4f) for a photo waiting for me. Exceptions: the failed upload line on 4e still opens 6e, and the "iptal etti" row has a ✕ that removes it from this phone (it still goes by itself after a day, open-questions#36).
- Titles never push other parts around: one line with an ellipsis in a row, two lines on the Sıradaki card. A row has the avatar, the title, one sub line and a chevron; the status is the first part of the sub line in its pill color (no pill for Kurulu), for example "Bırakıldı · Not: yarın hallederim".

**Task detail (`task/{id}`).** One screen for any open task: the BackBar "Görev", who wrote it and when or whom it is for, the status pill, the whole title, the state's message, the rows (Kimden or Kime, Son zaman or Zaman, Tekrar for a repeating task: "Her gün 09:00" (ADR 0021), Detay, Nasıl bitsin), and the actions for the state and my side pinned to the bottom:

| My side | Status | Actions |
|---|---|---|
| To me | Kurulu | Şimdiden bitti (Bitti, çek on a photo task), Bugün yapamayacağım (7f), Ara and Mesaj. A one-time message (NUD-79): only Tamam |
| To me | Tekrar istendi | The sender's note; Yeni fotoğraf çek, Bugün yapamayacağım, Ara and Mesaj |
| To me | Onay bekleniyor | The 4e message; no action |
| To me | Kaçırıldı | Sonradan yaptım, Ara, Listeden kaldır |
| To self | Kurulu | Şimdiden bitti (Bitti, çek), Görevi iptal et; Son zaman and Nasıl bitsin can be edited (NUD-47) |
| To self | Kaçırıldı | Sonradan yaptım, Listeden kaldır |
| Mine to someone | Kurulu, Tekrar istendi | 7c as before: rows can be edited, Görevi iptal et (7d). Once a poke rang, a past-tense line under the sentence: "2 kez dürttüm, en son 09:10." (NUD-81); never the plan ahead |
| Mine to someone | Bırakıldı | The 5d message; Yeni zaman ver, Ben hallederim |
| Mine to someone | Kaçırıldı | The 5d message; Süreyi uzat, İptal et (no 7d sheet) |

The screen goes back by itself once the task leaves that list (done, cancelled, approved, handled). Yeni zaman ver and Süreyi uzat open the 5d deadline sheet over the detail.
**Copy:** Bana | Gönderdiklerim | Sonra | Cevabını bekliyor | Devam eden | Bitenler zilde yazar. | Henüz kimseye görev yazmadın. | Kimden | Listeden kaldır | Kaldır

## Quality pass (NUD-68)

Added on 2026-10-15 after the first test with two phones. Wins over the rows, the detail and 4i where they differ.
- **The sender's tick.** My task to someone shows how far it got: on a row in Gönderdiklerim after the name ("Sibel · gördü · bugün 19:00"), and in the detail under the status sentence while it is Kurulu or Tekrar istendi ("✓ Telefonuna ulaştı, henüz bakmadı.", "✓✓ Gördü." in the success color; nothing while it is only sent, since the server may simply not have heard yet). Reached means the recipient's phone stored it at a sync; seen means the task was on screen there: the Bana tab of home or its detail (domain.md, Task events). A full screen poke alone is not seen. No name in these lines, so no suffix (#21).
- **When poking starts, to the recipient.** The detail of a task to me or to self that has not rung yet and is not snoozed says "Saat 09:00 olunca hatırlatırım, yapmazsan biraz ısrar ederim." under the status sentence: the first step of the plan on this phone. The rest of the ladder stays hidden. Since NUD-78 the line is a row the recipient can tap: the sheet "Ne zaman hatırlatayım?" offers the hour choices and "Saat seç", between now and the task's time; Kaydet sends `set_start` and the line follows the new plan.
- **The last hour counts down.** Within an hour of a task's time, home adds the minutes left and keeps them current while it is on screen: rows "bugün 19:00, 35 dk kaldı" (an `at` task: "35 dk sonra"), the next-up card's time tile a second line "35 dk kaldı" in the link color. Further out the clock time stays alone. The detail, notifications and the bell do not count down.
- **4i says which time it is.** The undo toast's sub line uses the deadline phrase: "Para çek · son zaman yarın 09:00 · bitince fotoğraf" for a `by` task, "Su iç · bugün 19:48" for the moment of an `at` task, so a wrong reading of the text shows before the toast goes. Düzenle stays the way to fix it.

**Copy:** ulaştı | gördü | ✓ Telefonuna ulaştı, henüz bakmadı. | ✓✓ Gördü. | Saat 09:00 olunca hatırlatırım, yapmazsan biraz ısrar ederim. | bugün 19:00, 35 dk kaldı | 35 dk sonra

## Status to UI

| `TaskStatus` | Pill text | Pill color | Recipient sees | Sender sees |
|---|---|---|---|---|
| `SCHEDULED` | Kurulu | `neutralPill` | TaskRow or card with "Bitti" and "Şimdiden bitti" | TaskRow + reachability sentence (7c) |
| `AWAITING_APPROVAL` | Onayını bekliyor / Onay bekleniyor | `infoPill` | Waiting card (4e) | Approval screen (4f) |
| `REJECTED` | Tekrar istendi | `warningPill` | Note + take a new photo (4g), poking resumes | TaskRow |
| `DONE` | Tamamlandı | `successSoft` | DoneNote (5c), then leaves the list | Text entry in the bell (7b) |
| `DROPPED` | Bırakıldı | `neutralPill` | Leaves the list | Row under "Cevabını bekliyor"; Yeni zaman ver / Ben hallederim in the detail (5d) |
| `MISSED` | Kaçırıldı | `danger` filled | Row in Bana; Sonradan yaptım / Ara in the detail (7e), gone after 1 day | Row under "Cevabını bekliyor"; Süreyi uzat / İptal et in the detail (5d) |
| `CANCELLED` | İptal edildi | `neutralPill` | Info row (7e) | Leaves the list |
| `QUEUED` (local) | Sırada | `warningPill` | Written offline, not yet on the server (6a) | same |
| (local) just sent | Yeni | `neutralPill` | - | Highlighted TaskRow while the undo toast is on screen (4i) |

## Error states

| Case | Shown as | Screen |
|---|---|---|
| No network | StatusBanner, changes `QUEUED` | 6a |
| Extraction failed | InlineAlert + ComposeBar `Failed`, text kept | 6b |
| Wrong code / out of attempts | CodeInput error (remaining attempts and the 15 min wait once the API counts attempts) | 6c |
| Invalid invite | Centered empty state | 6d |
| Photo upload failed | InlineAlert, automatic retry via WorkManager | 6e |
| Unrecoverable | Full screen with a `DRT-xxxx` code, same code sent to Crashlytics | 6f |

---

## 0 · First launch and sign-in

### 0a Welcome · `Welcome.png`
Dark screen. Illustration: a stack of escalating pokes ("Ekmek al" 14:32, "Ekmek al!" 18:15, "EKMEK." on poke orange, then a green "Ekmek alındı · 19:02").
**Copy:** dürt. | Bir kere söylenir. / Gerisini dürt halleder. | Biri bir kere yazar, unutan kişi iş bitene kadar dürtülür. Kaçıncı dürtmede uyandığını da öğrenir. | Telefonla başla | Devam ederek Kullanım Koşulları ve Gizlilik metnini kabul etmiş olursun.

"Gizlilik" opens `https://nuddle.app/tr/privacy` in the browser (NUD-87). "Kullanım Koşulları" does nothing until the terms page exists (human checklist, before the beta).

### 0b Welcome, from an invite · `WelcomeInvite.png`
Same as 0a with an inviter chip on top.
**Copy:** Sibel seni dürt'e davet etti | Önce numaranla giriş yap, sonra Sibel'in davetine bakarız. | Telefonla başla

### 0c Phone · `Phone.png`
Country code button (TR +90) + number input. Privacy note card. The design makes WhatsApp the primary channel; until a WhatsApp template exists the code goes by SMS only (ADR 0014, decided in NUD-9), so the screen has one button.
**Copy:** Numaran ne? | Şifre yok. Numarana tek seferlik bir kod gelir. | Telefon numarası | Numaran sadece eşleştiğin kişilere görünür. Reklam, pazarlama mesajı, rehber taraması yok. | Kodu SMS ile gönder
WhatsApp copy for later: Kodu WhatsApp'tan gönder | SMS ile gönder

### 0d Code · `Otp.png`
Six-box CodeInput, resend countdown, autofill hint. SMS variant until WhatsApp exists (NUD-9); after the one minute countdown "Yeni kod gönder" appears in its place.
After a correct code for an existing account the code screen keeps its spinner until the first sync is in, at most 5 seconds, so home opens with the tasks instead of the empty state (NUD-105). A failed or slow sync opens home anyway.
**Copy:** Mesajlarına baktın mı? | +90 532 123 45 67 numarasına 6 haneli kod gönderdik. Numarayı değiştir | Kod gelmedi mi? 0:42 sonra tekrar gönderebilirsin. | Kod SMS ile gelince klavyenin üstünde otomatik çıkar, dokunman yeter. | Yeni kod gönder
WhatsApp copy for later: WhatsApp'ına baktın mı? | Kod WhatsApp'a gelince klavyenin üstünde otomatik çıkar, dokunman yeter. | SMS ile dene

### 0e Name · `Name.png`
Avatar placeholder with camera badge (photo optional; the picture is set later on Profile 5a, NUD-72). When invited, prefilled with the inviter's name for you.
**Copy:** Sana ne diyelim? | Eşleştiğin kişiler seni bu isimle görür, görevlerde de bu isimle anılırsın. | Adın | Sibel seni "Eren" diye eklemişti. Değiştirebilirsin. | Fotoğraf isteğe bağlı. Yoksa baş harfin görünür. | Devam

### 0f Consent (invited) · `Consent.png`
Inviter avatar, relationship row with "Değiştir", five promises, accept or later.
**Copy:** Sibel seni dürtebilsin mi? | Sibel seni eşi olarak ekledi. | Değiştir | Birbirinize görev yazabilirsiniz. | Gerekirse tam ekran alarmla dürtülebilirsin. | 23:00 ile 07:00 arası kimse dürtülmez. | Sibel bir işi tüm hatırlatmalara rağmen kaçırırsa sana da haber verebilirim. İstemezsen kapatabilirsin. | İstediğin an bağlantıyı kaldırabilirsin. Sibel'e bildirim gitmez. | İzin ver | Şimdi değil
"İzin ver" continues to setup (2c). "Şimdi değil" goes home; the invite stays in the bell.

### 0g Matched · `Matched.png`
Dark celebration screen.
**Copy:** Sen ve Sibel eşleştiniz | Artık birbirinize görev yazabilirsiniz. Sibel, Bizimkiler listende. | Başla

## 1 · Home

### 1a Home, empty · `Home.png`
TopBar, greeting, "invite someone" card, empty task state, ComposeBar.
**Copy:** Merhaba Eren | Dürt iki kişiyle güzel | Eşini, anneni ya da ev arkadaşını ekle. Birbirinize görev yazın, unutan dürtülsün. | Birini davet et | Henüz görev yok | Beklerken kendine de yazabilirsin. Zamanı gelince seni dürterim. | Yeni görev
Note: this card copy uses "ev arkadaşı" (roommate), which is allowed. The banned words are "ev" and "hane" as a household concept.

### 1b Home, pending invite · `HomePending.png`
Same as 1a with a bell badge (1).

### 1c Bell · `Notifications.png`
**Copy:** Bildirimler | Yeni | Sibel seni davet etti | Seni dürtmek için izin istiyor · 2 sa önce | Daveti gör | Daha eski bildirim yok

### 1d Invite detail · `InviteDetail.png`
Consent content opened from the bell, with Reddet instead of Şimdi değil.
**Copy:** Davet · 2 sa önce | (same as 0f) | İzin ver | Reddet | Reddedersen Sibel'e bildirim gitmez.

### 1e Invite, first open · `InviteCreate.png`
Pick a contact (single contact picker) or type a number, then choose the relationship (mandatory, nothing preselected). "Numarayı elle yaz" shows two inputs, each with a visible label above it as on 0c: Adı | Telefon numarası (NUD-99).
**Copy:** Birini davet et | Kimi? | Rehberden seç | Sadece seçtiğin kişiyi görürüz | Numarayı elle yaz | Senin neyin? | Eşim · Annem / Babam · Çocuğum · Kardeşim · Ev arkadaşım · Diğer | Zorunlu. Kime, ne zaman haber vereceğime buna göre karar veririm. | Daveti gönder | Kişiyi ve ilişkiyi seçince aktif olur.

### 1f Invite, ready · `InviteReady.png`
**Copy:** Sibel | +90 532 987 65 43 | Değiştir | Rehberden tek bir kişi seçtin. Rehberinin geri kalanına erişmiyoruz. | Sibel senin neyin? | Birbirinize görev yazabileceksiniz. Sibel'in eşin olduğunu bilmek, kime ne zaman haber vereceğime karar verirken işime yarar. | Daveti gönder
"Daveti gönder" opens the system share sheet. Afterwards the UI says "paylaşıldı".

### 1g Home, invite pending · `HomeInviteSent.png`
On home only while nobody is connected yet, as in the PNG. Once someone is connected, the card stays off home: its "Bekliyor" row on profile (5a) opens the same content in a sheet (NUD-100, after the first internal test).
**Copy:** Sibel'in katılması bekleniyor | Davet 14:32'de paylaşıldı · Eşin olarak | Sibel katılınca haber veririm. Link kaybolsa bile numarasıyla kayıt olduğunda davet ona ulaşır. | Tekrar paylaş | İptal et | Henüz görev yok | Sibel'i beklerken kendine yazabilirsin. | Yeni görev

## 2 · Writing a task and setup

### 2a Compose, to self · `TaskCompose.png`
ComposeBar focused with keyboard. Text that is not a task ("merhaba") gets the extractor's one-sentence reply in a ReplyNote above the bar, the bar is emptied, and nothing is created (NUD-52, components.md 6).
**Copy:** Kime, ne, ne zaman. Yazınca mikrofon gönder butonuna dönüşür. | Yeni görev | (example input) 1000 tl çek

### 2b Preview, only when info is missing · `TaskPreview.png`
Bottom sheet. Shows what was understood and asks exactly one question (here: the deadline). When the text gave the time of a moment task and the extraction judged a lead, the time row adds "ilk hatırlatma 17:10" after the time (NUD-82).
**Copy:** Yazdığın: "1000 tl çek" | Para çek | Kime | Kendine | Detay | 1.000 TL | Ne zamana kadar? | Bugün 19:00 | Yarın 09:00 (the canvas says "Bugün akşam" and "Yarın sabah"; open-questions #28) | Saat seç | Zamanı gelince seni dürterim. Gece 23:00 ile 07:00 arası asla. | Kur | Vazgeç

### 2c Setup: try first · `SetupDemo.png`
**Copy:** Bir kerelik kurulum | Önce bir dene, dürtme nasıl hissettiriyor gör | Sibel'in işlerini kaçırmaya başlarsan böyle dürterim. 3 saniye sonra çalar, sonra birlikte kuralım. | Ekranı kaplar, sesi susturana kadar çalar | Bir dene | Denemeden kur
Open: copy for users who write only to themselves (see `open-questions.md`).

### 2d Demo poke · `PokeDemo.png`
Full screen poke labeled as a demo.
**Copy:** DENEME · gerçek bir iş değil | Ekmek al | Sibel'den · örnek | İşte bu. Telefonun kilitliyken de ekranı açar. Gerçek bir işte buna nadiren gerek kalır, önce kibarca denerim. | Anladım, kuralım | Bu ekranın her seferinde çıkabilmesi için 2 ayar gerekiyor.

### 2e Two settings (alarm required) · `Permissions.png`
PermissionStep list: 1 Notifications (done), 2 Alarms and reminders (active, "Gerekli"), 3 Full screen (next). Progress bar. On Samsung, Xiaomi, Huawei, Oppo and Vivo phones a 4th step "Pil kısıtlaması" with "İzin ver" opens the one-tap system dialog (NUD-75, open-questions #49); the title then counts 3 settings.
**Copy:** Bir kerelik kurulum | Seni zamanında dürtebilmem için 2 ayar | Bildirim iznini verdin. Kalan ikisi Android'in ayar sayfasında, her biri tek düğme. | Bildirimler | Tamam | Alarmlar ve hatırlatıcılar | Gerekli | Dürtmelerin tam saatinde çalması için. Bu olmadan telefon onları biriktirir, dakikalarca bazen saatlerce geç çalarlar. | Açılan sayfada "Alarm ve hatırlatıcı ayarlamaya izin ver"i aç, sonra geri dön. | Ayarları aç | Tam ekran dürtme | Sırada | İzinleri istediğin an Ayarlar'dan geri alabilirsin. Biri kapanırsa zilde haber veririm. | Alarmsız devam et, dürtmeler geç çalabilir

### 2f Home, task scheduled · `HomeTask.png`
"Next up" card for a self task. Tapping it opens 7c for the task (edit a row, "Görevi iptal et"); for a task to self 7c says "Kendine", "Zamanı gelince seni dürterim." and the cancel sheet has no reasons (NUD-47).
**Copy:** Sıradaki | Para çek | 1.000 TL · Kendine | Kurulu | Son zaman | Yarın 09:00 | Bitince | "Bitti" demen yeter | Şimdiden bitti | Dürt iki kişiyle güzel | Birini davet et, birbirinize görev yazın | Yeni görev

### 2g Bell, permission and missed-ring warnings · `NotificationsPerm.png`
**Copy:** Bildirimler | Yeni | Son dürtmen çalmadı | Telefonun beni arka planda kapatmış. Düzeltmezsen bir sonraki dürtme de kaybolabilir. | Düzelt | Tam ekran izni kapalı | Yarınki "Para çek" dürtmen sadece bildirim olarak gelecek. Kaçırma ihtimalin artıyor. | Düzelt | Daha önce | "Para çek" kuruldu | Bugün 14:32

### 2h Poke did not ring, brand-specific · `OemFix.png`
Steps come from the server brand JSON (example: Xiaomi).
**Copy:** Son dürtmen çalmadı | Dün 08:15'te seni dürtecektim, telefonun beni kapatmış. Xiaomi telefonlar uygulamaları arka planda durdurabiliyor. İki ayarla düzelir. | Otomatik başlatma | Tamam | Pil tasarrufu: Kısıtlama yok | Açılan sayfada "dürt"ü bul, "Kısıtlama yok"u seç. | Ayarı aç | Bu adımları sadece dürtmesi kaybolan telefonlarda gösteririm. Adımlar markaya göre değişir.

## 3 · The poke

### 3a Poke, notification · `PokeNotif.png`
System notification (shell is the system's, content and actions are ours).
**Copy:** 2. dürtme | Para çek · 1.000 TL | Son zamana 1 saat var. Şimdi çıkarken uğrarsan iş biter. | Bitti | 15 dk ertele (built as "Ertele · 08:30", open-questions #1) | Bugün olmaz

### 3b Poke, full screen · `PokeFull.png`
`PokeActivity` on `poke` background with rings.
**Copy:** 3. dürtme · 08:15 | Para çek | 1.000 TL · son zamana 45 dk | İki dürtmeyi geçtin. Bu sefer ciddiyim, 09:00'a az kaldı. | Bitti | 10 dk ertele · son hakkın (built as "Ertele · 08:25 · son hakkın", open-questions #1) | Bugün yapamayacağım

## 4 · A task for someone else

### 4a Preview, only if ambiguous · `OtherPreview.png`
Sender side. Shows the extracted task, finish mode choice (photo preselected for money), and reachability.
**Copy:** Yazdığın: "eren yarın 9dan önce temizlikçi için 1000 çeksin" | Temizlikçi için para çek | Kime | Eren | Son zaman | Yarın 09:00'dan önce | Detay | 1.000 TL | Nasıl bitsin? | Para işlerinde fotoğraf öneririm | "Bitti" yeter | Fotoğrafla, sen onayla | Eren'e şu an en fazla sesli bildirimle ulaşabilirim. | Daha güçlü dürtebilmem için kendisinden izin isteyeceğim. | Eren'e gönder | Vazgeç

### 4b Notification to Eren · `OtherNotif.png`
**Copy:** Sibel sana bir görev yazdı | Temizlikçi için para çek · yarın 09:00'dan önce. Bitince fotoğrafını istiyor. | Bir şey yapman gerekmiyor. Zamanı gelince seni dürterim. | Gör

### 4c Eren's home, task from Sibel · `OtherHome.png`
Next-up card with Call and Message icons.
**Copy:** Sıradaki | Sibel yazdı · 14:32 | Kurulu | Temizlikçi için para çek | 1.000 TL | Son zaman | Yarın 09:00 | Bitince | Fotoğraf, Sibel onaylar | Bitti, çek | Bugün yapamayacağım

### 4d Take photo (Eren) · `PhotoCapture.png`
**Copy:** Temizlikçi için para çek | Sibel'e gidecek · 1.000 TL istendi | Fotoğraf sadece Sibel'le paylaşılır, görev kapanınca silinir. Kart numarası ya da bakiye görünüyorsa kapatarak çekebilirsin. | Sibel'e gönder | Tekrar çek
Since NUD-73 the shutter has "Galeriden seç" on its left and "Kamerayı çevir" on its right (44 dp dark round buttons); a picked picture goes to the same review as a shot (open-questions #48).

### 4e Awaiting approval (Eren) · `OtherWaiting.png`
**Copy:** Sibel yazdı · dün 14:32 | Onay bekleniyor | Temizlikçi için para çek | 1.000 TL · son zaman 09:00 | Fotoğraf Sibel'de. Onaylayınca görev kapanır. | 08:24'te gönderdin · Dürtme durdu | Sibel bakmazsa 3 saat sonra kendiliğinden kapanır.

### 4f Approval (Sibel) · `ApproveSibel.png`
**Copy:** Onayını bekliyor | Temizlikçi için para çek | Eren fotoğraf gönderdi · 08:24 · 1.000 TL istemiştin | Tekrar istersen not (isteğe bağlı) | Tekrar iste | Onayla | Bakmazsan 3 saat sonra kendiliğinden onaylanır.

### 4g Re-requested (Eren) · `OtherRejected.png`
**Copy:** Tekrar istendi | (Sibel's note, e.g.) 1.500 lazımdı, 500 daha çekebilir misin? | Dürtme kaldığı yerden devam ediyor · sıradaki 08:40 | Yeni fotoğraf çek | Bugün yapamayacağım
Note: "sıradaki 08:40" reveals the next poke time. This conflicts with the hidden plan rule; see `open-questions.md`.

### 4h Voice input (tap to dictate) · `VoiceHold.png`
**Copy:** Dinliyorum | (live transcript in the box)
Changed in NUD-48 after the phone test: no press and hold and no panel. A tap on the mic starts listening, the transcript goes into the compose box, a second tap or silence stops, and the person sends it with the arrow. The PNG still shows the old hold-to-talk panel; `docs/ux/components.md` 3 wins. Rules and notes: open-questions #40.

### 4i Sent directly, undo · `QuickSent.png`
Sender home with the new task marked "Yeni" and the UndoToast.
**Copy:** Eren'e yazdıkların | Temizlikçi için para çek | 1.000 TL · yarın 09:00 · fotoğrafla | Yeni | Eren'e gitti | Para çek · yarın 09:00 · bitince fotoğraf | Geri al | Düzenle

## 5 · Profile, full home, sender side

### 5a Profile and settings · `Profile.png`
**Copy:** Profil | Düzenle | Fotoğraf | Galeriden seç | Fotoğrafı kaldır | Bizimkiler | Eşin | + Birini davet et | Dürtme | Benim işlerimde tanıdıklarımı kullanabilirsin | Her şeyi kaçırırsam bir yakınıma haber verebilirsin. | Başkalarının işleri için bana haber verilebilir | Bir tanıdığım işini kaçırırsa bana söyleyebilirsin. | Sessiz saatler | Kimse dürtülmez | 23:00 – 07:00 | İzinler | Bildirimler | Alarmlar ve hatırlatıcılar | Tam ekran alarm | Kapalı · Düzelt | Çıkış yap | Hesabı sil
Note: the canvas shows "23:00 – 07:00" with an en dash. Use "23:00-07:00" or "23:00 ile 07:00" in strings; never an em dash.
An open invite I sent is a row under Bizimkiler with "Bekliyor"; a tap opens a sheet with the 1g content: "Sibel henüz katılmadı", when it was shared and as what, the body, "Tekrar paylaş" (the system share sheet with the same link) and "İptal et" (NUD-100).
Since NUD-72 the avatar at the top opens the picture sheet ("Fotoğraf"): "Galeriden seç" opens the system photo picker and uploads the picture; "Fotoğrafı kaldır" shows the initial again. Connections see the picture after their next sync.

### 5b Person detail · `PersonDetail.png`
**Copy:** Eşin · [3 aydır] bağlısınız | Bu bağlantı | İlişki | Sibel'in işleri için bana haber verilebilir | Sibel bir işi kaçırırsa sana da söyleyebilirim. | Birlikte | Birbirinize yazdığınız | [24] görev | Zamanında biten | [%88] | Bağlantıyı kaldır | Sibel'e bildirim gitmez. Açık görevleriniz iptal olur.
Note: "Zamanında biten %88" is a statistic shown to both people. Check against the "never a scorecard" principle (open question).

### 5c Home, full + done note · `HomeFull.png`
Sections: Sana yazılanlar, Senin yazdıkların, Kendine. DoneNote at the bottom. Built in NUD-61 with the 2f/4c next-up card kept on top: every other open task to me follows as a TaskRow, from others under "Sana yazılanlar" ("Sibel · bu akşam") and to self under "Kendine", due first; then "Senin yazdıkların". Since NUD-67 the sections are the two tabs above and every row opens the task detail.
**Copy:** Gelirken ekmek al · Sibel · bu akşam · Kurulu | Faturayı öde · Sibel'e · fotoğraf geldi · Onayını bekliyor | Kuru temizlemeyi al · Sibel'e · cuma 18:00 | Spor salonu üyeliğini yenile · Pazartesi 12:00 | "Para çek" tamamlandı | Bu sefer seni 2. dürtmede yakaladım. Sabahları daha hızlısın.

### 5d Sibel: dropped and missed · `SibelIssues.png`
**Copy:** Bugün 19:00'a kadardı | Bırakıldı | Eren bugün yapamayacağını söyledi. Sebep: vakit yok. | Yeni zaman ver | Ben hallederim | Bugün 09:00'a kadardı | Kaçırıldı | Son zaman geçti, fotoğraf gelmedi. 4 kez dürttüm, sana da haber verdim. | Süreyi uzat | İptal et

## 6 · Errors

### 6a No internet · `ErrOffline.png`
**Copy:** İnternet yok. Dürtmeler yine zamanında çalar, değişiklikler bağlantı gelince gider. | Sibel'e · bağlantı gelince gidecek | Sırada

### 6b Send failed · `ErrSend.png`
**Copy:** Şu an anlayamadım | Sunucuya ulaşırken bir sorun oldu. Yazdığın duruyor, tekrar dene. | Tekrar dene

### 6c Wrong code · `ErrOtp.png`
**Copy:** Kod tutmadı | +90 532 123 45 67 numarasına gelen 6 haneli kodu tekrar dene. | Kod yanlış. | Yeni kod gönder
After a wrong code: Kod yanlış. 2 deneme hakkın kaldı. | Hakların biterse 15 dakika sonra yeni kod isteyebilirsin. Bu, numaranı kötüye kullanılmaktan korur. Rules and the copy for the wait: open-questions #41.

### 6d Invalid invite · `ErrInvite.png`
**Copy:** Bu davet artık geçerli değil | Davet ya kullanıldı ya da süresi doldu. Seni davet edenden yeni bir link isteyebilirsin. | Ana ekrana dön | Birini ben davet edeyim

### 6e Photo upload failed · `ErrPhoto.png`
**Copy:** Telefonunda saklı, kaybolmadı. | Sibel'e gönderilemedi | Bağlantı gelince kendiliğinden tekrar denerim. Son zaman, gönderdiğin ana göre sayılır. | Şimdi tekrar dene

### 6f Generic error · `ErrGeneric.png`
**Copy:** Bir şeyler ters gitti | Bizden kaynaklı bir sorun. Kurduğun dürtmeler telefonunda duruyor, zamanında çalacak. | Tekrar dene | Ana ekrana dön | Hata kodu: DRT-5021

## 7 · Sender side, missing, settings

### 7a Sibel: can't reach Eren · `SibelReach.png`
System notification on the `reach_out` channel.
**Copy:** Eren'e ulaşamıyorum | Temizlikçi için para çek · son zaman 09:00. 4 kez dürttüm, tam ekran dahil, cevap yok. | Bir arasan iyi olur. Ben dürtmeye devam ediyorum. | Ara | Mesaj | Gör

### 7b Sibel: bell, finished tasks as text · `SibelBell.png`
**Copy:** Bugün | Eren'e ulaşamıyorum: Temizlikçi için para çek | Son zaman 09:00 · bir arasan iyi olur | Eren "Çöpü at"ı bitirdi | Bitti dedi | Eren "Gelirken ekmek al"ı bitirdi | Onayladın | Eren "Kuru temizleme"yi bugün yapamayacak | Not: yarın hallederim | Biten işler sadece burada yazı olarak durur, ayrıca bildirim göndermem. Fotoğraflı işler onayına düşer.
Note: Turkish suffixes after a quoted title ("Çöpü at"ı) depend on the title's last vowel. Use plural/format resources carefully or rephrase to avoid suffixing user content (open question).

### 7c Sibel: ongoing task · `SibelTaskDetail.png`
**Copy:** Görev | Yazdığın: "eren yarın 9dan önce temizlikçi için 1000 tl çeksin" | Kurulu | Dürtmeler başladı | Eren'e şu an en fazla tam ekranla ulaşabilirim. Bitince sana yazarım. | Kime | Son zaman | Detay | Nasıl bitsin | Fotoğrafla, sen onayla | Bir satıra dokunup değiştirebilirsin. Değişiklik Eren'e kısa bir notla gider. | Görevi iptal et

### 7d Sibel: cancel task · `SibelCancel.png`
Bottom sheet, Danger primary.
**Copy:** Görev iptal edilsin mi? | Dürtmeler hemen durur. Eren'e kısa bir not giderim: "Sibel bu görevi iptal etti." | Ben hallettim | Gerek kalmadı | Sebep opsiyonel, seçersen nota eklenir. | İptal et | Vazgeç

### 7e Eren: missed and cancelled · `OtherMissed.png`
**Copy:** Sibel · bugün 09:00'a kadardı | Kaçırıldı | Son zaman geçti. Sibel'e haber verdim, yeni bir zaman verebilir. | Sonradan yaptım | Sibel'i ara | Sibel "Kuru temizlemeyi al"ı iptal etti | Not: ben hallettim · dürtmeler durdu | Kaçırılan iş bir gün sonra listeden kalkar. Sayılar sadece seni daha iyi dürtmek için tutulur, kimseye karne olarak gösterilmez.

### 7f Can't today, optional note · `DropReason.png`
Bottom sheet over `PokeActivity` or home.
**Copy:** Tamam, bugün olmuyor | Sibel'e bir not bırakmak ister misin? Zorunlu değil. | Vaktim olmadı | Yarın hallederim | Dışarı çıkamadım | Not | Görev "bırakıldı" olarak kapanır, dürtmeler durur. Başarısız sayılmaz. | Notla gönder | Notsuz kapat

### 7g Quiet hours · `QuietHours.png`
**Copy:** Sessiz saatler | Bu saatlerde hiçbir dürtme çalmaz, tam ekran dahil. | Başlangıç | Bitiş | Son zamanı bu aralığa düşen bir iş varsa seni önceden dürterim. | Kaydet

### 7h Log out · `LogoutConfirm.png`
**Copy:** Çıkış yapılsın mı? | Bu telefonda dürtmeler çalmaz. Sana yazılan işler kaçabilir, Sibel de sana ulaşamadığımı görür. | Görevlerin ve bağlantıların silinmez. Aynı numarayla girince hepsi geri gelir. | Çıkış yap | Vazgeç

### 7i Delete account · `DeleteConfirm.png`
**Copy:** Hesabın silinsin mi? | Bağlantıların, görevlerin ve fotoğrafların silinir. | Sibel'in sana yazdığı açık işler iptal olur, ona haber veririm. | Seni ne kadar dürtmem gerektiğini öğrendiklerim de silinir. | Geri alınamaz. | Hesabımı sil | Vazgeç

## 8 · Component catalog · `Components.png`
Visual reference for every component in `components.md`.

## Landing · `Landing.png`
Web landing page for the domain (1440 wide, interactive in the canvas). Built in `apps/web` with Next. The canvas page is branded "dürt" / durtmobil.com; the global brand decision (Nuddle) means the landing needs an English Nuddle version and a Turkish dürt version: `/` is English (Nuddle), `/tr` is Turkish (dürt); `/` sends a Turkish browser to `/tr` unless English was picked with the language switch (open-questions #14). Built in NUD-21 with these differences from the canvas:
- The footer shows nuddle.app, not durtmobil.com (open-questions #13).
- Step 1's example is "Eren yarın 9'a kadar kirayı yatırsın." instead of "Temizlikçi yarın 9'da, para çekilsin.", which read badly.
- Premium shows "Yakında" instead of a price and has no button until the price is decided (open-questions #10).
- "Google Play'den indir" reads "Yakında Google Play'de" and links nowhere until the Play listing exists (`PLAY_STORE_URL` in `apps/web/src/landing/store.ts`).
- The footer links to the privacy notice (`/privacy`, `/tr/privacy`) and the account deletion page (`/delete-account`, `/tr/delete-account`), labeled "Gizlilik" and "Hesabı sil" (NUD-85). Koşullar and İletişim are left out until those pages exist (human checklist, before the beta). The text of both pages is in `apps/web/src/i18n/messages/legal.*.ts`.
- Step 3 and the FAQ say a photo is taken in the app or picked from the gallery, and the gallery question is answered with the approval (open-questions #48, NUD-90). The canvas still says gallery photos cannot be uploaded.
- The hero badge counts the taps on the poke levels toy ("Bu sayfa seni 2 kez dürttü.").
- A video section ("28 saniyede dürt", `#video`, "İzle" in the nav) after the marquee plays the Play promo video of the page's language (NUD-94): the dürt video on `/tr`, the Nuddle one on `/`. The poster is a frame of the video served from `apps/web/public/promo/`; the YouTube player (`youtube-nocookie.com`) loads only after the play tap, and a line under it says so.
- A features section ("Bir de bunlar var.") after the three steps shows what the app gained after NUD-21, each card topped with the app's own words: repeating tasks ("Her gün 09:00", ADR 0021), one-time messages ("Mesaj", NUD-79), voice input ("Dinliyorum", NUD-48), moving the poke start ("Saat 17:00 olunca", NUD-78), snooze ("Ertele · 22:37", NUD-63), what the sender sees ("✓✓ Gördü", NUD-68, NUD-81), the profile picture (NUD-72, NUD-80) and tasks to self. Step 1 says the task can be said as well as written and that "3'te" is a moment, "9'a kadar" a deadline (ADR 0017); the free plan lists voice, repeating tasks and messages; the FAQ adds repeating tasks and a later poke start; the marquee adds "KİRAYI YATIR", "HER SALI ÇÖP", "İLACINI İÇ".
- A medicine reminder section ("İlaç hatırlatıcı: annen unutmasın.", `#medicine`) after the features, for people who search for one (NUD-106): a repeating task said once, the escalation up to the full screen alarm, and the sender learning whether it was taken. It ends with a line that the app gives no medical advice. The page title starts with "Hatırlatıcı ve alarm" (Nuddle: "Reminders and alarms") for the same searches.
- The invite link page (`/i/{code}`) and the 404 use the same look.

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
| `home` | 1a, 1b, 1g, 2a, 2f, 4c, 4e, 4g, 5c, 5d, 6a, 6b, 7e | One screen, state derived from the lists |
| `task/{id}` | 2b, 4a, 7c, 7d | Preview (missing info) and the sender's detail. Cancel is a bottom sheet |
| `task/{id}/photo`, `task/{id}/approve` | 4d, 6e, 4f | Camera and approval |
| `inbox` | 1c, 2g, 7b | The bell |
| `invite`, `invite/ready` | 1e, 1f | Ends in the system share sheet |
| `profile`, `person/{id}` | 5a, 5b, 7g, 7h, 7i | Settings sheets on top of profile |
| `PokeActivity` | 3b, 2d (demo), 7f | Opened from a notification or alarm |
| System notification | 3a, 4b, 7a | `NotificationCompat`, not Compose |

## Status to UI

| `TaskStatus` | Pill text | Pill color | Recipient sees | Sender sees |
|---|---|---|---|---|
| `SCHEDULED` | Kurulu | `neutralPill` | TaskRow or card with "Bitti" and "Şimdiden bitti" | TaskRow + reachability sentence (7c) |
| `AWAITING_APPROVAL` | Onayını bekliyor / Onay bekleniyor | `infoPill` | Waiting card (4e) | Approval screen (4f) |
| `REJECTED` | Tekrar istendi | `warningPill` | Note + take a new photo (4g), poking resumes | TaskRow |
| `DONE` | Tamamlandı | `successSoft` | DoneNote (5c), then leaves the list | Text entry in the bell (7b) |
| `DROPPED` | Bırakıldı | `neutralPill` | Leaves the list | TaskActionCard: Yeni zaman ver / Ben hallederim (5d) |
| `MISSED` | Kaçırıldı | `danger` filled | TaskActionCard: Sonradan yaptım / Sibel'i ara (7e), gone after 1 day | TaskActionCard: Süreyi uzat / İptal et (5d) |
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

### 0b Welcome, from an invite · `WelcomeInvite.png`
Same as 0a with an inviter chip on top.
**Copy:** Sibel seni dürt'e davet etti | Önce numaranla giriş yap, sonra Sibel'in davetine bakarız. | Telefonla başla

### 0c Phone · `Phone.png`
Country code button (TR +90) + number input. Privacy note card. The design makes WhatsApp the primary channel; until a WhatsApp template exists the code goes by SMS only (ADR 0014, decided in NUD-9), so the screen has one button.
**Copy:** Numaran ne? | Şifre yok. Numarana tek seferlik bir kod gelir. | Telefon numarası | Numaran sadece eşleştiğin kişilere görünür. Reklam, pazarlama mesajı, rehber taraması yok. | Kodu SMS ile gönder
WhatsApp copy for later: Kodu WhatsApp'tan gönder | SMS ile gönder

### 0d Code · `Otp.png`
Six-box CodeInput, resend countdown, autofill hint. SMS variant until WhatsApp exists (NUD-9); after the one minute countdown "Yeni kod gönder" appears in its place.
**Copy:** Mesajlarına baktın mı? | +90 532 123 45 67 numarasına 6 haneli kod gönderdik. Numarayı değiştir | Kod gelmedi mi? 0:42 sonra tekrar gönderebilirsin. | Kod SMS ile gelince klavyenin üstünde otomatik çıkar, dokunman yeter. | Yeni kod gönder
WhatsApp copy for later: WhatsApp'ına baktın mı? | Kod WhatsApp'a gelince klavyenin üstünde otomatik çıkar, dokunman yeter. | SMS ile dene

### 0e Name · `Name.png`
Avatar placeholder with camera badge (photo optional, MVP shows initials only). When invited, prefilled with the inviter's name for you.
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
Pick a contact (single contact picker) or type a number, then choose the relationship (mandatory, nothing preselected).
**Copy:** Birini davet et | Kimi? | Rehberden seç | Sadece seçtiğin kişiyi görürüz | Numarayı elle yaz | Senin neyin? | Eşim · Annem / Babam · Çocuğum · Kardeşim · Ev arkadaşım · Diğer | Zorunlu. Kime, ne zaman haber vereceğime buna göre karar veririm. | Daveti gönder | Kişiyi ve ilişkiyi seçince aktif olur.

### 1f Invite, ready · `InviteReady.png`
**Copy:** Sibel | +90 532 987 65 43 | Değiştir | Rehberden tek bir kişi seçtin. Rehberinin geri kalanına erişmiyoruz. | Sibel senin neyin? | Birbirinize görev yazabileceksiniz. Sibel'in eşin olduğunu bilmek, kime ne zaman haber vereceğime karar verirken işime yarar. | Daveti gönder
"Daveti gönder" opens the system share sheet. Afterwards the UI says "paylaşıldı".

### 1g Home, invite pending · `HomeInviteSent.png`
**Copy:** Sibel'in katılması bekleniyor | Davet 14:32'de paylaşıldı · Eşin olarak | Sibel katılınca haber veririm. Link kaybolsa bile numarasıyla kayıt olduğunda davet ona ulaşır. | Tekrar paylaş | İptal et | Henüz görev yok | Sibel'i beklerken kendine yazabilirsin. | Yeni görev

## 2 · Writing a task and setup

### 2a Compose, to self · `TaskCompose.png`
ComposeBar focused with keyboard. Text that is not a task ("merhaba") gets the extractor's one-sentence reply in a ReplyNote above the bar, the bar is emptied, and nothing is created (NUD-52, components.md 6).
**Copy:** Kime, ne, ne zaman. Yazınca mikrofon gönder butonuna dönüşür. | Yeni görev | (example input) 1000 tl çek

### 2b Preview, only when info is missing · `TaskPreview.png`
Bottom sheet. Shows what was understood and asks exactly one question (here: the deadline).
**Copy:** Yazdığın: "1000 tl çek" | Para çek | Kime | Kendine | Detay | 1.000 TL | Ne zamana kadar? | Bugün 19:00 | Yarın 09:00 (the canvas says "Bugün akşam" and "Yarın sabah"; open-questions #28) | Saat seç | Zamanı gelince seni dürterim. Gece 23:00 ile 07:00 arası asla. | Kur | Vazgeç

### 2c Setup: try first · `SetupDemo.png`
**Copy:** Bir kerelik kurulum | Önce bir dene, dürtme nasıl hissettiriyor gör | Sibel'in işlerini kaçırmaya başlarsan böyle dürterim. 3 saniye sonra çalar, sonra birlikte kuralım. | Ekranı kaplar, sesi susturana kadar çalar | Bir dene | Denemeden kur
Open: copy for users who write only to themselves (see `open-questions.md`).

### 2d Demo poke · `PokeDemo.png`
Full screen poke labeled as a demo.
**Copy:** DENEME · gerçek bir iş değil | Ekmek al | Sibel'den · örnek | İşte bu. Telefonun kilitliyken de ekranı açar. Gerçek bir işte buna nadiren gerek kalır, önce kibarca denerim. | Anladım, kuralım | Bu ekranın her seferinde çıkabilmesi için 2 ayar gerekiyor.

### 2e Two settings (alarm required) · `Permissions.png`
PermissionStep list: 1 Notifications (done), 2 Alarms and reminders (active, "Gerekli"), 3 Full screen (next). Progress bar.
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
**Copy:** 2. dürtme | Para çek · 1.000 TL | Son zamana 1 saat var. Şimdi çıkarken uğrarsan iş biter. | Bitti | 15 dk ertele | Bugün olmaz

### 3b Poke, full screen · `PokeFull.png`
`PokeActivity` on `poke` background with rings.
**Copy:** 3. dürtme · 08:15 | Para çek | 1.000 TL · son zamana 45 dk | İki dürtmeyi geçtin. Bu sefer ciddiyim, 09:00'a az kaldı. | Bitti | 10 dk ertele · son hakkın | Bugün yapamayacağım

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
**Copy:** Profil | Düzenle | Bizimkiler | Eşin | + Birini davet et | Dürtme | Benim işlerimde tanıdıklarımı kullanabilirsin | Her şeyi kaçırırsam bir yakınıma haber verebilirsin. | Başkalarının işleri için bana haber verilebilir | Bir tanıdığım işini kaçırırsa bana söyleyebilirsin. | Sessiz saatler | Kimse dürtülmez | 23:00 – 07:00 | İzinler | Bildirimler | Alarmlar ve hatırlatıcılar | Tam ekran alarm | Kapalı · Düzelt | Çıkış yap | Hesabı sil
Note: the canvas shows "23:00 – 07:00" with an en dash. Use "23:00-07:00" or "23:00 ile 07:00" in strings; never an em dash.

### 5b Person detail · `PersonDetail.png`
**Copy:** Eşin · [3 aydır] bağlısınız | Bu bağlantı | İlişki | Sibel'in işleri için bana haber verilebilir | Sibel bir işi kaçırırsa sana da söyleyebilirim. | Birlikte | Birbirinize yazdığınız | [24] görev | Zamanında biten | [%88] | Bağlantıyı kaldır | Sibel'e bildirim gitmez. Açık görevleriniz iptal olur.
Note: "Zamanında biten %88" is a statistic shown to both people. Check against the "never a scorecard" principle (open question).

### 5c Home, full + done note · `HomeFull.png`
Sections: Sana yazılanlar, Senin yazdıkların, Kendine. DoneNote at the bottom.
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
- The footer leaves out Gizlilik · Koşullar · İletişim until those pages exist (human checklist, before the beta).
- The hero badge counts the taps on the poke levels toy ("Bu sayfa seni 2 kez dürttü.").
- The invite link page (`/i/{code}`) and the 404 use the same look.

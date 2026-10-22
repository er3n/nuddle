# Components

About 20 Compose components cover every screen. All live in `ui/components`, are stateless (state in, events out as lambdas), and each has at least one `@Preview` (enforced by Konsist). Visual reference: `screens/Components.png` (canvas board "8 · Bileşen kataloğu").

Theme: Material 3 `MaterialTheme` as a base, with our own `DurtColors`, `DurtType`, `DurtDimens` provided through a `CompositionLocal` and filled from `tokens.json`. Material components are used only where they save work (TextField internals, Switch, ModalBottomSheet). No hex values or magic numbers in screen code.

Naming: the handoff used `Durt*` prefixes. Keep them for now; whether to rename to `Nuddle*` is in `open-questions.md`.

## 1. DurtButton

```kotlin
enum class DurtButtonKind { Primary, Accent, Secondary, Ghost, Danger, DangerOutline, DangerGhost, Soft, Success, OnPoke, OnPokeOutline }
enum class DurtButtonSize(val height: Dp) { Poke(62.dp), Large(56.dp), Medium(52.dp), Small(44.dp) }

@Composable fun DurtButton(
    text: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    kind: DurtButtonKind = DurtButtonKind.Primary, size: DurtButtonSize = DurtButtonSize.Large,
    leadingIcon: ImageVector? = null, enabled: Boolean = true, loading: Boolean = false)
```
- Full width by default. Small and secondary buttons side by side use `weight(1f)`.
- Text 15-16 sp / 600 (Poke: 18 / 700). Corner: Large and Poke 18, others 16.
- Disabled: background `border`, text `textTertiary`. Loading: 20 dp spinner instead of text, width fixed, double taps blocked.
- Pressed: background darkens 8% (no ripple). At most one Primary or Accent per screen.
- Success: `success` background, `surface` text. Used for "Şimdiden bitti" on the next-up card (2f).
- DangerGhost: no background or border, `danger` text. Used for "İptal et" on the pending invite card (1g).

## 2. TopBar and BackBar

```kotlin
@Composable fun TopBar(me: Person, unread: Int, onBell: () -> Unit, onProfile: () -> Unit)
@Composable fun BackBar(title: String?, onBack: () -> Unit, trailing: @Composable (() -> Unit)? = null)
```
- TopBar: logo left (dot in `accent`), right a 44 dp bell (white circle) and a 44 dp avatar. Badge: `poke` background, 2 dp `background` colored ring, "9+" above nine.
- BackBar: 44 dp white circle back button + `h3` title. No title means button only (code, error, info screens).
- Both scroll with content. No Material TopAppBar in the MVP.

## 3. ComposeBar

```kotlin
sealed interface ComposeState { data object Idle; data class Typing(val text: String); data class Failed(val text: String); data class Sending(val text: String); data class Listening(val heard: String) }
@Composable fun ComposeBar(state: ComposeState, onTextChange: (String) -> Unit, onSend: () -> Unit,
    onVoiceStart: () -> Unit, onVoiceEnd: (cancelled: Boolean) -> Unit, notice: VoiceNotice? = null)
```
- Idle: one slim line like a messaging app, 1 dp `border`, text 14 sp, 40 dp orange mic button. Placeholder "Yaz ya da konuş...". (Made smaller in NUD-48: the first phone test found the 52 dp mic and 18 sp text too big.)
- Focused: 2 dp `ink` border, radius 20, text 15 sp, grows to at most 4 lines. With text the mic turns into a send arrow (same place and size, 150 ms crossfade).
- Mic: **tap to dictate** (screen 4h, NUD-48): the transcript is written live into the box, a second tap or silence stops, and the text stays in the box to be fixed and sent with the arrow; nothing is sent by itself. `notice` shows a voice note in a bubble over the mic for 2 s (open-questions #40).
- Listening: the bar stays a bar: a "Dinliyorum" line above it, the transcript in the box with the word still being heard lighter, and the mic button pulses (`voicePulse`) as the stop button.
- Sending: the text stays in the box in `textTertiary`, not editable, and a 20 dp spinner takes the button's place. An empty box while sending looked as if the text was lost, and people sent it again (NUD-54).
- Failed: text is kept, the button becomes "Tekrar dene", an InlineAlert shows above.
- Sits above the keyboard with `imePadding()`.

## 4. TaskRow, StatusPill, HomeTabs

```kotlin
@Composable fun TaskRow(task: TaskUi, onClick: (() -> Unit)?,
    highlighted: Boolean = false, trailing: (@Composable () -> Unit)? = null) // avatar 36 + title + sub line + chevron
@Composable fun StatusPill(status: TaskStatus)
@Composable fun HomeTabs(tab: HomeTab, count: Int, waiting: Boolean, onTab: (HomeTab) -> Unit)
```
- TaskRow: white card, 1 dp `border`, radius 16, padding 12x14. Title 15/600 and sub line 13 `textSecondary`, each one line with an ellipsis, so a long title never moves anything else (NUD-67). The sub line starts with the status in its pill's text color ("Bırakıldı · Not: yarın hallederim"); Kurulu is not written. A chevron when the row opens something; `trailing` replaces it (the ✕ of an "iptal etti" row). `highlighted`: 2 dp `accent` border, for the task that just went out (4i).
- The TaskActionCard with buttons on home is gone since NUD-67: a state that needs a decision is a row, and its buttons are in the task detail.
- StatusPill: 12/600, horizontal padding 10, height 24. Mapping in `screens.md` (Status to UI).
- HomeTabs: a segmented control on `neutralPill`, radius 16, padding 4 (`size.tabs`); each segment 40 high, the selected one `surface` with radius 12. Labels 14/600, `ink` when selected, `textSecondary` otherwise. Bana carries a count badge (`ink` fill, `surface` text, `micro`), Gönderdiklerim the 8 dp `poke` dot while something waits for an answer. A horizontal drag longer than `size.tabs.swipe` switches; the content slides in `motion` medium.

## 5. SettingsGroup, LineRow, ToggleRow, ChoiceChip

```kotlin
@Composable fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit)
@Composable fun LineRow(label: String, value: String? = null, leading: @Composable (() -> Unit)? = null,
    valueColor: Color = DurtTheme.colors.ink, onClick: (() -> Unit)? = null)
@Composable fun ToggleRow(label: String, description: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit)
@Composable fun ChoiceChip(text: String, selected: Boolean, onClick: () -> Unit)
```
- SettingsGroup: `overline` title, white box 6 dp below (radius 16, horizontal padding 16). 1 dp `surfaceMuted` dividers between rows, none after the last.
- LineRow 52 dp. With `onClick`: chevron on the right, whole row clickable.
- ToggleRow 64 dp, switch 52x32: on `success`, off `borderStrong`, 26 dp white thumb. Whole row clickable, `Role.Switch`.
- ChoiceChip 40 dp, 1.5 border: selected `ink` fill with white text, unselected white with `borderStrong`. Single choice, a second tap clears. Used for the relationship choice on the invite screen.

## 6. UndoToast, DoneNote, InlineAlert, StatusBanner

```kotlin
@Composable fun UndoToast(title: String, subtitle: String, durationMs: Long = 5_000, onUndo: () -> Unit, onEdit: () -> Unit, onTimeout: () -> Unit)
@Composable fun ReplyNote(text: String, onTimeout: () -> Unit)
@Composable fun DoneNote(title: String, learning: String?)
@Composable fun InlineAlert(title: String, body: String, icon: ImageVector = DurtIcons.Warning)
@Composable fun StatusBanner(text: String)
```
- Toasts: `ink` background, radius 18, 10 dp above the compose box, slide up in 200 ms. No Material Snackbar.
- UndoToast: 3 dp orange progress line filling over 5 s. Timeout confirms the task. Undo and Edit 40 dp, background #2C333B, tick #7FD8C3. 10 s and focus moves to it when TalkBack is on.
- ReplyNote: the UndoToast's box and place without buttons or progress, the reply in 15 sp white. Stays 8 s (16 s with TalkBack, and focus moves to it), or until the bar is focused or a new text is sent. Announced as a polite live region (NUD-52).
- DoneNote: 4 s after a task completes, optional second line with the learning ("Bu sefer seni 2. dürtmede yakaladım").
- InlineAlert: `warningSoft` background, `warningBorder` border, radius 16. Stays inside the screen, never auto-dismisses.
- StatusBanner: ongoing states such as offline, right below the TopBar.

## 7. InboxItem and PokeNotification

- `InboxItem(icon, title, subtitle, time, unread, onClick)`: 36 dp icon circle; unread means title 600 and an 8 dp `poke` dot on the right. The bell groups items as "Bugün / Dün / Daha önce" with SettingsGroup.
- Warning entries (permission off, poke did not ring) are cards with a full width "Düzelt" button (screen 2g).
- PokeNotification is not Compose. Built with `NotificationCompat`: title = the step's poke title (glossary.md, Poke titles; a one-time message keeps the message), text = task title (with the detail), the expanded text adds the time to the deadline, large icon = sender avatar bitmap, at most three actions: Bitti, Ertele, Bugün olmaz. Channels in `platform.md`.

## 8. DurtBottomSheet

```kotlin
@Composable fun DurtBottomSheet(title: String, description: String?, onDismiss: () -> Unit,
    primary: SheetAction, content: @Composable ColumnScope.() -> Unit = {})
```
- On Material3 `ModalBottomSheet`: top corners 26, handle 40x5 `borderStrong`, scrim `ink` 50%, padding 12 / 20 / 24.
- Fixed order: handle, `h2` title, description, content, 56 dp primary action, 44 dp "Vazgeç". Swipe down and scrim tap mean Vazgeç.
- Destructive actions (cancel, delete account) use `Danger` for the primary button. Used on 2b, 7d, 7f, 7g, 7h, 7i.

## 9. Avatar, CodeInput, PermissionStep

- `Avatar(name, color, size, image?)`: the person's picture when they set one (NUD-72, a circle-cropped bitmap), else the first letter, Space Grotesk 700, about 40% of the size. The user's own avatar is always `ink`. Pictures come from `people.avatar_path` through a signed URL and are cached on the phone per path (`AvatarFiles`); `LocalAvatars` hands a composable the bitmap for a person id.
- `CodeInput(length = 6, value, error, onComplete)`: 6 boxes, active box 2 dp `ink`; on error all boxes get `poke` border and `highlightSoft` fill. SMS Retriever autofill, submits on the 6th digit.
- `PermissionStep(index, title, state: Done | Active | Next, badge?, description?, hint?, action?)`: Done shows a green tick and "Tamam"; Active has a 2 dp `ink` border, expands with description, the gray hint box ("**Açılan sayfada** ...") and "Ayarları aç" (or `action`, e.g. "İzin ver" for the system dialog); Next is dashed and gray.

## 10. SlideToConfirm

```kotlin
@Composable fun SlideToConfirm(text: String, accessibilityLabel: String, onConfirm: () -> Unit, modifier: Modifier = Modifier)
```
- A deliberate action for a screen that may be touched by accident. Used for Bitti on the full screen poke over the lock screen (3b, NUD-131).
- Track: full width, at least `size.slide.height` (the Poke button's 62), radius pill, `pokeDeep` fill. Thumb: `size.slide.thumb` circle in `surface` with the `pokeDeep` tick, inset `size.slide.inset` from the track's edge. The label is `buttonPoke` in `surface`, centered in the room right of the thumb, and fades as the thumb moves; it wraps and the track grows with the font (200%).
- A horizontal drag anywhere on the track moves the thumb. Released past `size.slide.confirm` of the way, the thumb goes to the end and `onConfirm` runs once; otherwise it springs back in `motion` medium. A tap does nothing.
- Accessibility: one `Role.Button` node named `accessibilityLabel` (the plain verb, "Bitti") whose click action confirms, so TalkBack and Switch Access never drag.
- States with previews: idle, dragging, done.

## Screen-specific parts

Parts used on one screen stay in that screen's file: full screen poke (3b, 2d), voice recording overlay (4h), camera (4d), welcome notification stack illustration (0a), quiet hours picker (7g, system `TimePicker`).

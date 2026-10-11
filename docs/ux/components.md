# Components

About 20 Compose components cover every screen. All live in `ui/components`, are stateless (state in, events out as lambdas), and each has at least one `@Preview` (enforced by Konsist). Visual reference: `screens/Components.png` (canvas board "8 · Bileşen kataloğu").

Theme: Material 3 `MaterialTheme` as a base, with our own `DurtColors`, `DurtType`, `DurtDimens` provided through a `CompositionLocal` and filled from `tokens.json`. Material components are used only where they save work (TextField internals, Switch, ModalBottomSheet). No hex values or magic numbers in screen code.

Naming: the handoff used `Durt*` prefixes. Keep them for now; whether to rename to `Nuddle*` is in `open-questions.md`.

## 1. DurtButton

```kotlin
enum class DurtButtonKind { Primary, Accent, Secondary, Ghost, Danger, DangerOutline, Soft, Success, OnPoke, OnPokeOutline }
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
sealed interface ComposeState { data object Idle; data class Typing(val text: String); data class Failed(val text: String); data object Sending }
@Composable fun ComposeBar(state: ComposeState, onTextChange: (String) -> Unit, onSend: () -> Unit,
    onVoiceStart: () -> Unit, onVoiceEnd: (cancelled: Boolean) -> Unit)
```
- Idle: one line, 1 dp `border`, 52 dp orange mic button. Placeholder "Yaz ya da basılı tutup konuş...".
- Focused: 2 dp `ink` border, radius 20, text 18 sp, grows to at most 4 lines. With text the mic turns into a send arrow (same place and size, 150 ms crossfade).
- Mic: hold to record (screen 4h), release to send, slide to cancel. A short tap shows a "Basılı tut" hint bubble for 2 s.
- Failed: text is kept, the button becomes "Tekrar dene", an InlineAlert shows above.
- Sits above the keyboard with `imePadding()`.

## 4. TaskRow, TaskActionCard, StatusPill

```kotlin
@Composable fun TaskRow(task: TaskUi, onClick: () -> Unit)            // avatar 36 + title + sub line + pill
@Composable fun TaskActionCard(task: TaskUi, message: String,
    primary: Action, secondary: Action?)                              // avatar 32, message box, 1-2 actions
@Composable fun StatusPill(status: TaskStatus)
```
- TaskRow: white card, 1 dp `border`, radius 16, padding 12x14. Title 15/600 single line with ellipsis. Sub line 13 `textSecondary`: "Person · deadline" or a status sentence.
- TaskActionCard: for states that need a decision (missed, dropped, awaiting approval). Radius 20, message box `background` radius 12, action buttons 42 dp.
- StatusPill: 12/600, horizontal padding 10, height 24. Mapping in `screens.md` (Status to UI).

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
@Composable fun DoneNote(title: String, learning: String?)
@Composable fun InlineAlert(title: String, body: String, icon: ImageVector = DurtIcons.Warning)
@Composable fun StatusBanner(text: String)
```
- Toasts: `ink` background, radius 18, 10 dp above the compose box, slide up in 200 ms. No Material Snackbar.
- UndoToast: 3 dp orange progress line filling over 5 s. Timeout confirms the task. Undo and Edit 40 dp, background #2C333B, tick #7FD8C3. 10 s and focus moves to it when TalkBack is on.
- DoneNote: 4 s after a task completes, optional second line with the learning ("Bu sefer seni 2. dürtmede yakaladım").
- InlineAlert: `warningSoft` background, `warningBorder` border, radius 16. Stays inside the screen, never auto-dismisses.
- StatusBanner: ongoing states such as offline, right below the TopBar.

## 7. InboxItem and PokeNotification

- `InboxItem(icon, title, subtitle, time, unread, onClick)`: 36 dp icon circle; unread means title 600 and an 8 dp `poke` dot on the right. The bell groups items as "Bugün / Dün / Daha önce" with SettingsGroup.
- Warning entries (permission off, poke did not ring) are cards with a full width "Düzelt" button (screen 2g).
- PokeNotification is not Compose. Built with `NotificationCompat`: title = task title, text = "From · time to deadline", large icon = sender avatar bitmap, at most three actions: Bitti, Ertele, Bugün olmaz. Channels in `platform.md`.

## 8. DurtBottomSheet

```kotlin
@Composable fun DurtBottomSheet(title: String, description: String?, onDismiss: () -> Unit,
    primary: SheetAction, content: @Composable ColumnScope.() -> Unit = {})
```
- On Material3 `ModalBottomSheet`: top corners 26, handle 40x5 `borderStrong`, scrim `ink` 50%, padding 12 / 20 / 24.
- Fixed order: handle, `h2` title, description, content, 56 dp primary action, 44 dp "Vazgeç". Swipe down and scrim tap mean Vazgeç.
- Destructive actions (cancel, delete account) use `Danger` for the primary button. Used on 2b, 7d, 7f, 7g, 7h, 7i.

## 9. Avatar, CodeInput, PermissionStep

- `Avatar(name, color, size)`: first letter, Space Grotesk 700, about 40% of the size. No photos in the MVP. The user's own avatar is always `ink`.
- `CodeInput(length = 6, value, error, onComplete)`: 6 boxes, active box 2 dp `ink`; on error all boxes get `poke` border and `highlightSoft` fill. SMS Retriever autofill, submits on the 6th digit.
- `PermissionStep(index, title, state: Done | Active | Next, badge?)`: Done shows a green tick and "Tamam"; Active has a 2 dp `ink` border, expands with description and "Ayarları aç"; Next is dashed and gray.

## Screen-specific parts

Parts used on one screen stay in that screen's file: full screen poke (3b, 2d), voice recording overlay (4h), camera (4d), welcome notification stack illustration (0a), quiet hours picker (7g, system `TimePicker`).

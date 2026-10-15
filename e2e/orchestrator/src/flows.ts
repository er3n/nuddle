// What a person does in the app, step by step (ADR 0019): one function per thing the orchestrator needs to observe in
// between. Elements are found by test tag (apps/android ui/TestTags.kt); text is matched only to check copy. Push and
// alarms can be slow, so a flow waits for what it needs, never for a fixed time.
import type { Device } from "./devices";
import type { Target } from "./ui";

/** Test OTP code of the local stack (supabase/config.toml, auth.sms.test_otp). */
const CODE = "123456";

/** Text that is exactly [s]. */
const text = (s: string): Target => ({
  text: new RegExp(`^${s.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")}$`),
});

/** Text that contains [s]. */
const textWith = (s: string): Target => ({
  text: new RegExp(s.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")),
});

/** Welcome (0a), number (0c), code (0d), name (0e): Sibel signs up until [done] is on screen. */
export async function signUp(d: Device, phone: string, name: string, done: string): Promise<void> {
  await d.openApp();
  await d.tap("welcome.start");
  await d.type("phone.input", phone);
  await d.hideKeyboard();
  await d.tap("phone.send");
  await d.see("code.input");
  // The code submits itself when complete; the next screen is the check.
  await d.type("code.input", CODE, false);
  await d.see("name.input");
  await d.type("name.input", name);
  await d.hideKeyboard();
  await d.tap("name.continue");
  await d.see(done, 60_000);
}

/** Eren opened Sibel's invite link: the invited welcome (0b), his number and code, the name she gave him (0e). */
export async function signUpFromLink(d: Device, phone: string): Promise<void> {
  await d.see("welcome.inviter");
  await d.see(text("Sibel seni dürt'e davet etti"));
  await d.tap("welcome.start");
  await d.type("phone.input", phone);
  await d.hideKeyboard();
  await d.tap("phone.send");
  await d.see("code.input");
  // The code submits itself when complete; the next screen is the check.
  await d.type("code.input", CODE, false);
  const name = await d.see("name.input");
  if (name.text !== "Eren") throw new Error(`eren: the name field says "${name.text}", not "Eren"`);
  // The hint under the field ("Sibel seni "Eren" diye eklemişti") is not in UI Automator's dump; its copy is checked
  // by SignInScreensTest, and the prefilled name shows the invite reached this screen.
  await d.hideKeyboard();
  await d.tap("name.continue");
  await d.see("consent.screen", 60_000);
}

/** İzin ver on the consent screen (0f), then matched (0g), then home. */
export async function consent(d: Device): Promise<void> {
  await d.see("consent.screen", 60_000);
  await d.tap("consent.allow");
  await d.tap("matched.start");
  await d.see("home.screen");
}

/** Sibel invites someone from home by typing the number (1e, 1f); her pending invite (1g) is on home after. */
export async function invite(
  d: Device,
  name: string,
  phone: string,
  relationship: string,
): Promise<void> {
  await d.tap("home.invite");
  await d.tap("invite.typeNumber");
  await d.type("invite.name", name);
  await d.type("invite.number", phone);
  await d.hideKeyboard();
  await d.tap(`invite.relationship.${relationship}`);
  await d.tap("invite.send");
  await d.gone("invite.screen");
  // The share sheet with the link.
  await d.back();
  await d.see("home.pendingInvite");
}

/** The invite was accepted: the pending invite and the invite card leave home. */
export async function inviteAccepted(d: Device): Promise<void> {
  await d.relaunch();
  await d.gone("home.pendingInvite", 60_000);
  await d.see("home.screen");
  await d.gone("home.invite", 5_000);
}

/** Writes [what] in the compose bar; the fixture extractor makes it a task to self, next up on home (2f). */
export async function writeTask(d: Device, what: string): Promise<void> {
  await d.see("home.screen");
  await d.type("compose.input", what);
  await d.tap("compose.send");
  await d.see("home.nextUp");
  await d.see("home.undo", 5_000);
}

/** Writes [what] in the compose bar; the fixture extractor makes it a task to someone, a row in Gönderdiklerim (4i). */
export async function writeTaskToOther(d: Device, what: string): Promise<void> {
  await d.see("home.screen");
  await d.type("compose.input", what);
  await d.tap("compose.send");
  await d.see("home.sent");
  await d.see("home.undo", 5_000);
}

/** The full screen poke (3b) is open. */
export async function waitPoke(d: Device): Promise<void> {
  await d.see("poke.done", 60_000);
}

/** Bitti on the full screen poke; it closes. */
export async function pokeDone(d: Device): Promise<void> {
  await d.tap("poke.done");
  await d.gone("poke.screen", 15_000);
}

/** Bugün yapamayacağım on the full screen poke, "Yarın hallederim", Notla gönder (7f); the poke closes. */
export async function pokeCantToday(d: Device): Promise<void> {
  await d.taps("poke.cantToday", "drop.reason.Tomorrow", "sheet.primary");
  await d.gone("poke.screen", 15_000);
}

/** Home after the task to self is done: no next-up card. */
export async function homeDone(d: Device): Promise<void> {
  await d.relaunch();
  await d.see("home.screen");
  await d.gone("home.nextUp", 15_000);
}

/** Eren's home after Sibel's task arrived (4c): the next-up card opens the task, whose detail has Ara and Mesaj. */
export async function homeFromSibel(d: Device): Promise<void> {
  await d.tap("home.nextUp", 60_000);
  await d.see("task.screen");
  await d.see("task.call", 5_000);
  await d.see("task.message", 5_000);
  await d.back();
  await d.see("home.nextUp");
}

/** Sibel opens the task she sent (7c) and cancels it, "Ben hallettim" (7d); its row leaves Gönderdiklerim. */
export async function cancelTask(d: Device): Promise<void> {
  await d.gone("home.undo");
  await d.tap("home.tab.sent");
  await d.tap("home.sent");
  await d.see("task.screen");
  await d.taps("task.cancel", "task.cancelReason.HandledIt", "sheet.primary");
  await d.see("home.screen");
  await d.gone("home.sent", 10_000);
}

/** Bitti on a photo task opened the camera (4d): Çek, Gönder, home with the task waiting for approval. */
export async function photoSend(d: Device): Promise<void> {
  await d.see("photo.screen");
  await d.tap("photo.shoot");
  await d.tap("photo.send", 15_000);
  await d.see("home.screen");
  await d.see("home.nextUp", 5_000);
}

/** Sibel's row in Gönderdiklerim says the photo waits for her; she opens it (4f) and approves; the row goes. */
export async function approve(d: Device): Promise<void> {
  await d.relaunch();
  await d.tap("home.tab.sent");
  await d.see(textWith("Onayını bekliyor"), 60_000);
  await d.tap("home.sent");
  await d.see("approve.screen");
  await d.tap("approve.ok");
  await d.see("home.screen");
  await d.gone("home.sent", 10_000);
}

/**
 * The dropped row with Eren's note (5d) in Gönderdiklerim; its detail, Yeni zaman ver, tomorrow morning, Kaydet; the
 * task is going again, so back home it is a row under "Devam eden".
 */
export async function newTime(d: Device): Promise<void> {
  await d.relaunch();
  await d.tap("home.tab.sent");
  await d.see(textWith("Not: Yarın hallederim"), 60_000);
  await d.tap("home.sentIssue");
  await d.see(text("Bırakıldı"), 5_000);
  await d.tap("task.action.NewTime");
  await d.tap("preview.morning", 15_000);
  await d.tap("sheet.primary");
  await d.gone("task.action.NewTime");
  await d.back();
  await d.gone("home.sentIssue");
  await d.see("home.sent");
}

/** Eren removes Sibel on her person screen (profile, person, Bağlantıyı kaldır, confirm); she leaves his profile. */
export async function removeConnection(d: Device): Promise<void> {
  await d.relaunch();
  await d.tap("bar.profile");
  await d.tap("profile.person.Sibel");
  await d.see("person.screen");
  await d.scrollTo("person.remove");
  await d.tap("person.remove");
  await d.see(text("Bağlantı kaldırılsın mı?"));
  await d.tap("sheet.primary");
  await d.see("profile.screen");
  await d.gone("profile.person.Sibel");
}

/** After a force stop: the bell says "Son dürtmen çalmadı" (2g) and Düzelt opens the brand steps (2h). */
export async function missedRing(d: Device): Promise<void> {
  await d.relaunch();
  await d.tap("bar.bell");
  await d.see("inbox.missedRing", 15_000);
  await d.see(text("Son dürtmen çalmadı"), 5_000);
  await d.tap("inbox.missedRing.fix");
  await d.see("missedRing.screen", 15_000);
  await d.see(text("Pil kullanımı: Kısıtlanmamış"), 15_000);
  await d.see("missedRing.open", 5_000);
}

/** Offline (6a): the banner, and a text written now waits as "Sırada". */
export async function offlineWrite(d: Device, what: string): Promise<void> {
  await d.relaunch();
  await d.see("home.offline");
  await d.type("compose.input", what);
  await d.tap("compose.send");
  await d.see("home.queued");
}

/** Back online: the banner and the queued text go, the task is on home. */
export async function offlineSent(d: Device, title: string): Promise<void> {
  await d.gone("home.offline");
  await d.gone("home.queued");
  await d.see(text(title));
}

/** The mic (4h): the injected speech goes into the box, the send arrow creates the task. */
export async function voiceTask(d: Device): Promise<void> {
  await d.relaunch();
  await d.tap("compose.mic");
  await d.tap("compose.send");
  await d.see("home.undo");
}

// Extractor fixtures for the scenario texts. Scenarios start Sunday 2026-10-11 20:00 Istanbul.
import type { PersonProfiler, PokePlanner } from "../../src/domain/ports";
import type { FixtureExtraction } from "../../src/infra/llm/fixture-extractor";
import { World, type Client } from "./harness";

export const TEXT = {
  photoToEren: "eren yarın 9dan önce temizlikçi için 1000 çeksin",
  trashToEren: "eren yarın akşam çöpü atsın",
  selfSport: "yarın sabah 8de spor yap",
  earlyParcelToEren: "eren yarın sabah 6ya kadar kargoyu kapıya koysun",
  breadToSibel: "sibel yarın akşam ekmek alsın",
  homeworkToDefne: "defne yarın akşam ödevini bitirsin",
  waterSelfAt: "2 dakika sonra su iç",
  dishesToEren: "eren yarın gece 10a kadar bulaşıkları yıkasın",
  pickupKidsAt: "eren saat 5.30da çocukları okuldan alsın, yürüyüş 20 dakika sürüyor",
  hello: "merhaba",
  dailyMedicineToEren: "eren her gün 9da ilacını alsın",
  weeklyTrashToEren: "eren her salı akşam 7de çöpü çıkarsın",
  loveToEren: "erene 3te seni seviyorum de",
  dinnerToEren: "erene yemek hazır de",
  kissToErenInAMinute: "bir dakika sonra erene öptüm de",
  weekdaysWakeSelf: "beni hafta içi her gün 7'de kaldır",
  earlyWakeSelf: "yarın sabah beni 5.00'te uyandır",
  earlyWakeEren: "eren yarın sabah 5te kalksın",
  callMomSelf: "annemi ara",
  callMomTomorrowSelf: "yarın annemi ara",
  callMeEren: "eren yarın akşam beni arasın",
  messageAyseEren: "eren yarın akşam ayşeye mesaj atsın",
  callTeacherSelf: "yarın öğretmeni ara",
} as const;

const fixtures: Record<string, FixtureExtraction> = {
  // A task about calling or messaging someone (NUD-128). Ayşe is Sibel's mother.
  [TEXT.callMomSelf]: {
    recipient: "self",
    title: "Annemi ara",
    detail: null,
    deadline: null,
    finishMode: "done",
    missing: ["deadline"],
    contact: { name: "Ayşe", action: "call" },
  },
  [TEXT.callMomTomorrowSelf]: {
    recipient: "self",
    title: "Annemi ara",
    detail: null,
    deadline: "2026-10-12T19:00:00+03:00",
    finishMode: "done",
    missing: [],
    contact: { name: "Ayşe", action: "call" },
  },
  [TEXT.callMeEren]: {
    recipient: "Eren",
    title: "Beni ara",
    detail: null,
    deadline: "2026-10-12T19:00:00+03:00",
    finishMode: "done",
    missing: [],
    contact: { name: "sender", action: "call" },
  },
  [TEXT.messageAyseEren]: {
    recipient: "Eren",
    title: "Ayşe'ye mesaj at",
    detail: null,
    deadline: "2026-10-12T19:00:00+03:00",
    finishMode: "done",
    missing: [],
    contact: { name: "Ayşe", action: "message" },
  },
  // The model named someone who is no connection: the task has no contact.
  [TEXT.callTeacherSelf]: {
    recipient: "self",
    title: "Öğretmeni ara",
    detail: null,
    deadline: "2026-10-12T19:00:00+03:00",
    finishMode: "done",
    missing: [],
    contact: { name: "Öğretmen", action: "call" },
  },
  [TEXT.hello]: {
    recipient: null,
    title: null,
    detail: null,
    deadline: null,
    finishMode: "done",
    missing: [],
    reply: "Merhaba! Kime ne hatırlatayım?",
  },
  [TEXT.photoToEren]: {
    recipient: "Eren",
    title: "Temizlikçi için para çek",
    detail: "1.000 TL",
    deadline: "2026-10-12T09:00:00+03:00",
    finishMode: "photo",
    missing: [],
  },
  [TEXT.trashToEren]: {
    recipient: "Eren",
    title: "Çöpü at",
    detail: null,
    deadline: "2026-10-12T19:00:00+03:00",
    finishMode: "done",
    missing: [],
  },
  [TEXT.selfSport]: {
    recipient: "self",
    title: "Spor yap",
    detail: null,
    deadline: "2026-10-12T08:00:00+03:00",
    finishMode: "done",
    missing: [],
  },
  [TEXT.earlyParcelToEren]: {
    recipient: "Eren",
    title: "Kargoyu kapıya koy",
    detail: null,
    deadline: "2026-10-12T06:00:00+03:00",
    finishMode: "done",
    missing: [],
  },
  [TEXT.breadToSibel]: {
    recipient: "Sibel",
    title: "Ekmek al",
    detail: null,
    deadline: "2026-10-12T19:00:00+03:00",
    finishMode: "done",
    missing: [],
  },
  // A moment, not a deadline (ADR 0017).
  [TEXT.pickupKidsAt]: {
    recipient: "Eren",
    title: "Çocukları okuldan al",
    detail: null,
    deadline: "2026-10-12T17:30:00+03:00",
    finishMode: "done",
    missing: [],
    timing: "at",
    missAfterMinutes: 15,
    leadMinutes: 20,
  },
  [TEXT.waterSelfAt]: {
    recipient: "self",
    title: "Su iç",
    detail: null,
    deadline: "2026-10-11T20:02:00+03:00",
    timing: "at",
    missAfterMinutes: 30,
    finishMode: "done",
    missing: [],
  },
  [TEXT.dishesToEren]: {
    recipient: "Eren",
    title: "Bulaşıkları yıka",
    detail: null,
    deadline: "2026-10-12T22:00:00+03:00",
    finishMode: "done",
    missing: [],
  },
  // Repeating tasks (ADR 0021): the first occurrence is the next matching time after Sunday 20:00.
  [TEXT.dailyMedicineToEren]: {
    recipient: "Eren",
    title: "İlacını al",
    detail: null,
    deadline: "2026-10-12T09:00:00+03:00",
    timing: "at",
    missAfterMinutes: 120,
    finishMode: "done",
    missing: [],
    repeat: { every: "day", time: "09:00" },
  },
  [TEXT.weeklyTrashToEren]: {
    recipient: "Eren",
    title: "Çöpü çıkar",
    detail: null,
    deadline: "2026-10-13T19:00:00+03:00",
    finishMode: "done",
    missing: [],
    repeat: { every: "week", days: [2], time: "19:00" },
  },
  // The model's first occurrence is wrong (today 07:00 is long past on Sunday 20:00, NUD-83);
  // the server computes it from the rule: Monday 07:00.
  [TEXT.weekdaysWakeSelf]: {
    recipient: "self",
    title: "Kalk",
    detail: null,
    deadline: "2026-10-11T07:00:00+03:00",
    timing: "at",
    missAfterMinutes: 15,
    finishMode: "done",
    missing: [],
    repeat: { every: "weekdays", time: "07:00" },
  },
  // A one-time message (NUD-79): 15:00 has passed on Sunday 20:00, so tomorrow 15:00.
  [TEXT.loveToEren]: {
    recipient: "Eren",
    title: "Seni seviyorum",
    detail: null,
    deadline: "2026-10-12T15:00:00+03:00",
    timing: "at",
    missAfterMinutes: 60,
    finishMode: "notice",
    missing: [],
  },
  // No time: no deadline and nothing missing, so it goes out at once (NUD-140).
  [TEXT.dinnerToEren]: {
    recipient: "Eren",
    title: "Yemek hazır",
    detail: null,
    deadline: null,
    timing: "at",
    missAfterMinutes: 60,
    finishMode: "notice",
    missing: [],
  },
  // A time a minute away rings at that time, not at once (NUD-140).
  [TEXT.kissToErenInAMinute]: {
    recipient: "Eren",
    title: "Öptüm",
    detail: null,
    deadline: "2026-10-11T20:01:00+03:00",
    timing: "at",
    missAfterMinutes: 60,
    finishMode: "notice",
    missing: [],
  },
  // Inside quiet hours (NUD-120): the preview asks before the task exists.
  [TEXT.earlyWakeSelf]: {
    recipient: "self",
    title: "Uyan",
    detail: null,
    deadline: "2026-10-12T05:00:00+03:00",
    timing: "at",
    missAfterMinutes: 15,
    finishMode: "done",
    missing: [],
  },
  [TEXT.earlyWakeEren]: {
    recipient: "Eren",
    title: "Kalk",
    detail: null,
    deadline: "2026-10-12T05:00:00+03:00",
    timing: "at",
    missAfterMinutes: 15,
    finishMode: "done",
    missing: [],
  },
  [TEXT.homeworkToDefne]: {
    recipient: "Defne",
    title: "Ödevini bitir",
    detail: null,
    deadline: "2026-10-12T19:00:00+03:00",
    finishMode: "done",
    missing: [],
  },
};

/** Sibel and Eren, signed up and connected as spouses. */
export async function sibelAndEren(
  options: { planner?: PokePlanner; profiler?: PersonProfiler } = {},
): Promise<{ world: World; sibel: Client; eren: Client }> {
  const world = new World(fixtures, options);
  const sibel = await world.addPerson("Sibel");
  const eren = await world.addPerson("Eren");
  await world.connect(sibel, eren, "spouse", "spouse");
  return { world, sibel, eren };
}

export function emptyWorld(): World {
  return new World(fixtures);
}

/** Write a text and return the created task id; fails the test otherwise. */
export async function writeTask(client: Client, text: string): Promise<string> {
  const r = await client.write(text);
  if (r.kind !== "created") throw new Error(`expected a created task, got ${JSON.stringify(r)}`);
  return r.taskId;
}

export const at = (localIstanbul: string): Date => new Date(`${localIstanbul}+03:00`);

/** "HH:MM" Istanbul time of an instant, for readable assertions. */
export const hhmm = (t: Date): string =>
  new Intl.DateTimeFormat("en-GB", {
    timeZone: "Europe/Istanbul",
    hour: "2-digit",
    minute: "2-digit",
  }).format(t);

// Extractor fixtures for the scenario texts. Scenarios start Sunday 2026-10-11 20:00 Istanbul.
import type { Extraction } from "../../src/domain/extraction";
import { World, type Client } from "./harness";

export const TEXT = {
  photoToEren: "eren yarın 9dan önce temizlikçi için 1000 çeksin",
  trashToEren: "eren yarın akşam çöpü atsın",
  selfSport: "yarın sabah 8de spor yap",
  earlyParcelToEren: "eren yarın sabah 6ya kadar kargoyu kapıya koysun",
  breadToSelin: "selin yarın akşam ekmek alsın",
  homeworkToDefne: "defne yarın akşam ödevini bitirsin",
} as const;

const fixtures: Record<string, Extraction> = {
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
  [TEXT.breadToSelin]: {
    recipient: "Selin",
    title: "Ekmek al",
    detail: null,
    deadline: "2026-10-12T19:00:00+03:00",
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

/** Selin and Eren, signed up and connected as spouses. */
export async function selinAndEren(): Promise<{ world: World; selin: Client; eren: Client }> {
  const world = new World(fixtures);
  const selin = world.addPerson("Selin");
  const eren = world.addPerson("Eren");
  await world.connect(selin, eren, "spouse", "spouse");
  return { world, selin, eren };
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

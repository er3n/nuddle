// LLM task extraction: the provider-neutral input/output and the post-processing that decides
// between creating the task at once and asking exactly one question (docs/domain.md).
import { nextOccurrenceAt, repeatRuleOf, textSaysRepeat, type RepeatRule } from "./repeat";
import type { EdgeType, FinishMode, PersonId, Timing } from "./types";

/**
 * How the sender reads dates and times (NUD-132): the language the title and the reply are in,
 * the first day of their week and their clock.
 */
export interface SenderFormats {
  /** BCP 47 tag, e.g. "tr-TR". */
  locale: string;
  weekStart: "monday" | "sunday";
  hour24: boolean;
}

/**
 * The app is Turkish only until the English strings ship, and the phone does not send its locale,
 * so every sender reads Turkish, a week from Monday and a 24-hour clock.
 * TODO(open-questions#56): take the phone's locale, week start and clock once English ships.
 */
export const TURKISH_FORMATS: SenderFormats = {
  locale: "tr-TR",
  weekStart: "monday",
  hour24: true,
};

/**
 * What the extractor is given (docs/domain.md, LLM task extraction). Only the sender's own
 * connections, each with the name and the relationship the sender picked; never a phone number,
 * an id or anything about people outside them.
 */
export interface ExtractionInput {
  text: string;
  /** The sender's local time comes from `now` and `timeZone`. */
  now: Date;
  timeZone: string;
  formats: SenderFormats;
  senderName: string;
  /** `relationship` is what the person is to the sender ("Annem" is `parent`). */
  connections: readonly { name: string; relationship: EdgeType }[];
}

export type MissingField = "recipient" | "title" | "deadline";

export interface Extraction {
  /** "self", a connection name as given in the input, or null when unclear. */
  recipient: string | null;
  title: string | null;
  detail: string | null;
  /** ISO 8601 with offset, or null when the text gives no time. For an `at` task the moment. */
  deadline: string | null;
  /** `at` for a moment ("2 dakika sonra", "9'da"), `by` for a deadline ("9'a kadar"). ADR 0017. */
  timing: Timing;
  /** For an `at` task: how long after the moment it still makes sense. Null for `by` or unknown. */
  missAfterMinutes: number | null;
  /** For an `at` task: how long before the moment the person must start. Null for `by` or unknown. */
  leadMinutes?: number | null;
  finishMode: FinishMode;
  /** A repeating task's rule (ADR 0021); null or absent for a one-off. The deadline is then the first occurrence. */
  repeat?: RepeatRule | null;
  missing: MissingField[];
  /**
   * One short sentence when the text is not a reminder task at all (a greeting, small talk, a
   * question about the app); null for a task. Optional so fixtures written before NUD-52 stay tasks.
   */
  reply?: string | null;
}

export type Resolution =
  | {
      kind: "create";
      recipientId: PersonId;
      title: string;
      detail: string | null;
      /** The picked time: the moment of an `at` task. */
      deadline: Date;
      timing: Timing;
      missAfterMinutes: number | null;
      leadMinutes: number | null;
      finishMode: FinishMode;
      /** The rule of a repeating task (ADR 0021); null for a one-off. */
      repeat: RepeatRule | null;
    }
  | { kind: "ask"; question: MissingField }
  | { kind: "chat"; reply: string };

const normalize = (s: string): string => s.trim().toLocaleLowerCase("tr");

/**
 * The words a sender uses for a connection of each relationship, without a suffix: the
 * nicknames, since the app keeps no nickname of its own (NUD-132). Glossary: "Senin neyin?".
 */
export const RELATIONSHIP_WORDS: Readonly<Record<EdgeType, readonly string[]>> = {
  spouse: ["eşim", "karım", "kocam", "hanımım"],
  parent: ["annem", "babam"],
  child: ["oğlum", "kızım", "çocuğum"],
  sibling: ["kardeşim", "ablam", "abim", "ağabeyim"],
  roommate: ["ev arkadaşım"],
  other: [],
};

type Connection = { personId: PersonId; name: string; relationship?: EdgeType };

const wordsOf = (text: string): string[] =>
  text
    .split(/\s+/)
    .map((w) => normalize(w.replace(/^[.,!?;:"]+|[.,!?;:"]+$/g, "")))
    .filter((w) => w !== "");

/** The one connection with that relationship, or null when none or more than one has it. */
function onlyOfType(
  types: ReadonlySet<EdgeType>,
  connections: readonly Connection[],
): PersonId | null {
  const of = connections.filter((c) => c.relationship !== undefined && types.has(c.relationship));
  return of.length === 1 && of[0] ? of[0].personId : null;
}

/** The relationships whose word is the whole of `phrase` ("Annem", "parent"). */
function typesNamedBy(phrase: string): Set<EdgeType> {
  const p = normalize(phrase);
  return new Set(
    (Object.keys(RELATIONSHIP_WORDS) as EdgeType[]).filter(
      (t) => t === p || RELATIONSHIP_WORDS[t].includes(p),
    ),
  );
}

/**
 * The relationships named in the text by a word without a suffix ("annem yarın ilacını alsın").
 * "annemi ara", "anneme 500 gönder" name the person as an object, so they do not count.
 */
function typesNamedInText(text: string): Set<EdgeType> {
  const words = wordsOf(text);
  const found = new Set<EdgeType>();
  for (const t of Object.keys(RELATIONSHIP_WORDS) as EdgeType[]) {
    for (const phrase of RELATIONSHIP_WORDS[t]) {
      const parts = phrase.split(" ");
      if (words.some((_, i) => parts.every((part, j) => words[i + j] === part))) found.add(t);
    }
  }
  return found;
}

/**
 * A repeating task's first occurrence comes from the rule, not from the model (ADR 0021): the
 * model wrote "today 07:00" at 07:23 and the server then asked for a time. When the text says the
 * task repeats and the rule is sound, the deadline becomes the rule's next time after now and
 * "deadline" leaves `missing`; a one-off, or a rule the text does not back, is returned as is.
 */
export function withFirstOccurrence(
  e: Extraction,
  text: string,
  now: Date,
  timeZone: string,
): Extraction {
  const repeat = textSaysRepeat(text) ? repeatRuleOf(e.repeat) : null;
  if (repeat === null) return { ...e, repeat: null };
  return {
    ...e,
    repeat,
    deadline: nextOccurrenceAt(repeat, now, timeZone).toISOString(),
    missing: e.missing.filter((m) => m !== "deadline"),
  };
}

/**
 * The relationships named in the text by a word with or without a suffix ("annem", "anneme",
 * "eşimin"): a word that starts with a relationship word.
 */
function typesMentionedInText(text: string): Set<EdgeType> {
  const words = wordsOf(text);
  const found = new Set<EdgeType>();
  for (const t of Object.keys(RELATIONSHIP_WORDS) as EdgeType[]) {
    for (const phrase of RELATIONSHIP_WORDS[t]) {
      const parts = phrase.split(" ");
      const last = parts.length - 1;
      const named = words.some((_, i) =>
        parts.every((part, j) =>
          j === last ? (words[i + j] ?? "").startsWith(part) : words[i + j] === part,
        ),
      );
      if (named) found.add(t);
    }
  }
  return found;
}

/**
 * The model picked one of two or more connections that share the relationship the text names
 * ("annem" with two parents) without the text naming that person: a guess, since the type has no
 * gender (NUD-132). The recipient becomes unknown, so the preview asks Kime.
 */
export function withoutRelationshipGuess(
  e: Extraction,
  text: string,
  connections: readonly { name: string; relationship: EdgeType }[],
): Extraction {
  if (e.recipient === null || normalize(e.recipient) === "self") return e;
  const picked = connections.filter((c) => normalize(c.name) === normalize(e.recipient ?? ""));
  if (picked.length !== 1 || !picked[0]) return e;
  const type = picked[0].relationship;
  const shared = connections.filter((c) => c.relationship === type).length > 1;
  const words = new Set(wordsOf(text));
  if (!shared || words.has(normalize(picked[0].name)) || !typesMentionedInText(text).has(type)) {
    return e;
  }
  return {
    ...e,
    recipient: null,
    missing: e.missing.includes("recipient") ? e.missing : ["recipient", ...e.missing],
  };
}

/**
 * The one connection whose name is a whole word of the text ("can yarın okula gitsin"), or null.
 * A word with a suffix ("eren'i ara", "erene yaz") is not the name, so an object is never taken
 * for the recipient. Used only when the model found no recipient (NUD-52: without reasoning it
 * sometimes misses a lowercase name).
 */
function namedInText(text: string, connections: readonly Connection[]): PersonId | null {
  const words = new Set(wordsOf(text));
  const named = connections.filter((c) => words.has(normalize(c.name)));
  if (named.length > 0) return named.length === 1 && named[0] ? named[0].personId : null;
  // No name: a relationship word ("annem") is the one connection with that relationship (NUD-132).
  const types = typesNamedInText(text);
  return types.size === 1 ? onlyOfType(types, connections) : null;
}

/**
 * Not a task: answer with the model's one sentence and create nothing. Confident result: create the
 * task directly (undo toast). Anything missing, ambiguous or in the past: ask one question, in the
 * order recipient, deadline, title.
 */
export function resolveExtraction(
  e: Extraction,
  sender: { id: PersonId },
  /** The sender's connections; `relationship` lets "annem" stand for the one parent (NUD-132). */
  connections: readonly Connection[],
  now: Date,
  /** The written text, to find a recipient the model missed. */
  text?: string,
): Resolution {
  const reply = e.reply?.trim() ?? "";
  if (reply !== "") return { kind: "chat", reply };
  let recipientId: PersonId | null = null;
  if (e.recipient !== null && !e.missing.includes("recipient")) {
    if (normalize(e.recipient) === "self") {
      recipientId = sender.id;
    } else {
      const matches = connections.filter((c) => normalize(c.name) === normalize(e.recipient ?? ""));
      if (matches.length === 1 && matches[0]) recipientId = matches[0].personId;
      // The model wrote the relationship word ("Annem") instead of the name (NUD-132).
      else if (matches.length === 0)
        recipientId = onlyOfType(typesNamedBy(e.recipient), connections);
    }
  }
  if (recipientId === null && e.recipient === null && text !== undefined) {
    recipientId = namedInText(text, connections);
  }
  if (recipientId === null) return { kind: "ask", question: "recipient" };

  // A repeating task's deadline was set from its rule by `withFirstOccurrence`, so it is ahead.
  const deadline = e.deadline === null ? null : new Date(e.deadline);
  if (
    deadline === null ||
    Number.isNaN(deadline.getTime()) ||
    deadline.getTime() <= now.getTime() ||
    e.missing.includes("deadline")
  ) {
    return { kind: "ask", question: "deadline" };
  }

  const title = e.title?.trim() ?? "";
  if (title === "" || e.missing.includes("title")) return { kind: "ask", question: "title" };

  return {
    kind: "create",
    recipientId,
    title,
    detail: e.detail?.trim() === "" ? null : e.detail,
    deadline,
    timing: e.timing,
    missAfterMinutes: e.timing === "at" ? e.missAfterMinutes : null,
    leadMinutes: e.timing === "at" ? (e.leadMinutes ?? null) : null,
    finishMode: e.finishMode,
    repeat: text !== undefined && textSaysRepeat(text) ? repeatRuleOf(e.repeat) : null,
  };
}

// LLM task extraction: the provider-neutral input/output and the post-processing that decides
// between creating the task at once and asking exactly one question (docs/domain.md).
import { nextOccurrenceAt, repeatRuleOf, textSaysRepeat, type RepeatRule } from "./repeat";
import type { EdgeType, FinishMode, PersonId, Timing } from "./types";

export interface ExtractionInput {
  text: string;
  /** The sender's local time comes from `now` and `timeZone`. */
  now: Date;
  timeZone: string;
  senderName: string;
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
 * The one connection whose name is a whole word of the text ("can yarın okula gitsin"), or null.
 * A word with a suffix ("eren'i ara", "erene yaz") is not the name, so an object is never taken
 * for the recipient. Used only when the model found no recipient (NUD-52: without reasoning it
 * sometimes misses a lowercase name).
 */
function namedInText(
  text: string,
  connections: readonly { personId: PersonId; name: string }[],
): PersonId | null {
  const words = new Set(
    text
      .split(/\s+/)
      .map((w) => normalize(w.replace(/^[.,!?;:"]+|[.,!?;:"]+$/g, "")))
      .filter((w) => w !== ""),
  );
  const named = connections.filter((c) => words.has(normalize(c.name)));
  return named.length === 1 && named[0] ? named[0].personId : null;
}

/**
 * Not a task: answer with the model's one sentence and create nothing. Confident result: create the
 * task directly (undo toast). Anything missing, ambiguous or in the past: ask one question, in the
 * order recipient, deadline, title.
 */
export function resolveExtraction(
  e: Extraction,
  sender: { id: PersonId },
  connections: readonly { personId: PersonId; name: string }[],
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

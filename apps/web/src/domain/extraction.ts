// LLM task extraction: the provider-neutral input/output and the post-processing that decides
// between creating the task at once and asking exactly one question (docs/domain.md).
import type { EdgeType, FinishMode, PersonId } from "./types";

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
  /** ISO 8601 with offset, or null when the text gives no time. */
  deadline: string | null;
  finishMode: FinishMode;
  missing: MissingField[];
}

export type Resolution =
  | {
      kind: "create";
      recipientId: PersonId;
      title: string;
      detail: string | null;
      deadline: Date;
      finishMode: FinishMode;
    }
  | { kind: "ask"; question: MissingField };

const normalize = (s: string): string => s.trim().toLocaleLowerCase("tr");

/**
 * Confident result: create the task directly (undo toast). Anything missing, ambiguous or in the
 * past: ask one question, in the order recipient, deadline, title.
 */
export function resolveExtraction(
  e: Extraction,
  sender: { id: PersonId },
  connections: readonly { personId: PersonId; name: string }[],
  now: Date,
): Resolution {
  let recipientId: PersonId | null = null;
  if (e.recipient !== null && !e.missing.includes("recipient")) {
    if (normalize(e.recipient) === "self") {
      recipientId = sender.id;
    } else {
      const matches = connections.filter((c) => normalize(c.name) === normalize(e.recipient ?? ""));
      if (matches.length === 1 && matches[0]) recipientId = matches[0].personId;
    }
  }
  if (recipientId === null) return { kind: "ask", question: "recipient" };

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
    finishMode: e.finishMode,
  };
}

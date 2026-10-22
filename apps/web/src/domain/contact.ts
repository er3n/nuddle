// The contact of a task: whom the recipient calls or messages from the poke (docs/domain.md, Call
// or message from the poke; NUD-128).
import type { PersonId, TaskContact } from "./types";

/** What the extractor gives: a connection's name as in the list, or "sender" for the writer. */
export interface ExtractedContact {
  name: string;
  action: "call" | "message";
}

const normalize = (s: string): string => s.trim().toLocaleLowerCase("tr");

/**
 * The person the extractor named, among the sender and the sender's connections: "sender", or the
 * one connection with that name. Null when nothing was named or the name is no connection (a
 * teacher, a plumber) or more than one. Who may keep it is `keepContact`'s question.
 */
export function contactOf(
  extracted: ExtractedContact | null | undefined,
  sender: { id: PersonId },
  connections: readonly { personId: PersonId; name: string }[],
): TaskContact | null {
  if (!extracted) return null;
  const { action } = extracted;
  const name = normalize(extracted.name);
  if (name === "") return null;
  if (name === "sender") return { personId: sender.id, action };
  const matches = connections.filter((c) => normalize(c.name) === name);
  return matches.length === 1 && matches[0] ? { personId: matches[0].personId, action } : null;
}

/**
 * The contact a task to `recipientId` may carry: never the recipient, always the sender (the task
 * joins the two), otherwise an active connection of the sender who, for a task to someone else, is
 * also an active connection of the recipient. `linked(a, b)` says whether a has an active edge to b.
 * Anything else gives null: the task simply has no Ara.
 */
export function keepContact(
  contact: TaskContact | null | undefined,
  task: { senderId: PersonId; recipientId: PersonId },
  linked: (from: PersonId, to: PersonId) => boolean,
): TaskContact | null {
  if (!contact) return null;
  const { personId } = contact;
  if (personId === task.recipientId) return null;
  if (personId === task.senderId) return contact;
  if (!linked(task.senderId, personId)) return null;
  if (task.recipientId !== task.senderId && !linked(task.recipientId, personId)) return null;
  return contact;
}

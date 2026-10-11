// Invites, connections and account deletion (docs/domain.md, Entities; docs/product.md,
// Connections and Identity). Pure functions returning the new rows and push hints.
import { applyCommand, type Outcome, type TaskContext } from "./task-machine";
import type { Edge, EdgeType, Person, PersonId, PushMessage, Task } from "./types";

export type InviteStatus = "open" | "accepted" | "declined" | "revoked" | "expired";

export interface Invite {
  id: string;
  inviterId: PersonId;
  inviteePhone: string;
  /** The inviter's choice: what the invitee is to the inviter. Nothing is preselected. */
  relationship: EdgeType;
  prefilledName: string;
  code: string;
  status: InviteStatus;
}

export type ConnectionError = "invite_not_open" | "wrong_invitee" | "self_invite";

export type ConnectionResult<T> = { ok: true; value: T } | { ok: false; error: ConnectionError };

// TODO(open-questions#2): invites stay open until accepted, declined or revoked; no expiry.
export function createInvite(
  input: Omit<Invite, "status">,
  inviter: Person,
): ConnectionResult<Invite> {
  if (input.inviteePhone === inviter.phone) return { ok: false, error: "self_invite" };
  return { ok: true, value: { ...input, status: "open" } };
}

export interface Accepted {
  invite: Invite;
  /** Two directed edges created together; each side picked its own relationship. */
  edges: [Edge, Edge];
  pushes: PushMessage[];
}

/**
 * The invitee says "İzin ver". `relationship` is the invitee's own pick for the inviter
 * ("Selin senin neyin?").
 */
export function acceptInvite(
  invite: Invite,
  invitee: Person,
  relationship: EdgeType,
): ConnectionResult<Accepted> {
  if (invite.status !== "open") return { ok: false, error: "invite_not_open" };
  if (invitee.phone !== invite.inviteePhone) return { ok: false, error: "wrong_invitee" };
  return {
    ok: true,
    value: {
      invite: { ...invite, status: "accepted" },
      edges: [
        {
          fromPersonId: invite.inviterId,
          toPersonId: invitee.id,
          type: invite.relationship,
          status: "active",
        },
        {
          fromPersonId: invitee.id,
          toPersonId: invite.inviterId,
          type: relationship,
          status: "active",
        },
      ],
      pushes: [{ to: invite.inviterId, hint: "sync", taskId: null, event: "invite_accepted" }],
    },
  };
}

/** "Şimdi değil". Stored, but never reported to the inviter: no push, no visible change. */
export function declineInvite(invite: Invite, invitee: Person): ConnectionResult<Invite> {
  if (invite.status !== "open") return { ok: false, error: "invite_not_open" };
  if (invitee.phone !== invite.inviteePhone) return { ok: false, error: "wrong_invitee" };
  return { ok: true, value: { ...invite, status: "declined" } };
}

/** The invite status as the inviter may see it. A decline looks like an invite still open. */
export function inviteStatusForInviter(invite: Invite): Exclude<InviteStatus, "declined"> {
  switch (invite.status) {
    case "declined":
    case "open":
      return "open";
    case "accepted":
    case "revoked":
    case "expired":
      return invite.status;
  }
}

export interface Deleted {
  person: Person;
  edges: Edge[];
  /** One outcome per task that was still open; the other side is told. */
  cancelled: Outcome[];
}

/**
 * Delete an account: connections are removed and every task the person sends or receives that
 * is not closed yet is cancelled. Hard deletion of rows and photos is the persistence layer's job.
 */
export function deleteAccount(
  person: Person,
  edges: readonly Edge[],
  tasks: readonly { task: Task; ctx: TaskContext }[],
  now: Date,
): Deleted {
  const cancelled: Outcome[] = [];
  for (const { task, ctx } of tasks) {
    const r = applyCommand(task, { type: "account_deleted", personId: person.id }, null, ctx);
    if (r.ok) cancelled.push(r.value);
  }
  return {
    person: { ...person, deletedAt: now },
    edges: edges
      .filter((e) => e.fromPersonId === person.id || e.toPersonId === person.id)
      .map((e) => ({ ...e, status: "removed" })),
    cancelled,
  };
}

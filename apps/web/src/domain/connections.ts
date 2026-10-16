// Invites, connections and account deletion (docs/domain.md, Entities; docs/product.md,
// Connections and Identity). Pure functions returning the new rows and push hints.
import { applyCommand, type Outcome, type TaskContext } from "./task-machine";
import { addMs, DAY } from "./time";
import type { Edge, EdgeType, Person, PersonId, PushMessage, ScheduledAction, Task } from "./types";

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

export type ConnectionError =
  "invite_not_open" | "wrong_invitee" | "self_invite" | "not_inviter" | "not_connected";

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
 * ("Sibel senin neyin?").
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
          notifyMe: true,
        },
        {
          fromPersonId: invitee.id,
          toPersonId: invite.inviterId,
          type: relationship,
          status: "active",
          notifyMe: true,
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

/**
 * "İptal et" on the pending invite (1g). Only the inviter, only while it is open to them: a decline still looks
 * open to the inviter, so it can be revoked too, and nothing tells them it was declined.
 */
export function revokeInvite(invite: Invite, inviter: Person): ConnectionResult<Invite> {
  if (invite.inviterId !== inviter.id) return { ok: false, error: "not_inviter" };
  if (invite.status !== "open" && invite.status !== "declined") {
    return { ok: false, error: "invite_not_open" };
  }
  return { ok: true, value: { ...invite, status: "revoked" } };
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
  /** Phone and name erased: the number is free for a new sign-up at once. */
  person: Person;
  edges: Edge[];
  /** One outcome per task that was still open; the other side is told. */
  cancelled: Outcome[];
  /** Deletes the tasks, connections and the person row once the other side has seen the cancels. */
  purge: ScheduledAction;
}

/**
 * How long the tasks of a deleted account stay, so the other side's phones sync the cancels
 * before the rows go: sync has no deletions (ADR 0006, NUD-89).
 */
const ACCOUNT_PURGE_DELAY_MS = 7 * DAY;

/** What stands in for the phone of a deleted account until the purge; never a valid number. */
const deletedPhone = (id: PersonId): string => `deleted:${id}`;

/**
 * Delete an account (docs/product.md, Identity): the phone and name are erased, connections are
 * removed and every task the person sends or receives that is not closed yet is cancelled. The
 * rest goes a week later with `account_purge`. Deleting files and the other personal rows is the
 * persistence layer's job.
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
    person: {
      ...person,
      phone: deletedPhone(person.id),
      displayName: "",
      avatarPath: null,
      deletedAt: now,
    },
    edges: edges
      .filter((e) => e.fromPersonId === person.id || e.toPersonId === person.id)
      .map((e) => ({ ...e, status: "removed" })),
    cancelled,
    purge: {
      kind: "account_purge",
      taskId: null,
      dueAt: addMs(now, ACCOUNT_PURGE_DELAY_MS),
      payload: { personId: person.id },
    },
  };
}

/**
 * "İlişki" and "Sibel'in işleri için bana haber verilebilir" on person detail (5b). Only my own
 * edge changes: each side picks its own relationship and its own switch.
 */
export function changeConnection(
  mine: Edge | null,
  patch: { relationship?: EdgeType; notifyMe?: boolean },
): ConnectionResult<Edge> {
  if (mine?.status !== "active") return { ok: false, error: "not_connected" };
  return {
    ok: true,
    value: {
      ...mine,
      ...(patch.relationship === undefined ? {} : { type: patch.relationship }),
      ...(patch.notifyMe === undefined ? {} : { notifyMe: patch.notifyMe }),
    },
  };
}

export interface Removed {
  edges: Edge[];
  /** One outcome per task between the two that was not closed; nobody is notified. */
  cancelled: Outcome[];
}

/**
 * "Bağlantıyı kaldır" (5b): both directed edges are removed and every task between the two that
 * is not closed is cancelled with reason `connection_removed`. The other side gets no notification
 * ("Sibel'e bildirim gitmez"); their lists change on the next sync.
 */
export function removeConnection(
  me: Person,
  edges: readonly Edge[],
  tasks: readonly { task: Task; ctx: TaskContext }[],
): ConnectionResult<Removed> {
  const mine = edges.find((e) => e.fromPersonId === me.id);
  if (mine?.status !== "active") return { ok: false, error: "not_connected" };
  const cancelled: Outcome[] = [];
  for (const { task, ctx } of tasks) {
    const r = applyCommand(task, { type: "connection_removed", personId: me.id }, null, ctx);
    if (r.ok) cancelled.push(r.value);
  }
  return {
    ok: true,
    value: { edges: edges.map((e) => ({ ...e, status: "removed" })), cancelled },
  };
}

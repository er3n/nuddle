import { describe, expect, it } from "vitest";
import {
  acceptInvite,
  changeConnection,
  createInvite,
  declineInvite,
  deleteAccount,
  inviteStatusForInviter,
  removeConnection,
  revokeInvite,
  type Invite,
} from "../../src/domain/connections";
import type { Edge } from "../../src/domain/types";
import { aPerson, aTask } from "../builders";

const sibel = aPerson("Sibel");
const eren = aPerson("Eren");
const now = new Date("2026-10-11T20:00:00+03:00");

const invite: Invite = {
  id: "i1",
  inviterId: "sibel",
  inviteePhone: eren.phone,
  relationship: "spouse",
  prefilledName: "Eren",
  code: "abc",
  status: "open",
};

describe("invites", () => {
  it("cannot invite yourself", () => {
    expect(createInvite({ ...invite, inviteePhone: sibel.phone }, sibel)).toEqual({
      ok: false,
      error: "self_invite",
    });
  });

  it("accepting creates two directed edges with each side's own relationship", () => {
    const r = acceptInvite(invite, eren, "spouse");
    if (!r.ok) throw new Error(r.error);
    expect(r.value.invite.status).toBe("accepted");
    expect(r.value.edges).toEqual([
      {
        fromPersonId: "sibel",
        toPersonId: "eren",
        type: "spouse",
        status: "active",
        notifyMe: true,
      },
      {
        fromPersonId: "eren",
        toPersonId: "sibel",
        type: "spouse",
        status: "active",
        notifyMe: true,
      },
    ]);
    expect(r.value.pushes).toEqual([
      { to: "sibel", hint: "sync", taskId: null, event: "invite_accepted" },
    ]);
  });

  it("is bound to the invitee's phone number", () => {
    expect(acceptInvite(invite, aPerson("Can"), "other")).toEqual({
      ok: false,
      error: "wrong_invitee",
    });
  });

  it("a decline is stored but looks open to the inviter", () => {
    const r = declineInvite(invite, eren);
    if (!r.ok) throw new Error(r.error);
    expect(r.value.status).toBe("declined");
    expect(inviteStatusForInviter(r.value)).toBe("open");
  });

  it("only the inviter revokes, while the invite is open to them", () => {
    expect(revokeInvite(invite, eren)).toEqual({ ok: false, error: "not_inviter" });
    expect(revokeInvite(invite, sibel)).toEqual({
      ok: true,
      value: { ...invite, status: "revoked" },
    });
    // A decline looks open to the inviter, so it can still be taken back.
    expect(revokeInvite({ ...invite, status: "declined" }, sibel).ok).toBe(true);
    expect(revokeInvite({ ...invite, status: "accepted" }, sibel)).toEqual({
      ok: false,
      error: "invite_not_open",
    });
  });

  it("cannot accept twice", () => {
    expect(acceptInvite({ ...invite, status: "accepted" }, eren, "spouse")).toEqual({
      ok: false,
      error: "invite_not_open",
    });
  });
});

describe("deleteAccount", () => {
  it("removes connections and cancels open tasks on both sides", () => {
    const edges: Edge[] = [
      {
        fromPersonId: "sibel",
        toPersonId: "eren",
        type: "spouse",
        status: "active",
        notifyMe: true,
      },
      {
        fromPersonId: "eren",
        toPersonId: "sibel",
        type: "spouse",
        status: "active",
        notifyMe: true,
      },
    ];
    const ctx = { sender: sibel, recipient: eren, reachability: "NONE" as const, history: [], now };
    const r = deleteAccount(
      eren,
      edges,
      [
        { task: aTask().build(), ctx },
        { task: { ...aTask().withStatus("DONE").build(), id: "task-2" }, ctx },
      ],
      now,
    );
    expect(r.person.deletedAt).toEqual(now);
    expect(r.edges.every((e) => e.status === "removed")).toBe(true);
    expect(r.cancelled.map((o) => o.task.id)).toEqual(["task-1"]);
  });

  it("erases the phone and name at once and purges the rest a week later (NUD-89)", () => {
    const r = deleteAccount({ ...eren, avatarPath: "avatars/eren/1.jpg" }, [], [], now);
    expect(r.person).toMatchObject({ phone: "deleted:eren", displayName: "", avatarPath: null });
    expect(r.purge).toEqual({
      kind: "account_purge",
      taskId: null,
      dueAt: new Date("2026-10-18T20:00:00+03:00"),
      payload: { personId: "eren" },
    });
  });
});

describe("person detail (5b)", () => {
  const mine: Edge = {
    fromPersonId: "eren",
    toPersonId: "sibel",
    type: "spouse",
    status: "active",
    notifyMe: true,
  };
  const theirs: Edge = { ...mine, fromPersonId: "sibel", toPersonId: "eren", type: "other" };
  const ctx = { sender: sibel, recipient: eren, reachability: "NONE" as const, history: [], now };

  it("changes only my own edge: relationship and the switch for their tasks", () => {
    expect(changeConnection(mine, { relationship: "roommate" })).toEqual({
      ok: true,
      value: { ...mine, type: "roommate" },
    });
    expect(changeConnection(mine, { notifyMe: false })).toEqual({
      ok: true,
      value: { ...mine, notifyMe: false },
    });
  });

  it("changes nothing on a connection that is not active", () => {
    expect(changeConnection(null, { notifyMe: false })).toEqual({
      ok: false,
      error: "not_connected",
    });
    expect(changeConnection({ ...mine, status: "removed" }, { notifyMe: false })).toEqual({
      ok: false,
      error: "not_connected",
    });
  });

  it("removing drops both edges and cancels what is not closed, in both directions", () => {
    const toMe = { ...aTask().build(), id: "to-me", senderId: "sibel", recipientId: "eren" };
    const fromMe = {
      ...aTask().withStatus("MISSED").build(),
      id: "from-me",
      senderId: "eren",
      recipientId: "sibel",
    };
    const done = { ...aTask().withStatus("DONE").build(), id: "done" };
    const r = removeConnection(
      eren,
      [mine, theirs],
      [
        { task: toMe, ctx },
        { task: fromMe, ctx: { ...ctx, sender: eren, recipient: sibel } },
        { task: done, ctx },
      ],
    );
    if (!r.ok) throw new Error(r.error);
    expect(r.value.edges.map((e) => e.status)).toEqual(["removed", "removed"]);
    expect(r.value.cancelled.map((o) => o.task.id)).toEqual(["to-me", "from-me"]);
    expect(r.value.cancelled.flatMap((o) => o.pushes.map((p) => p.hint))).toEqual([
      "sync",
      "sync",
      "sync",
      "sync",
    ]);
  });

  it("cannot remove a connection that is already gone", () => {
    expect(removeConnection(eren, [{ ...mine, status: "removed" }, theirs], [])).toEqual({
      ok: false,
      error: "not_connected",
    });
  });
});

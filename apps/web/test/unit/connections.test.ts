import { describe, expect, it } from "vitest";
import {
  acceptInvite,
  createInvite,
  declineInvite,
  deleteAccount,
  inviteStatusForInviter,
  type Invite,
} from "../../src/domain/connections";
import type { Edge } from "../../src/domain/types";
import { aPerson, aTask } from "../builders";

const selin = aPerson("Selin");
const eren = aPerson("Eren");
const now = new Date("2026-10-11T20:00:00+03:00");

const invite: Invite = {
  id: "i1",
  inviterId: "selin",
  inviteePhone: eren.phone,
  relationship: "spouse",
  prefilledName: "Eren",
  code: "abc",
  status: "open",
};

describe("invites", () => {
  it("cannot invite yourself", () => {
    expect(createInvite({ ...invite, inviteePhone: selin.phone }, selin)).toEqual({
      ok: false,
      error: "self_invite",
    });
  });

  it("accepting creates two directed edges with each side's own relationship", () => {
    const r = acceptInvite(invite, eren, "spouse");
    if (!r.ok) throw new Error(r.error);
    expect(r.value.invite.status).toBe("accepted");
    expect(r.value.edges).toEqual([
      { fromPersonId: "selin", toPersonId: "eren", type: "spouse", status: "active" },
      { fromPersonId: "eren", toPersonId: "selin", type: "spouse", status: "active" },
    ]);
    expect(r.value.pushes).toEqual([
      { to: "selin", hint: "sync", taskId: null, event: "invite_accepted" },
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
      { fromPersonId: "selin", toPersonId: "eren", type: "spouse", status: "active" },
      { fromPersonId: "eren", toPersonId: "selin", type: "spouse", status: "active" },
    ];
    const ctx = { sender: selin, recipient: eren, reachability: "NONE" as const, history: [], now };
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
});

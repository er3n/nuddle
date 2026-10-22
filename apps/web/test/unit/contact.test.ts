// The contact of a task (NUD-128, docs/domain.md, Call or message from the poke).
import { describe, expect, it } from "vitest";
import { contactOf, keepContact } from "../../src/domain/contact";

const sibel = { id: "sibel" };
const connections = [
  { personId: "eren", name: "Eren" },
  { personId: "ayse", name: "Ayşe" },
];

describe("contactOf", () => {
  it("finds the connection the extractor named, in any case", () => {
    expect(contactOf({ name: "ayşe", action: "call" }, sibel, connections)).toEqual({
      personId: "ayse",
      action: "call",
    });
    expect(contactOf({ name: "Eren", action: "message" }, sibel, connections)).toEqual({
      personId: "eren",
      action: "message",
    });
  });

  it("'sender' is the writer ('eren beni arasın')", () => {
    expect(contactOf({ name: "sender", action: "call" }, sibel, connections)).toEqual({
      personId: "sibel",
      action: "call",
    });
  });

  it("nothing for no contact, a name that is no connection, or a name two connections share", () => {
    expect(contactOf(null, sibel, connections)).toBeNull();
    expect(contactOf(undefined, sibel, connections)).toBeNull();
    expect(contactOf({ name: "Öğretmen", action: "call" }, sibel, connections)).toBeNull();
    expect(contactOf({ name: " ", action: "call" }, sibel, connections)).toBeNull();
    const twoErens = [...connections, { personId: "eren2", name: "Eren" }];
    expect(contactOf({ name: "Eren", action: "call" }, sibel, twoErens)).toBeNull();
  });
});

describe("keepContact", () => {
  const edges = new Set(["sibel>ayse", "sibel>eren", "eren>sibel", "eren>defne"]);
  const linked = (from: string, to: string) => edges.has(`${from}>${to}`);
  const call = (personId: string) => ({ personId, action: "call" as const });

  it("a task to self keeps the sender's own connection ('annemi ara')", () => {
    expect(keepContact(call("ayse"), { senderId: "sibel", recipientId: "sibel" }, linked)).toEqual(
      call("ayse"),
    );
  });

  it("a task to someone else keeps the sender ('eren beni arasın')", () => {
    expect(keepContact(call("sibel"), { senderId: "sibel", recipientId: "eren" }, linked)).toEqual(
      call("sibel"),
    );
  });

  it("a task to someone else keeps a connection only both of them have", () => {
    const toEren = { senderId: "sibel", recipientId: "eren" };
    // Eren does not know Ayşe through Nuddle: his phone has no number for her.
    expect(keepContact(call("ayse"), toEren, linked)).toBeNull();
    // Defne is Eren's connection but not Sibel's: the extractor could not have named her.
    expect(keepContact(call("defne"), toEren, linked)).toBeNull();
    edges.add("eren>ayse");
    expect(keepContact(call("ayse"), toEren, linked)).toEqual(call("ayse"));
    edges.delete("eren>ayse");
  });

  it("never the recipient, never someone no edge leads to", () => {
    expect(
      keepContact(call("eren"), { senderId: "sibel", recipientId: "eren" }, linked),
    ).toBeNull();
    expect(
      keepContact(call("sibel"), { senderId: "sibel", recipientId: "sibel" }, linked),
    ).toBeNull();
    expect(
      keepContact(call("stranger"), { senderId: "sibel", recipientId: "sibel" }, linked),
    ).toBeNull();
    expect(keepContact(null, { senderId: "sibel", recipientId: "sibel" }, linked)).toBeNull();
  });
});

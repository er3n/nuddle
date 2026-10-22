import { describe, expect, it } from "vitest";
import {
  resolveExtraction,
  TURKISH_FORMATS,
  withFirstOccurrence,
  withoutRelationshipGuess,
  type Extraction,
} from "../../src/domain/extraction";
import { FixtureExtractor } from "../../src/infra/llm/fixture-extractor";
import type { LlmExchange } from "../../src/domain/ports";
import type { EdgeType } from "../../src/domain/types";
import { buildPrompt, OpenRouterExtractor } from "../../src/infra/llm/openrouter-extractor";

const now = new Date("2026-10-11T20:00:00+03:00");
const connections = [
  { personId: "eren", name: "Eren" },
  { personId: "defne", name: "Defne" },
];

const complete: Extraction = {
  recipient: "Eren",
  title: "Temizlikçi için para çek",
  detail: "1.000 TL",
  deadline: "2026-10-12T09:00:00+03:00",
  timing: "by",
  missAfterMinutes: null,
  finishMode: "photo",
  missing: [],
};

const input = {
  text: "eren yarın 9dan önce temizlikçi için 1000 çeksin",
  now,
  timeZone: "Europe/Istanbul",
  formats: TURKISH_FORMATS,
  senderName: "Sibel",
  connections: [{ name: "Eren", relationship: "spouse" as const }],
};

describe("resolveExtraction", () => {
  it("creates directly when confident", () => {
    expect(resolveExtraction(complete, { id: "sibel" }, connections, now)).toEqual({
      kind: "create",
      recipientId: "eren",
      title: "Temizlikçi için para çek",
      detail: "1.000 TL",
      deadline: new Date("2026-10-12T06:00:00Z"),
      timing: "by",
      missAfterMinutes: null,
      leadMinutes: null,
      finishMode: "photo",
      repeat: null,
    });
  });

  it("keeps the timing, and the grace only for an at task (ADR 0017)", () => {
    const water = {
      ...complete,
      recipient: "self",
      title: "Su iç",
      detail: null,
      deadline: "2026-10-11T20:02:00+03:00",
      timing: "at" as const,
      missAfterMinutes: 30,
      leadMinutes: null,
      finishMode: "done" as const,
    };
    expect(resolveExtraction(water, { id: "sibel" }, connections, now)).toMatchObject({
      kind: "create",
      recipientId: "sibel",
      deadline: new Date("2026-10-11T17:02:00Z"),
      timing: "at",
      missAfterMinutes: 30,
    });
    expect(
      resolveExtraction({ ...complete, missAfterMinutes: 30 }, { id: "sibel" }, connections, now),
    ).toMatchObject({ timing: "by", missAfterMinutes: null });
  });

  it("matches names with Turkish case rules", () => {
    const r = resolveExtraction(
      { ...complete, recipient: "İLKER" },
      { id: "sibel" },
      [{ personId: "ilker", name: "ilker" }],
      now,
    );
    expect(r).toMatchObject({ kind: "create", recipientId: "ilker" });
  });

  it("resolves self", () => {
    expect(
      resolveExtraction({ ...complete, recipient: "self" }, { id: "sibel" }, connections, now),
    ).toMatchObject({ kind: "create", recipientId: "sibel" });
  });

  it.each<[string, Partial<Extraction>, string]>([
    ["unknown recipient", { recipient: "Mehmet" }, "recipient"],
    ["no recipient", { recipient: null, missing: ["recipient"] }, "recipient"],
    ["no deadline", { deadline: null, missing: ["deadline"] }, "deadline"],
    ["deadline in the past", { deadline: "2026-10-11T19:00:00+03:00" }, "deadline"],
    ["unparseable deadline", { deadline: "yarın" }, "deadline"],
    ["no title", { title: " " }, "title"],
  ])("asks one question: %s", (_name, change, question) => {
    expect(
      resolveExtraction({ ...complete, ...change }, { id: "sibel" }, connections, now),
    ).toEqual({ kind: "ask", question });
  });

  it("asks for the recipient when two connections share the name", () => {
    const twins = [...connections, { personId: "eren-2", name: "eren" }];
    expect(resolveExtraction(complete, { id: "sibel" }, twins, now)).toEqual({
      kind: "ask",
      question: "recipient",
    });
  });
});

describe("resolveExtraction, a name the model missed (NUD-52)", () => {
  const noRecipient: Extraction = { ...complete, recipient: null, missing: ["recipient"] };
  const resolve = (text: string, e: Extraction = noRecipient) =>
    resolveExtraction(e, { id: "sibel" }, connections, now, text);

  it("takes the one connection named as a whole word", () => {
    expect(resolve("eren yarın 9dan önce 1000 çeksin")).toMatchObject({
      kind: "create",
      recipientId: "eren",
    });
    expect(resolve("Yarın, Eren! 9dan önce çeksin")).toMatchObject({ recipientId: "eren" });
  });

  it("does not take a name with a suffix, two names, or override the model's choice", () => {
    expect(resolve("eren'i yarın ara")).toEqual({ kind: "ask", question: "recipient" });
    expect(resolve("erene yarın yaz")).toEqual({ kind: "ask", question: "recipient" });
    expect(resolve("eren ve defne yarın gitsin")).toEqual({ kind: "ask", question: "recipient" });
    expect(resolve("eren yarın gitsin", { ...complete, recipient: "Mehmet" })).toEqual({
      kind: "ask",
      question: "recipient",
    });
  });
});

describe("resolveExtraction, a relationship word (NUD-132)", () => {
  type Person = { personId: string; name: string; relationship: EdgeType };
  const family: Person[] = [
    { personId: "eren", name: "Eren", relationship: "spouse" as const },
    { personId: "ayse", name: "Ayşe", relationship: "parent" as const },
    { personId: "defne", name: "Defne", relationship: "child" as const },
    { personId: "can", name: "Can", relationship: "child" as const },
    { personId: "ali", name: "Ali", relationship: "roommate" as const },
  ];
  const noRecipient: Extraction = { ...complete, recipient: null, missing: ["recipient"] };
  const resolve = (text: string, e: Extraction = noRecipient, people: readonly Person[] = family) =>
    resolveExtraction(e, { id: "sibel" }, people, now, text);

  it("takes the one connection with the relationship the model wrote instead of a name", () => {
    expect(resolve("annem yarın ilacını alsın", { ...complete, recipient: "Annem" })).toMatchObject(
      { kind: "create", recipientId: "ayse" },
    );
    expect(resolve("eşim yarın gitsin", { ...complete, recipient: "spouse" })).toMatchObject({
      recipientId: "eren",
    });
  });

  it("takes the one connection named by a relationship word the model missed", () => {
    expect(resolve("annem yarın 9dan önce ilacını alsın")).toMatchObject({ recipientId: "ayse" });
    expect(resolve("Eşim, yarın ekmek alsın")).toMatchObject({ recipientId: "eren" });
    expect(resolve("ev arkadaşım yarın çöpü atsın")).toMatchObject({ recipientId: "ali" });
  });

  it("asks when two connections share the relationship or none has it", () => {
    expect(resolve("oğlum yarın antrenmana gitsin")).toEqual({
      kind: "ask",
      question: "recipient",
    });
    expect(resolve("kızım yarın gitsin", { ...complete, recipient: "kızım" })).toEqual({
      kind: "ask",
      question: "recipient",
    });
    const twoParents = [
      ...family,
      { personId: "mehmet", name: "Mehmet", relationship: "parent" as const },
    ];
    expect(resolve("annem yarın ilacını alsın", noRecipient, twoParents)).toEqual({
      kind: "ask",
      question: "recipient",
    });
    expect(resolve("kardeşim yarın gitsin")).toEqual({ kind: "ask", question: "recipient" });
  });

  it("does not take a relationship word with a suffix, or one next to a name", () => {
    expect(resolve("annemi yarın ara")).toEqual({ kind: "ask", question: "recipient" });
    expect(resolve("anneme yarın 500 gönder")).toEqual({ kind: "ask", question: "recipient" });
    expect(resolve("annem ve eşim yarın gitsin")).toEqual({ kind: "ask", question: "recipient" });
    expect(resolve("eren annem gelince kapıyı açsın")).toMatchObject({ recipientId: "eren" });
  });

  it("prefers a connection whose name is the word itself", () => {
    const named = [...family, { personId: "annem", name: "Annem", relationship: "other" as const }];
    expect(resolve("annem yarın gitsin", { ...complete, recipient: "annem" }, named)).toMatchObject(
      { recipientId: "annem" },
    );
  });
});

describe("withoutRelationshipGuess (NUD-132)", () => {
  const people = [
    { name: "Eren", relationship: "spouse" as const },
    { name: "Ayşe", relationship: "parent" as const },
    { name: "Mehmet", relationship: "parent" as const },
  ];
  const toAyse: Extraction = { ...complete, recipient: "Ayşe" };

  it("drops a pick among two connections of the relationship the text names", () => {
    for (const text of ["annem yarın ilacını alsın", "anneme söyle yarın ilacını alsın"]) {
      expect(withoutRelationshipGuess(toAyse, text, people)).toMatchObject({
        recipient: null,
        missing: ["recipient"],
      });
    }
  });

  it("keeps the pick when the relationship is unique, the name is in the text, or no word is", () => {
    const oneParent = people.filter((p) => p.name !== "Mehmet");
    expect(withoutRelationshipGuess(toAyse, "annem yarın ilacını alsın", oneParent)).toBe(toAyse);
    expect(withoutRelationshipGuess(toAyse, "annem ayşe yarın ilacını alsın", people)).toBe(toAyse);
    expect(withoutRelationshipGuess(toAyse, "ayşe yarın ilacını alsın", people)).toBe(toAyse);
    const toEren: Extraction = { ...complete, recipient: "Eren" };
    expect(withoutRelationshipGuess(toEren, "eren annemi yarın arasın", people)).toBe(toEren);
    const self: Extraction = { ...complete, recipient: "self" };
    expect(withoutRelationshipGuess(self, "annemi yarın ara", people)).toBe(self);
  });
});

describe("resolveExtraction, not a task (NUD-52)", () => {
  const hello: Extraction = {
    recipient: null,
    title: null,
    detail: null,
    deadline: null,
    timing: "by",
    missAfterMinutes: null,
    finishMode: "done",
    missing: [],
    reply: " Merhaba! Kime ne hatırlatayım? ",
  };

  it("answers with the reply and creates nothing", () => {
    expect(resolveExtraction(hello, { id: "sibel" }, connections, now)).toEqual({
      kind: "chat",
      reply: "Merhaba! Kime ne hatırlatayım?",
    });
  });

  it("treats an empty reply as a task", () => {
    expect(
      resolveExtraction({ ...complete, reply: "  " }, { id: "sibel" }, connections, now),
    ).toMatchObject({ kind: "create", recipientId: "eren" });
    expect(
      resolveExtraction({ ...complete, reply: null }, { id: "sibel" }, connections, now),
    ).toMatchObject({ kind: "create" });
  });
});

describe("FixtureExtractor", () => {
  it("returns the fixture for the exact text and everything missing otherwise", async () => {
    const e = new FixtureExtractor(new Map([[input.text, complete]]));
    expect(await e.extract(input)).toEqual(complete);
    expect((await e.extract({ ...input, text: "bilinmeyen" })).missing).toEqual([
      "recipient",
      "title",
      "deadline",
    ]);
    expect(e.calls).toHaveLength(2);
  });
});

describe("OpenRouterExtractor", () => {
  it("sends the model, the prompt and a strict JSON schema, and parses the answer", async () => {
    const requests: { url: string; init: RequestInit }[] = [];
    const fakeFetch: typeof fetch = (url, init) => {
      requests.push({ url: url as string, init: init ?? {} });
      return Promise.resolve(
        new Response(
          JSON.stringify({ choices: [{ message: { content: JSON.stringify(complete) } }] }),
          { status: 200 },
        ),
      );
    };
    const extractor = new OpenRouterExtractor({ apiKey: "k", model: "m/x", fetch: fakeFetch });
    expect(await extractor.extract(input)).toEqual({ ...complete, repeat: null });

    const body = JSON.parse(requests[0]?.init.body as string) as {
      model: string;
      messages: { content: string }[];
      response_format: { type: string; json_schema: { strict: boolean } };
    };
    expect(requests[0]?.url).toBe("https://openrouter.ai/api/v1/chat/completions");
    expect(body.model).toBe("m/x");
    expect(body.response_format.type).toBe("json_schema");
    expect(body.response_format.json_schema.strict).toBe(true);
    expect(body.messages[1]?.content).toContain("2026-10-11T20:00:00+03:00 (Sunday)");
  });

  it("turns the model's thinking off, since the sender waits for the answer (NUD-52)", async () => {
    const bodies: string[] = [];
    const fakeFetch: typeof fetch = (_url, init) => {
      bodies.push(init?.body as string);
      return Promise.resolve(
        new Response(
          JSON.stringify({ choices: [{ message: { content: JSON.stringify(complete) } }] }),
          { status: 200 },
        ),
      );
    };
    await new OpenRouterExtractor({ apiKey: "k", model: "m", fetch: fakeFetch }).extract(input);
    expect(JSON.parse(bodies[0] ?? "{}")).toMatchObject({ reasoning: { enabled: false } });
  });

  it("accepts JSON wrapped in a code fence", async () => {
    const fenced = "```json\n" + JSON.stringify(complete) + "\n```";
    const fakeFetch: typeof fetch = () =>
      Promise.resolve(
        new Response(JSON.stringify({ choices: [{ message: { content: fenced } }] }), {
          status: 200,
        }),
      );
    const extractor = new OpenRouterExtractor({ apiKey: "k", model: "m", fetch: fakeFetch });
    expect(await extractor.extract(input)).toEqual({ ...complete, repeat: null });
  });

  it("fails loudly on an HTTP error", async () => {
    const fakeFetch: typeof fetch = () => Promise.resolve(new Response("no", { status: 401 }));
    const extractor = new OpenRouterExtractor({ apiKey: "k", model: "m", fetch: fakeFetch });
    await expect(extractor.extract(input)).rejects.toThrow("status 401");
  });

  it("tells the call log what went to the model and what came back, as text when it is not JSON (ADR 0019)", async () => {
    const answering =
      (content: string): typeof fetch =>
      () =>
        Promise.resolve(
          new Response(JSON.stringify({ choices: [{ message: { content } }] }), { status: 200 }),
        );
    const seen: LlmExchange[] = [];
    await new OpenRouterExtractor({
      apiKey: "k",
      model: "m/x",
      fetch: answering(JSON.stringify(complete)),
    }).extract(input, (x) => seen.push(x));
    expect(seen[0]).toMatchObject({ model: "m/x", prompt: buildPrompt(input), output: complete });
    expect(seen[0]?.system).toContain("recipient");

    const broken = new OpenRouterExtractor({
      apiKey: "k",
      model: "m",
      fetch: answering("not json"),
    });
    await expect(broken.extract(input, (x) => seen.push(x))).rejects.toThrow();
    expect(seen[1]?.output).toBe("not json");
  });

  it("lists connections with their relationship", () => {
    expect(buildPrompt(input)).toContain("- Eren: spouse");
  });

  it("tells the model the relationship words (NUD-132)", async () => {
    const seen: LlmExchange[] = [];
    const extractor = new OpenRouterExtractor({
      apiKey: "k",
      model: "m",
      fetch: () =>
        Promise.resolve(
          new Response(
            JSON.stringify({ choices: [{ message: { content: JSON.stringify(complete) } }] }),
            { status: 200 },
          ),
        ),
    });
    await extractor.extract(input, (x) => seen.push(x));
    expect(seen[0]?.system).toContain("annem, babam (parent)");
    expect(seen[0]?.system).toContain("ev arkadaşım (roommate)");
  });

  it("gives the sender's language, week start and clock (NUD-132)", () => {
    expect(buildPrompt(input)).toContain(
      "Sender's formats: language tr-TR, week starts Monday, 24-hour clock",
    );
    expect(
      buildPrompt({ ...input, formats: { locale: "en-US", weekStart: "sunday", hour24: false } }),
    ).toContain("Sender's formats: language en-US, week starts Sunday, 12-hour clock");
  });

  it("sends only what the input holds: no phone number, no id (NUD-132)", () => {
    // Fields a caller might pass by mistake are not in the prompt.
    const leaky = {
      ...input,
      connections: [
        { name: "Eren", relationship: "spouse" as const, phone: "+905321112233", personId: "p-1" },
      ],
    };
    const prompt = buildPrompt(leaky);
    expect(prompt).not.toContain("5321112233");
    expect(prompt).not.toContain("p-1");
    expect(prompt.split("\n").filter((l) => l.startsWith("- "))).toEqual(["- Eren: spouse"]);
  });
});

describe("withFirstOccurrence (NUD-83)", () => {
  const tz = "Europe/Istanbul";
  // Friday 07:23: the model wrote today 07:00, which has passed.
  const friday = new Date("2026-10-16T07:23:00+03:00");
  const wake: Extraction = {
    ...complete,
    recipient: "self",
    title: "Kalk",
    detail: null,
    deadline: "2026-10-16T07:00:00+03:00",
    timing: "at",
    missAfterMinutes: 15,
    finishMode: "done",
    repeat: { every: "weekdays", time: "07:00" },
  };
  const text = "beni hafta içi her gün 7'de kaldır";

  it("sets the deadline to the rule's next time after now and drops the deadline question", () => {
    const got = withFirstOccurrence({ ...wake, missing: ["deadline"] }, text, friday, tz);
    expect(got.deadline).toBe(new Date("2026-10-19T07:00:00+03:00").toISOString());
    expect(got.missing).toEqual([]);
    expect(got.repeat).toEqual({ every: "weekdays", time: "07:00" });
    expect(resolveExtraction(got, { id: "sibel" }, connections, friday, text)).toMatchObject({
      kind: "create",
      deadline: new Date("2026-10-19T07:00:00+03:00"),
      repeat: { every: "weekdays", time: "07:00" },
    });
  });

  it("today when the rule's time is still ahead", () => {
    const early = new Date("2026-10-16T06:40:00+03:00");
    expect(withFirstOccurrence(wake, text, early, tz).deadline).toBe(
      new Date("2026-10-16T07:00:00+03:00").toISOString(),
    );
  });

  it("leaves a one-off alone, and drops a rule the text does not say", () => {
    expect(withFirstOccurrence(complete, "eren yarın 9dan önce para çeksin", friday, tz)).toEqual({
      ...complete,
      repeat: null,
    });
    const invented = withFirstOccurrence(wake, "beni yarın 7de kaldır", friday, tz);
    expect(invented.repeat).toBeNull();
    expect(invented.deadline).toBe("2026-10-16T07:00:00+03:00");
  });
});

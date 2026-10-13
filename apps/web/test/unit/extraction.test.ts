import { describe, expect, it } from "vitest";
import { resolveExtraction, type Extraction } from "../../src/domain/extraction";
import { FixtureExtractor } from "../../src/infra/llm/fixture-extractor";
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
  finishMode: "photo",
  missing: [],
};

const input = {
  text: "eren yarın 9dan önce temizlikçi için 1000 çeksin",
  now,
  timeZone: "Europe/Istanbul",
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
      finishMode: "photo",
    });
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
    expect(await extractor.extract(input)).toEqual(complete);

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

  it("accepts JSON wrapped in a code fence", async () => {
    const fenced = "```json\n" + JSON.stringify(complete) + "\n```";
    const fakeFetch: typeof fetch = () =>
      Promise.resolve(
        new Response(JSON.stringify({ choices: [{ message: { content: fenced } }] }), {
          status: 200,
        }),
      );
    const extractor = new OpenRouterExtractor({ apiKey: "k", model: "m", fetch: fakeFetch });
    expect(await extractor.extract(input)).toEqual(complete);
  });

  it("fails loudly on an HTTP error", async () => {
    const fakeFetch: typeof fetch = () => Promise.resolve(new Response("no", { status: 401 }));
    const extractor = new OpenRouterExtractor({ apiKey: "k", model: "m", fetch: fakeFetch });
    await expect(extractor.extract(input)).rejects.toThrow("status 401");
  });

  it("lists connections with their relationship", () => {
    expect(buildPrompt(input)).toContain("- Eren: spouse");
  });
});

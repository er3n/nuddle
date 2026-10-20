// HTTP layer: `mise run announce` (ADR 0025), behind the tick secret; the content reaches the
// recipient's sync and nobody else's.
import { afterAll, beforeAll, describe, expect, it } from "vitest";
import { startTestServer, TICK_SECRET, type TestServer } from "../support/server";
import { signUp, type SignedUp } from "./helpers";

const announcement = {
  slug: "premium",
  title: "Premium geldi",
  summary: "SMS ile dürtme artık Premium'da.",
  body: "**Yeni**",
  button: { label: "Davet et", target: "invite" },
};

describe("http: announce", () => {
  let server: TestServer;
  let sibel: SignedUp;
  let eren: SignedUp;

  const ask = (body: unknown, secret = TICK_SECRET) =>
    server.app.request("/api/internal/announce", {
      method: "POST",
      headers: { Authorization: `Bearer ${secret}`, "content-type": "application/json" },
      body: JSON.stringify(body),
    });
  const announcementsOf = async (who: SignedUp) =>
    (await who.api.GET("/api/v1/sync", { params: { query: {} } })).data?.announcements ?? [];

  beforeAll(async () => {
    server = await startTestServer();
    sibel = await signUp(server, "Sibel");
    eren = await signUp(server, "Eren");
  });
  afterAll(async () => {
    await server.close();
  });

  it("needs the tick secret, a slug and E.164 numbers or *", async () => {
    expect((await ask({ announcement, to: [eren.phone] }, "nope")).status).toBe(401);
    expect((await ask({ announcement, to: ["0532"] })).status).toBe(400);
    expect((await ask({ announcement, to: [] })).status).toBe(400);
    expect(
      (await ask({ announcement: { ...announcement, slug: "Bad Slug" }, to: "*" })).status,
    ).toBe(400);
  });

  it("without confirm only says who would get it", async () => {
    const res = await ask({ announcement, to: [eren.phone, "+905559999999"] });
    expect(await res.json()).toEqual({
      problems: [],
      unknownPhones: ["+905559999999"],
      newRecipients: 1,
      alreadySent: 0,
      textChanged: false,
      sent: false,
    });
    expect(await announcementsOf(eren)).toEqual([]);
  });

  it("with confirm the recipient's sync carries it", async () => {
    const res = await ask({ announcement, to: [eren.phone], confirm: true });
    expect(await res.json()).toMatchObject({ sent: true, newRecipients: 1 });
    const got = await announcementsOf(eren);
    expect(
      got.map((a) => ({ title: a.title, summary: a.summary, body: a.body, button: a.button })),
    ).toEqual([
      {
        title: announcement.title,
        summary: announcement.summary,
        body: announcement.body,
        button: announcement.button,
      },
    ]);
    expect(got[0]?.id).toMatch(/^[0-9a-f-]{36}$/);
    expect(Number.isNaN(Date.parse(got[0]?.sentAt ?? ""))).toBe(false);
    expect(await announcementsOf(sibel)).toEqual([]);
  });
});

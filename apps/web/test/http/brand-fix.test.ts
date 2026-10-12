// HTTP layer: GET /api/v1/brand-fix (2h; docs/ux/platform.md, Missed-ring detection).
import { afterAll, beforeAll, describe, expect, it } from "vitest";
import { startTestServer, type TestServer } from "../support/server";
import { signUp, type SignedUp } from "./helpers";

describe("http: brand fix", () => {
  let server: TestServer;
  let eren: SignedUp;

  beforeAll(async () => {
    server = await startTestServer();
    eren = await signUp(server, "Eren");
  });
  afterAll(async () => {
    await server.close();
  });

  it("gives the steps for the phone's manufacturer, and the default ones for any other phone", async () => {
    const xiaomi = await eren.api.GET("/api/v1/brand-fix", {
      params: { query: { manufacturer: "Xiaomi" } },
    });
    expect(xiaomi.data).toMatchObject({ brand: "xiaomi", name: "Xiaomi" });
    expect(xiaomi.data?.steps.map((s) => s.title)).toEqual([
      "Otomatik başlatma",
      "Pil tasarrufu: Kısıtlama yok",
    ]);

    const pixel = await eren.api.GET("/api/v1/brand-fix", {
      params: { query: { manufacturer: "Google" } },
    });
    expect(pixel.data).toMatchObject({ brand: "default", name: null, reason: null });
  });

  it("needs a manufacturer and a signed-in caller", async () => {
    const missing = await eren.api.GET("/api/v1/brand-fix", {
      params: { query: {} as { manufacturer: string } },
    });
    expect(missing.response.status).toBe(400);

    const anonymous = await server
      .client()
      .GET("/api/v1/brand-fix", { params: { query: { manufacturer: "Xiaomi" } } });
    expect(anonymous.response.status).toBe(401);
  });
});

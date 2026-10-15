import { describe, expect, it } from "vitest";
import { PROFILE_EVERY_TASKS, profileIsStale } from "../../src/domain/profile";
import { DAY } from "../../src/domain/time";
import { buildProfilePrompt, OpenRouterProfiler } from "../../src/infra/llm/openrouter-profiler";

const now = new Date("2026-10-15T20:00:00+03:00");
const written = (daysAgo: number, tasksSeen: number) => ({
  text: "Acts on the first SOUND.",
  writtenAt: new Date(now.getTime() - daysAgo * DAY),
  tasksSeen,
});

describe("profileIsStale (ADR 0020)", () => {
  it("never before 10 closed tasks, then once there is none", () => {
    expect(profileIsStale(null, PROFILE_EVERY_TASKS - 1, now)).toBe(false);
    expect(profileIsStale(null, PROFILE_EVERY_TASKS, now)).toBe(true);
  });

  it("after every 10 more closed tasks", () => {
    expect(profileIsStale(written(0, 10), 19, now)).toBe(false);
    expect(profileIsStale(written(0, 10), 20, now)).toBe(true);
  });

  it("after a week, only when a task closed since", () => {
    expect(profileIsStale(written(8, 10), 10, now)).toBe(false);
    expect(profileIsStale(written(8, 10), 11, now)).toBe(true);
    expect(profileIsStale(written(6, 10), 11, now)).toBe(false);
  });
});

describe("OpenRouterProfiler", () => {
  const input = {
    now,
    timeZone: "Europe/Istanbul",
    recipient: { name: "Eren" },
    previous: "Sleeps through SILENT.",
    history: [],
  };

  it("sends the previous profile and the history in local time, and returns the text", async () => {
    const bodies: string[] = [];
    const fakeFetch: typeof fetch = (_url, init) => {
      bodies.push(init?.body as string);
      return Promise.resolve(
        new Response(
          JSON.stringify({
            choices: [{ message: { content: JSON.stringify({ profile: "  Acts on SOUND. " }) } }],
          }),
          { status: 200 },
        ),
      );
    };
    const profiler = new OpenRouterProfiler({ apiKey: "k", model: "m", fetch: fakeFetch });
    expect(await profiler.profile(input)).toBe("Acts on SOUND.");
    const body = JSON.parse(bodies[0] ?? "{}") as { messages: { content: string }[] };
    expect(body.messages[1]?.content).toBe(buildProfilePrompt(input));
    expect(buildProfilePrompt(input)).toContain('"previousProfile": "Sleeps through SILENT."');
    expect(buildProfilePrompt(input)).toContain("2026-10-15T20:00:00+03:00");
  });
});

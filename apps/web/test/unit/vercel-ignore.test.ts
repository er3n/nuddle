// The Vercel ignored build step skips only Android-only changes (docs/bootstrap-plan.md, Stage 7).
import { describe, expect, it } from "vitest";
import { shouldBuild } from "../../scripts/vercel-ignore";

describe("vercel ignored build step", () => {
  it("skips a change confined to the Android app", () => {
    expect(shouldBuild(["apps/android/app/build.gradle.kts", "apps/android/README.md"])).toBe(
      false,
    );
  });

  it("builds when anything outside the Android app changed", () => {
    expect(
      shouldBuild(["apps/android/app/build.gradle.kts", "packages/contract/openapi.yaml"]),
    ).toBe(true);
    expect(shouldBuild(["docs/architecture.md"])).toBe(true);
    expect(shouldBuild(["supabase/migrations/20261011143500_tick_job.sql"])).toBe(true);
    expect(shouldBuild(["apps/androidx.txt"])).toBe(true);
  });

  it("builds when nothing changed (a redeploy of the same commit)", () => {
    expect(shouldBuild([])).toBe(true);
  });
});

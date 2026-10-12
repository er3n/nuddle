// Brand fixes (docs/ux/platform.md, Missed-ring detection; open-questions #22).
import { describe, expect, it } from "vitest";
import { BrandFix } from "../../src/api/schemas";
import { allBrandFixes, brandFix } from "../../src/domain/brand-fixes";

describe("brand fixes", () => {
  it("finds the brand whatever the case of Build.MANUFACTURER, with the name in the reason", () => {
    expect(brandFix("Xiaomi")).toMatchObject({
      brand: "xiaomi",
      name: "Xiaomi",
      reason: "Xiaomi telefonlar uygulamaları arka planda durdurabiliyor.",
    });
    expect(brandFix("samsung").brand).toBe("samsung");
    expect(brandFix(" HUAWEI ").brand).toBe("huawei");
  });

  it("covers sub-brands with their own name", () => {
    expect(brandFix("Redmi")).toMatchObject({ brand: "xiaomi", name: "Redmi" });
    expect(brandFix("OnePlus")).toMatchObject({ brand: "oppo", name: "OnePlus" });
    expect(brandFix("HONOR")).toMatchObject({ brand: "huawei", name: "Honor" });
  });

  it("gives every other phone the default steps without a brand sentence", () => {
    const fix = brandFix("Google");
    expect(fix).toMatchObject({ brand: "default", name: null, reason: null });
    expect(fix.steps.map((s) => s.id)).toEqual(["battery"]);
  });

  it("the first version covers Xiaomi, Samsung, Huawei, Oppo and Vivo", () => {
    for (const m of ["xiaomi", "samsung", "huawei", "oppo", "vivo"])
      expect(brandFix(m).brand).toBe(m);
  });

  it("every fix in the file matches the API schema, ends in a target that always exists and has no em dash", () => {
    for (const fix of allBrandFixes()) {
      expect(() => BrandFix.parse(fix)).not.toThrow();
      for (const step of fix.steps) {
        expect(step.targets.at(-1)?.action).toMatch(/^android\.settings\./);
        expect(JSON.stringify(step)).not.toContain("\u2014");
      }
      expect(fix.reason ?? "").not.toContain("{name}");
    }
  });
});

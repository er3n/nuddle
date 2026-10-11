// DRT codes are stable identifiers shown to users and sent to Crashlytics (screen 6f).
import { describe, expect, it } from "vitest";
import { ApiError, errorNames } from "../../src/api/errors";

describe("DRT error codes", () => {
  it("gives every error a unique DRT-xxxx code", () => {
    const codes = errorNames.map((name) => new ApiError(name).code);
    expect(codes.every((c) => /^DRT-\d{4}$/.test(c))).toBe(true);
    expect(new Set(codes).size).toBe(codes.length);
  });
});

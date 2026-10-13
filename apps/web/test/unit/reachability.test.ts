import { describe, expect, it } from "vitest";
import {
  deriveReachability,
  deviceReachability,
  logOut,
  type Device,
  type PermissionSummary,
} from "../../src/domain/reachability";
import { allPermissions } from "../builders";

function device(permissions: Partial<PermissionSummary>, id = "d1"): Device {
  return {
    id,
    personId: "eren",
    fid: `fid-${id}`,
    permissions: { ...allPermissions, ...permissions },
    lastSeenAt: new Date("2026-10-11T20:00:00+03:00"),
  };
}

describe("reachability", () => {
  it.each<[string, Partial<PermissionSummary>, string]>([
    ["everything granted", {}, "FULL_SCREEN"],
    ["no full screen", { fullScreen: false }, "SOUND"],
    ["no exact alarm", { exactAlarm: false }, "SOUND"],
    ["alarm channel off", { channels: { poke_silent: true, poke_alarm: false } }, "SILENT"],
    ["both poke channels off", { channels: { poke_silent: false, poke_alarm: false } }, "NONE"],
    ["notifications off", { notifications: false }, "NONE"],
  ])("%s -> %s", (_name, permissions, expected) => {
    expect(deviceReachability(device(permissions))).toBe(expected);
  });

  it("takes the best signed-in device", () => {
    expect(deriveReachability([device({ fullScreen: false }, "a"), device({}, "b")])).toBe(
      "FULL_SCREEN",
    );
    expect(deriveReachability([])).toBe("NONE");
  });

  it("is NONE after logout", () => {
    expect(deriveReachability([logOut(device({}))])).toBe("NONE");
  });
});

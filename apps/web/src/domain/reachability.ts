// Reachability (docs/domain.md, Reachability; docs/ux/platform.md, Permissions and channels):
// the highest level the server can reach a person with, derived from their signed-in devices.
import type { DeviceLevel, PersonId } from "./types";

export type Reachability = DeviceLevel | "NONE";

/** What a device reports on launch and whenever a permission or channel changes. */
export interface PermissionSummary {
  notifications: boolean;
  exactAlarm: boolean;
  fullScreen: boolean;
  /** Notification channel ids (platform.md) to enabled state. A disabled channel is missing. */
  channels: {
    poke_silent: boolean;
    poke_alarm: boolean;
  };
}

export interface Device {
  id: string;
  personId: PersonId;
  /** Null after logout: the device can no longer be reached. */
  fid: string | null;
  permissions: PermissionSummary;
  lastSeenAt: Date;
}

const order: readonly Reachability[] = ["NONE", "SILENT", "SOUND", "FULL_SCREEN"];

export function deviceReachability(device: Device): Reachability {
  const p = device.permissions;
  if (device.fid === null || !p.notifications) return "NONE";
  if (p.channels.poke_alarm) {
    return p.fullScreen && p.exactAlarm ? "FULL_SCREEN" : "SOUND";
  }
  return p.channels.poke_silent ? "SILENT" : "NONE";
}

export function deriveReachability(devices: readonly Device[]): Reachability {
  let best: Reachability = "NONE";
  for (const d of devices) {
    const level = deviceReachability(d);
    if (order.indexOf(level) > order.indexOf(best)) best = level;
  }
  return best;
}

/** Logging out clears the FID: pokes stop ringing on that phone (docs/product.md). */
export function logOut(device: Device): Device {
  return { ...device, fid: null };
}

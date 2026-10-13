// Shared steps for HTTP tests: sign a person up the way the app does (register, then device).
import { randomUUID } from "node:crypto";
import { v7 as uuidv7 } from "uuid";
import type { PermissionSummary } from "../../src/domain/reachability";
import { aPerson, allPermissions } from "../builders";
import type { ApiClient, TestServer } from "../support/server";

export interface SignedUp {
  id: string;
  phone: string;
  api: ApiClient;
  deviceId: string;
}

export async function signUp(
  server: TestServer,
  name: string,
  permissions: PermissionSummary = allPermissions,
): Promise<SignedUp> {
  const { phone, displayName } = aPerson(name);
  const id = randomUUID();
  const api = server.client(await server.tokenFor(id, phone));
  const r = await api.POST("/api/v1/commands/register", {
    body: { idempotencyKey: uuidv7(), displayName, timeZone: "Europe/Istanbul" },
  });
  if (!r.data) throw new Error(`register failed: ${JSON.stringify(r.error)}`);
  const d = await api.POST("/api/v1/devices", {
    body: {
      deviceId: null,
      fid: `fid-${id}`,
      platform: "android",
      appVersion: "test",
      permissions,
    },
  });
  if (!d.data) throw new Error(`device failed: ${JSON.stringify(d.error)}`);
  return { id, phone, api, deviceId: d.data.deviceId };
}

/** Selin invites Eren and Eren accepts: both connected as spouses. */
export async function connect(a: SignedUp, b: SignedUp): Promise<void> {
  const invite = await a.api.POST("/api/v1/commands/create_invite", {
    body: {
      idempotencyKey: uuidv7(),
      phone: b.phone,
      relationship: "spouse",
      prefilledName: "Eren",
    },
  });
  if (!invite.data) throw new Error("invite failed");
  const accepted = await b.api.POST("/api/v1/commands/accept_invite", {
    body: { idempotencyKey: uuidv7(), code: invite.data.code, relationship: "spouse" },
  });
  if (!accepted.data) throw new Error("accept failed");
}

/** A task from `sender` to `recipient` through the preview's create_task. */
export async function createTask(
  sender: SignedUp,
  recipient: SignedUp,
  overrides: { finishMode?: "done" | "photo"; deadline?: string } = {},
): Promise<string> {
  const r = await sender.api.POST("/api/v1/commands/create_task", {
    body: {
      idempotencyKey: uuidv7(),
      recipientId: recipient.id,
      title: "Çöpü at",
      detail: null,
      deadline: overrides.deadline ?? "2026-10-12T19:00:00+03:00",
      finishMode: overrides.finishMode ?? "done",
      rawText: null,
    },
  });
  if (!r.data) throw new Error(`create_task failed: ${JSON.stringify(r.error)}`);
  return r.data.taskId;
}

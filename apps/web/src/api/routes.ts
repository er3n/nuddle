// Public API routes under /api/v1 (ADR 0003, 0006, 0008). Each route is defined with its zod
// schemas so validation and the OpenAPI document come from the same source.
import { createRoute, type OpenAPIHono, type z } from "@hono/zod-openapi";
import type { Command } from "../domain/task-machine";
import type { Person } from "../domain/types";
import type { Caller } from "../infra/auth/jwt";
import type { AppDeps } from "./deps";
import { ApiError } from "./errors";
import * as s from "./schemas";
import * as svc from "./services";

export interface AppEnv {
  Variables: { requestId: string; caller: Caller };
}

const json = <T extends z.ZodType>(schema: T) => ({ "application/json": { schema } });

const errors = (codes: readonly (400 | 401 | 403 | 404 | 409 | 422 | 502)[]) =>
  Object.fromEntries(
    codes.map((code) => [
      code,
      { description: "Error with a DRT code", content: json(s.ErrorSchema) },
    ]),
  );

const commandErrors = errors([400, 401, 403, 404, 409, 422]);

function command<I extends z.ZodType, O extends z.ZodType>(
  name: string,
  operationId: string,
  summary: string,
  input: I,
  output: O,
) {
  return createRoute({
    method: "post",
    path: `/v1/commands/${name}`,
    operationId,
    summary,
    tags: ["commands"],
    security: [{ bearer: [] }],
    request: { body: { required: true, content: json(input) } },
    responses: {
      200: {
        description: "The result; a replay with the same key returns it again",
        content: json(output),
      },
      ...commandErrors,
    },
  });
}

const iso = (d: Date): string => d.toISOString();

function meDto(p: Person): z.infer<typeof s.Me> {
  return {
    id: p.id,
    phone: p.phone,
    displayName: p.displayName,
    quietHours: p.quietHours,
    useConnectionsForMyTasks: p.useConnectionsForMyTasks,
    notifyMeForOthersTasks: p.notifyMeForOthersTasks,
  };
}

export function registerRoutes(app: OpenAPIHono<AppEnv>, deps: AppDeps): void {
  const person = (caller: Caller) => svc.requirePerson(deps, caller.id);

  // ---- auth (no token) ----------------------------------------------------------------------

  app.openapi(
    createRoute({
      method: "post",
      path: "/v1/auth/otp",
      operationId: "sendOtp",
      summary: "Send a one-time code to the phone (sign-up and sign-in are one flow)",
      tags: ["auth"],
      security: [],
      request: { body: { required: true, content: json(s.SendOtpRequest) } },
      responses: { 200: { description: "Code sent", content: json(s.Ok) }, ...errors([400, 502]) },
    }),
    async (c) => {
      try {
        await deps.auth.sendOtp(c.req.valid("json").phone);
      } catch (error) {
        console.error("send otp failed", error);
        throw new ApiError("upstream_failed", "could not send the code");
      }
      return c.json({ ok: true as const }, 200);
    },
  );

  const sessionDto = async (session: Awaited<ReturnType<typeof deps.auth.refresh>>) => {
    if (!session) throw new ApiError("invalid_otp");
    const p = await deps.tokens.verify(session.accessToken);
    const existing = await svc.requirePerson(deps, p.id).catch(() => null);
    return {
      accessToken: session.accessToken,
      refreshToken: session.refreshToken,
      expiresAt: iso(session.expiresAt),
      personId: p.id,
      registered: existing !== null,
    };
  };

  app.openapi(
    createRoute({
      method: "post",
      path: "/v1/auth/verify",
      operationId: "verifyOtp",
      summary: "Exchange the code for a session",
      tags: ["auth"],
      security: [],
      request: { body: { required: true, content: json(s.VerifyOtpRequest) } },
      responses: {
        200: { description: "Signed in", content: json(s.Session) },
        ...errors([400, 401, 502]),
      },
    }),
    async (c) => {
      const { phone, code } = c.req.valid("json");
      // TODO(NUD-24): attempt limits and the 15 minute wait (ADR 0011, screen 6c).
      return c.json(await sessionDto(await deps.auth.verifyOtp(phone, code)), 200);
    },
  );

  app.openapi(
    createRoute({
      method: "post",
      path: "/v1/auth/refresh",
      operationId: "refreshSession",
      summary: "Exchange a refresh token for a new session",
      tags: ["auth"],
      security: [],
      request: { body: { required: true, content: json(s.RefreshRequest) } },
      responses: {
        200: { description: "New session", content: json(s.Session) },
        ...errors([400, 401, 502]),
      },
    }),
    async (c) =>
      c.json(await sessionDto(await deps.auth.refresh(c.req.valid("json").refreshToken)), 200),
  );

  // ---- sync, devices, photos ----------------------------------------------------------------

  app.openapi(
    createRoute({
      method: "get",
      path: "/v1/sync",
      operationId: "sync",
      summary: "Everything that changed for the caller since the cursor (ADR 0006)",
      tags: ["sync"],
      security: [{ bearer: [] }],
      request: { query: s.SyncQuery },
      responses: {
        200: { description: "Changes and the next cursor", content: json(s.SyncResponse) },
        ...errors([400, 401, 403]),
      },
    }),
    async (c) => {
      const q = c.req.valid("query");
      const r = await svc.sync(deps, await person(c.get("caller")), {
        cursor: Number(q.cursor ?? "0"),
        deviceId: q.deviceId ?? null,
      });
      return c.json(
        {
          ...r,
          me: meDto(r.me),
          connections: r.connections.map((cn) => ({ ...cn, connectedAt: iso(cn.connectedAt) })),
          tasks: r.tasks.map((t) => ({
            id: t.id,
            senderId: t.senderId,
            recipientId: t.recipientId,
            title: t.title,
            detail: t.detail,
            deadline: iso(t.deadline),
            finishMode: t.finishMode,
            rawText: t.rawText,
            status: t.status,
            late: t.late,
            planVersion: t.planVersion,
            statusChangedAt: iso(t.statusChangedAt),
            inRecipientList: t.inRecipientList,
            snoozesUsed: t.snoozesUsed,
            createdAt: iso(t.createdAt),
            photoSentAt: t.photoSentAt === null ? null : iso(t.photoSentAt),
            reRequestNote: t.reRequestNote,
          })),
          sentInvites: r.sentInvites.map((i) => ({
            id: i.id,
            code: i.code,
            inviteePhone: i.inviteePhone,
            prefilledName: i.prefilledName,
            relationship: i.relationship,
            status: i.status,
            createdAt: iso(i.createdAt),
          })),
          receivedInvites: r.receivedInvites.map((i) => ({
            id: i.id,
            code: i.code,
            inviterId: i.inviterId,
            inviterName: i.inviterName,
            prefilledName: i.prefilledName,
            relationship: i.relationship,
            status: i.status,
            createdAt: iso(i.createdAt),
          })),
          events: r.events.map((e) => ({
            id: e.id,
            taskId: e.taskId,
            actorId: e.actorId,
            type: e.type,
            payload: e.payload,
            at: iso(e.at),
          })),
          plans: r.plans.map((p) => ({
            taskId: p.taskId,
            version: p.version,
            steps: p.steps.map((st) => ({ ...st, plannedAt: iso(st.plannedAt) })),
          })),
        },
        200,
      );
    },
  );

  app.openapi(
    createRoute({
      method: "post",
      path: "/v1/devices",
      operationId: "registerDevice",
      summary: "Register the device: FCM token and permission summary (source of reachability)",
      tags: ["devices"],
      security: [{ bearer: [] }],
      request: { body: { required: true, content: json(s.RegisterDeviceRequest) } },
      responses: {
        200: {
          description: "Device id and the caller's reachability",
          content: json(s.RegisterDeviceResult),
        },
        ...errors([400, 401, 403]),
      },
    }),
    async (c) =>
      c.json(
        await svc.registerDevice(deps, await person(c.get("caller")), c.req.valid("json")),
        200,
      ),
  );

  app.openapi(
    createRoute({
      method: "post",
      path: "/v1/photos/upload-url",
      operationId: "createPhotoUploadUrl",
      summary: "A signed upload URL for the photo that finishes a task",
      tags: ["photos"],
      security: [{ bearer: [] }],
      request: { body: { required: true, content: json(s.PhotoUploadUrlRequest) } },
      responses: {
        200: {
          description: "Upload URL and the path to send with photo_sent",
          content: json(s.PhotoUploadUrlResult),
        },
        ...errors([400, 401, 403, 404, 409]),
      },
    }),
    async (c) =>
      c.json(
        await svc.photoUploadUrl(deps, await person(c.get("caller")), c.req.valid("json")),
        200,
      ),
  );

  app.openapi(
    createRoute({
      method: "post",
      path: "/v1/photos/view-url",
      operationId: "createPhotoViewUrl",
      summary: "A short-lived signed URL to the photo waiting for approval (4f)",
      tags: ["photos"],
      security: [{ bearer: [] }],
      request: { body: { required: true, content: json(s.PhotoViewUrlRequest) } },
      responses: {
        200: { description: "Where to load the photo from", content: json(s.PhotoViewUrlResult) },
        ...errors([400, 401, 403, 404, 409]),
      },
    }),
    async (c) => {
      const r = await svc.photoViewUrl(
        deps,
        await person(c.get("caller")),
        c.req.valid("json").taskId,
      );
      return c.json({ url: r.url, sentAt: iso(r.sentAt) }, 200);
    },
  );

  // ---- commands -----------------------------------------------------------------------------

  app.openapi(
    command(
      "register",
      "register",
      "Create or rename the caller's person (name screen 0e)",
      s.RegisterCommand,
      s.Me,
    ),
    async (c) => c.json(meDto(await svc.register(deps, c.get("caller"), c.req.valid("json"))), 200),
  );

  app.openapi(
    command(
      "write_task",
      "writeTask",
      "Free text: create a task or ask one question",
      s.WriteTaskCommand,
      s.WriteTaskResult,
    ),
    async (c) =>
      c.json(await svc.writeTask(deps, await person(c.get("caller")), c.req.valid("json")), 200),
  );

  app.openapi(
    command(
      "create_task",
      "createTask",
      "Create a task from the preview's fields",
      s.CreateTaskCommand,
      s.TaskCommandResult,
    ),
    async (c) => {
      const b = c.req.valid("json");
      return c.json(
        await svc.createTask(deps, await person(c.get("caller")), {
          ...b,
          deadline: new Date(b.deadline),
        }),
        200,
      );
    },
  );

  const taskCommand = <I extends z.ZodType<{ idempotencyKey: string; taskId: string }>>(
    name: string,
    operationId: string,
    summary: string,
    input: I,
    toCommand: (body: z.infer<I>) => Command,
  ) =>
    app.openapi(command(name, operationId, summary, input, s.TaskCommandResult), async (c) => {
      // The generic route loses the validator's target types; the body was validated against `input`.
      const body = c.req.valid("json" as never) as z.infer<I>;
      return c.json(
        await svc.taskCommand(deps, await person(c.get("caller")), name, body, toCommand(body)),
        200,
      );
    });

  taskCommand("done", "done", "Recipient: Bitti", s.DoneCommand, (b) => ({
    type: "done",
    ...(b.cameraUnavailable === undefined ? {} : { cameraUnavailable: b.cameraUnavailable }),
  }));
  taskCommand(
    "photo_sent",
    "photoSent",
    "Recipient: the photo is uploaded",
    s.PhotoSentCommand,
    (b) => ({
      type: "photo_sent",
      photoPath: b.photoPath,
      takenAt: b.takenAt === undefined ? undefined : new Date(b.takenAt),
    }),
  );
  taskCommand(
    "cant_today",
    "cantToday",
    "Recipient: Bugün olmaz, with an optional note",
    s.CantTodayCommand,
    (b) => ({
      type: "cant_today",
      note: b.note,
    }),
  );
  taskCommand(
    "snooze",
    "snooze",
    "Recipient: Ertele. 15 then 10 minutes, at most twice, never past the deadline",
    s.SnoozeCommand,
    () => ({
      type: "snooze",
    }),
  );
  taskCommand(
    "did_it_later",
    "didItLater",
    "Recipient: Sonradan yaptım (after missed)",
    s.DidItLaterCommand,
    () => ({
      type: "did_it_later",
    }),
  );
  taskCommand(
    "cancel",
    "cancel",
    "Sender: cancel the task (also the undo toast)",
    s.CancelCommand,
    (b) => ({
      type: "cancel",
      note: b.note,
    }),
  );
  taskCommand(
    "edit",
    "edit",
    "Sender: change deadline, detail or finish mode",
    s.EditCommand,
    (b) => ({
      type: "edit",
      ...(b.deadline === undefined ? {} : { deadline: new Date(b.deadline) }),
      ...(b.detail === undefined ? {} : { detail: b.detail }),
      ...(b.finishMode === undefined ? {} : { finishMode: b.finishMode }),
    }),
  );
  taskCommand("approve", "approve", "Sender: approve the photo", s.ApproveCommand, () => ({
    type: "approve",
  }));
  taskCommand(
    "re_request",
    "reRequest",
    "Sender: ask for another photo",
    s.ReRequestCommand,
    (b) => ({
      type: "re_request",
      note: b.note,
    }),
  );
  taskCommand(
    "new_deadline",
    "newDeadline",
    "Sender: Yeni zaman ver / Süreyi uzat",
    s.NewDeadlineCommand,
    (b) => ({
      type: "new_deadline",
      deadline: new Date(b.deadline),
    }),
  );
  taskCommand(
    "sender_handles",
    "senderHandles",
    "Sender: Ben hallederim",
    s.SenderHandlesCommand,
    () => ({
      type: "sender_handles",
    }),
  );

  app.openapi(
    command(
      "report_fired",
      "reportFired",
      "Device: a planned step rang",
      s.ReportFiredCommand,
      s.ReportFiredResult,
    ),
    async (c) => {
      const b = c.req.valid("json");
      return c.json(
        await svc.reportFired(deps, await person(c.get("caller")), {
          ...b,
          firedAt: new Date(b.firedAt),
        }),
        200,
      );
    },
  );

  app.openapi(
    command(
      "create_invite",
      "createInvite",
      "Invite a phone number as a connection",
      s.CreateInviteCommand,
      s.CreateInviteResult,
    ),
    async (c) =>
      c.json(
        await svc.createInviteCommand(deps, await person(c.get("caller")), c.req.valid("json")),
        200,
      ),
  );
  app.openapi(
    command("accept_invite", "acceptInvite", "Invitee: İzin ver", s.AcceptInviteCommand, s.Ok),
    async (c) =>
      c.json(
        await svc.acceptInviteCommand(deps, await person(c.get("caller")), c.req.valid("json")),
        200,
      ),
  );
  app.openapi(
    command(
      "decline_invite",
      "declineInvite",
      "Invitee: Şimdi değil (never shown to the inviter)",
      s.DeclineInviteCommand,
      s.Ok,
    ),
    async (c) =>
      c.json(
        await svc.declineInviteCommand(deps, await person(c.get("caller")), c.req.valid("json")),
        200,
      ),
  );
  app.openapi(
    command(
      "revoke_invite",
      "revokeInvite",
      "Inviter: İptal et on a pending invite",
      s.RevokeInviteCommand,
      s.Ok,
    ),
    async (c) =>
      c.json(
        await svc.revokeInviteCommand(deps, await person(c.get("caller")), c.req.valid("json")),
        200,
      ),
  );
  app.openapi(
    command(
      "update_settings",
      "updateSettings",
      "Quiet hours and the human channel switches (5a, 7g); affected plans are redone",
      s.UpdateSettingsCommand,
      s.Me,
    ),
    async (c) =>
      c.json(
        meDto(await svc.updateSettings(deps, await person(c.get("caller")), c.req.valid("json"))),
        200,
      ),
  );
  app.openapi(
    command(
      "update_connection",
      "updateConnection",
      "My relationship to a connection and my switch for their tasks (5b)",
      s.UpdateConnectionCommand,
      s.Ok,
    ),
    async (c) =>
      c.json(
        await svc.updateConnection(deps, await person(c.get("caller")), c.req.valid("json")),
        200,
      ),
  );
  app.openapi(
    command(
      "remove_connection",
      "removeConnection",
      "Remove a connection (5b); open tasks between the two are cancelled, nobody is told",
      s.RemoveConnectionCommand,
      s.Ok,
    ),
    async (c) =>
      c.json(
        await svc.removeConnection(deps, await person(c.get("caller")), c.req.valid("json")),
        200,
      ),
  );
  app.openapi(
    command("log_out", "logOut", "Log out this device: its pokes stop", s.LogOutCommand, s.Ok),
    async (c) =>
      c.json(await svc.logOut(deps, await person(c.get("caller")), c.req.valid("json")), 200),
  );
  app.openapi(
    command(
      "delete_account",
      "deleteAccount",
      "Delete the account; open tasks are cancelled",
      s.DeleteAccountCommand,
      s.Ok,
    ),
    async (c) =>
      c.json(
        await svc.deleteAccount(deps, await person(c.get("caller")), c.req.valid("json")),
        200,
      ),
  );
}

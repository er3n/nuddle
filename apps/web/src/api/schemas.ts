// zod schemas of the public API. They are the contract source (ADR 0008): `mise run
// contract:gen` turns them into packages/contract/openapi.yaml.
import { z } from "@hono/zod-openapi";
import { errorNames } from "./errors";

export const ErrorSchema = z
  .object({
    error: z.object({
      code: z.string().openapi({ example: "DRT-4001" }),
      name: z.enum(errorNames),
      message: z.string(),
      requestId: z.string(),
    }),
  })
  .openapi("Error");

const Instant = z.iso
  .datetime({ offset: true })
  .openapi({ example: "2026-10-12T09:00:00+03:00", description: "ISO 8601 with offset" });
const Id = z.uuid();
const Phone = z
  .string()
  .regex(/^\+[1-9]\d{7,14}$/)
  .openapi({ example: "+905320000002", description: "E.164" });
const IdempotencyKey = z.uuid({ version: "v7" }).openapi({
  description: "Client generated UUIDv7; a replay returns the first result (ADR 0006)",
});
const Note = z.string().max(500).nullable();

const TaskStatus = z
  .enum(["SCHEDULED", "AWAITING_APPROVAL", "REJECTED", "DONE", "DROPPED", "MISSED", "CANCELLED"])
  .openapi("TaskStatus");
const FinishMode = z.enum(["done", "photo"]).openapi("FinishMode");
const EdgeType = z
  .enum(["spouse", "parent", "child", "sibling", "roommate", "other"])
  .openapi("EdgeType");
const DeviceLevel = z.enum(["SILENT", "SOUND", "FULL_SCREEN"]).openapi("DeviceLevel");
const Reachability = z.enum(["NONE", "SILENT", "SOUND", "FULL_SCREEN"]).openapi("Reachability");
const TaskEventType = z
  .enum([
    "created",
    "edited",
    "plan_generated",
    "poke_fired",
    "poke_lost",
    "snoozed",
    "done",
    "photo_sent",
    "approved",
    "re_requested",
    "dropped",
    "missed",
    "cancelled",
    "human_notified",
    "permission_missing",
  ])
  .openapi("TaskEventType");

const PermissionSummary = z
  .object({
    notifications: z.boolean(),
    exactAlarm: z.boolean(),
    fullScreen: z.boolean(),
    channels: z.object({ poke_silent: z.boolean(), poke_alarm: z.boolean() }),
  })
  .openapi("PermissionSummary");

// ---- auth -----------------------------------------------------------------------------------

export const SendOtpRequest = z.object({ phone: Phone }).openapi("SendOtpRequest");
export const VerifyOtpRequest = z
  .object({ phone: Phone, code: z.string().regex(/^\d{6}$/) })
  .openapi("VerifyOtpRequest");
export const RefreshRequest = z
  .object({ refreshToken: z.string().min(1) })
  .openapi("RefreshRequest");
export const Session = z
  .object({
    accessToken: z.string(),
    refreshToken: z.string(),
    expiresAt: Instant,
    personId: Id,
    /** False until the name screen (0e) sends `register`. */
    registered: z.boolean(),
  })
  .openapi("Session");

// ---- results --------------------------------------------------------------------------------

export const Ok = z.object({ ok: z.literal(true) }).openapi("Ok");

export const TaskCommandResult = z
  .object({ taskId: Id, status: TaskStatus, planVersion: z.int() })
  .openapi("TaskCommandResult");

export const Me = z
  .object({
    id: Id,
    phone: Phone,
    displayName: z.string(),
    quietHours: z.object({ start: z.string(), end: z.string(), timeZone: z.string() }),
    useConnectionsForMyTasks: z.boolean(),
    notifyMeForOthersTasks: z.boolean(),
  })
  .openapi("Me");

// ---- commands -------------------------------------------------------------------------------

const keyed = <T extends z.ZodRawShape>(shape: T) =>
  z.object({ idempotencyKey: IdempotencyKey, ...shape });
const taskKeyed = <T extends z.ZodRawShape>(shape: T) => keyed({ taskId: Id, ...shape });

export const RegisterCommand = keyed({
  displayName: z.string().trim().min(1).max(60),
  timeZone: z.string().min(1).openapi({ example: "Europe/Istanbul" }),
}).openapi("RegisterCommand");

export const WriteTaskCommand = keyed({ text: z.string().trim().min(1).max(500) }).openapi(
  "WriteTaskCommand",
);
export const WriteTaskResult = z
  .object({
    /** created: the undo toast shows; ask: the preview (2b) asks exactly one question. */
    kind: z.enum(["created", "ask"]),
    taskId: Id.nullable(),
    question: z.enum(["recipient", "title", "deadline"]).nullable(),
    draft: z
      .object({
        recipient: z.string().nullable(),
        title: z.string().nullable(),
        detail: z.string().nullable(),
        deadline: z.string().nullable(),
        finishMode: FinishMode,
      })
      .nullable(),
  })
  .openapi("WriteTaskResult");

export const CreateTaskCommand = keyed({
  recipientId: Id,
  title: z.string().trim().min(1).max(200),
  detail: z.string().max(200).nullable(),
  deadline: Instant,
  finishMode: FinishMode,
  rawText: z.string().max(500).nullable(),
}).openapi("CreateTaskCommand");

export const DoneCommand = taskKeyed({
  cameraUnavailable: z.boolean().optional(),
}).openapi("DoneCommand");
export const PhotoSentCommand = taskKeyed({ photoPath: z.string().min(1) }).openapi(
  "PhotoSentCommand",
);
export const CantTodayCommand = taskKeyed({ note: Note }).openapi("CantTodayCommand");
export const DidItLaterCommand = taskKeyed({}).openapi("DidItLaterCommand");
export const CancelCommand = taskKeyed({ note: Note }).openapi("CancelCommand");
export const EditCommand = taskKeyed({
  deadline: Instant.optional(),
  detail: z.string().max(200).nullable().optional(),
  finishMode: FinishMode.optional(),
}).openapi("EditCommand");
export const ApproveCommand = taskKeyed({}).openapi("ApproveCommand");
export const ReRequestCommand = taskKeyed({ note: Note }).openapi("ReRequestCommand");
export const NewDeadlineCommand = taskKeyed({ deadline: Instant }).openapi("NewDeadlineCommand");
export const SenderHandlesCommand = taskKeyed({}).openapi("SenderHandlesCommand");

export const ReportFiredCommand = taskKeyed({
  deviceId: Id,
  planVersion: z.int().min(1),
  seq: z.int().min(0),
  firedAt: Instant,
  /** The level that actually rang; FULL_SCREEN degrades to SOUND without the permission. */
  level: DeviceLevel,
  permissionMissing: z.boolean(),
}).openapi("ReportFiredCommand");
export const ReportFiredResult = z
  .object({ stopRinging: z.boolean() })
  .openapi("ReportFiredResult");

export const CreateInviteCommand = keyed({
  phone: Phone,
  relationship: EdgeType,
  prefilledName: z.string().trim().min(1).max(60),
}).openapi("CreateInviteCommand");
export const CreateInviteResult = z
  .object({ inviteId: Id, code: z.string() })
  .openapi("CreateInviteResult");
export const AcceptInviteCommand = keyed({
  code: z.string().min(1),
  relationship: EdgeType,
}).openapi("AcceptInviteCommand");
export const DeclineInviteCommand = keyed({ code: z.string().min(1) }).openapi(
  "DeclineInviteCommand",
);
export const LogOutCommand = keyed({ deviceId: Id }).openapi("LogOutCommand");
export const DeleteAccountCommand = keyed({}).openapi("DeleteAccountCommand");

// ---- devices and photos ---------------------------------------------------------------------

export const RegisterDeviceRequest = z
  .object({
    /** Null on first launch; afterwards the id this endpoint returned. */
    deviceId: Id.nullable(),
    fcmToken: z.string().min(1),
    platform: z.enum(["android", "ios"]),
    appVersion: z.string().min(1),
    permissions: PermissionSummary,
  })
  .openapi("RegisterDeviceRequest");
export const RegisterDeviceResult = z
  .object({ deviceId: Id, reachability: Reachability })
  .openapi("RegisterDeviceResult");

export const PhotoUploadUrlRequest = z.object({ taskId: Id }).openapi("PhotoUploadUrlRequest");
export const PhotoUploadUrlResult = z
  .object({
    /** Send this path back with `photo_sent`. */
    path: z.string(),
    /** PUT the image here. */
    url: z.string(),
  })
  .openapi("PhotoUploadUrlResult");

// ---- sync -----------------------------------------------------------------------------------

export const SyncQuery = z.object({
  cursor: z
    .string()
    .regex(/^\d+$/)
    .optional()
    .openapi({ param: { name: "cursor", in: "query" }, description: "Omit for a full sync" }),
  deviceId: Id.optional().openapi({
    param: { name: "deviceId", in: "query" },
    description: "Marks the device as seen online",
  }),
});

const SyncTask = z
  .object({
    id: Id,
    senderId: Id,
    recipientId: Id,
    title: z.string(),
    detail: z.string().nullable(),
    deadline: Instant,
    finishMode: FinishMode,
    /** Only for the sender. */
    rawText: z.string().nullable(),
    status: TaskStatus,
    late: z.boolean(),
    planVersion: z.int(),
    statusChangedAt: Instant,
    /** False once the task leaves the recipient's list (a missed card after one day). */
    inRecipientList: z.boolean(),
  })
  .openapi("SyncTask");

const SyncEvent = z
  .object({
    id: z.string(),
    taskId: Id,
    actorId: Id.nullable(),
    type: TaskEventType,
    payload: z
      .record(z.string(), z.unknown())
      .openapi({ type: "object", additionalProperties: true }),
    at: Instant,
  })
  .openapi("SyncEvent");

const SyncPlan = z
  .object({
    taskId: Id,
    /** Devices ignore steps from older versions; no steps means stop poking. */
    version: z.int(),
    steps: z.array(z.object({ seq: z.int(), plannedAt: Instant, level: DeviceLevel })),
  })
  .openapi("SyncPlan");

export const SyncResponse = z
  .object({
    cursor: z.string(),
    me: Me,
    connections: z.array(
      z
        .object({
          personId: Id,
          displayName: z.string(),
          /** What this person is to me. */
          relationship: EdgeType,
          status: z.enum(["pending", "active", "removed"]),
          reachability: Reachability,
        })
        .openapi("SyncConnection"),
    ),
    sentInvites: z.array(
      z
        .object({
          id: Id,
          code: z.string(),
          inviteePhone: Phone,
          prefilledName: z.string(),
          relationship: EdgeType,
          /** A declined invite looks open to the inviter. */
          status: z.enum(["open", "accepted", "revoked", "expired"]),
        })
        .openapi("SyncSentInvite"),
    ),
    receivedInvites: z.array(
      z
        .object({
          id: Id,
          code: z.string(),
          inviterId: Id,
          inviterName: z.string(),
          prefilledName: z.string(),
          relationship: EdgeType,
          status: z.enum(["open", "accepted", "declined", "revoked", "expired"]),
        })
        .openapi("SyncReceivedInvite"),
    ),
    tasks: z.array(SyncTask),
    events: z.array(SyncEvent),
    /** Only for tasks the caller receives: the device sets alarms from these. */
    plans: z.array(SyncPlan),
  })
  .openapi("SyncResponse");

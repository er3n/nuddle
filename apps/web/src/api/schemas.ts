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
      details: z
        .object({
          remainingAttempts: z.number().int().min(0).optional().openapi({
            description: "invalid_otp: codes left before the wait (screen 6c)",
          }),
          retryAt: z.string().optional().openapi({
            description: "otp_locked: ISO 8601, when a code can be asked for and checked again",
          }),
        })
        .optional(),
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
/** `by`: done by the deadline. `at`: a moment, the deadline is a grace after it (ADR 0017). */
const Timing = z.enum(["at", "by"]).openapi("Timing");
/** How long after the moment an `at` task is missed; the server keeps it between 15 and 720. */
const MissAfterMinutes = z
  .int()
  .min(1)
  .max(24 * 60);
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
        /** For an `at` task the moment. */
        deadline: z.string().nullable(),
        /** Always sent; optional so an app reading an older server's answer treats the draft as `by`. */
        timing: Timing.optional(),
        missAfterMinutes: MissAfterMinutes.nullable().optional(),
        finishMode: FinishMode,
      })
      .nullable(),
  })
  .openapi("WriteTaskResult");

export const CreateTaskCommand = keyed({
  recipientId: Id,
  title: z.string().trim().min(1).max(200),
  detail: z.string().max(200).nullable(),
  /** The picked time: the moment of an `at` task. */
  deadline: Instant,
  /** Defaults to `by`. */
  timing: Timing.optional(),
  missAfterMinutes: MissAfterMinutes.nullable().optional(),
  finishMode: FinishMode,
  rawText: z.string().max(500).nullable(),
}).openapi("CreateTaskCommand");

export const DoneCommand = taskKeyed({
  cameraUnavailable: z.boolean().optional(),
  /** When Bitti was tapped: a Bitti tapped offline before the deadline counts even if it reaches the server after it (6a). */
  doneAt: Instant.optional(),
}).openapi("DoneCommand");
export const PhotoSentCommand = taskKeyed({
  photoPath: z.string().min(1),
  /** When Gönder was tapped: a photo taken before the deadline counts even if its upload came late (6e). */
  takenAt: Instant.optional(),
}).openapi("PhotoSentCommand");
export const CantTodayCommand = taskKeyed({ note: Note }).openapi("CantTodayCommand");
export const DidItLaterCommand = taskKeyed({}).openapi("DidItLaterCommand");
/** "Kaldır" on the recipient's missed card (NUD-49). */
export const RemoveMissedCommand = taskKeyed({}).openapi("RemoveMissedCommand");
export const SnoozeCommand = taskKeyed({}).openapi("SnoozeCommand");
export const CancelCommand = taskKeyed({ note: Note }).openapi("CancelCommand");
export const EditCommand = taskKeyed({
  /** The picked time; an `at` task moves its moment and keeps its grace. Same for NewDeadlineCommand. */
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
export const RevokeInviteCommand = keyed({ inviteId: Id }).openapi("RevokeInviteCommand");
export const InvitePreviewParams = z.object({
  code: z
    .string()
    .min(1)
    .max(64)
    .openapi({ param: { name: "code", in: "path" } }),
});
/** Before sign-in, an invite link shows who invites and the name they typed (0b, 0e; ADR 0016). */
export const InvitePreview = z
  .object({ inviterName: z.string(), prefilledName: z.string() })
  .openapi("InvitePreview");
export const LogOutCommand = keyed({ deviceId: Id }).openapi("LogOutCommand");
export const DeleteAccountCommand = keyed({}).openapi("DeleteAccountCommand");

const LocalTime = z
  .string()
  .regex(/^([01]\d|2[0-3]):[0-5]\d$/)
  .openapi({ example: "23:00" });
export const UpdateSettingsCommand = keyed({
  /** In the caller's time zone; start may be after end (23:00-07:00 spans midnight). */
  quietHours: z
    .object({ start: LocalTime, end: LocalTime })
    .refine((q) => q.start !== q.end, { message: "quiet hours need a length" })
    .optional(),
  useConnectionsForMyTasks: z.boolean().optional(),
  notifyMeForOthersTasks: z.boolean().optional(),
}).openapi("UpdateSettingsCommand");
export const UpdateConnectionCommand = keyed({
  personId: Id,
  /** What this person is to me. */
  relationship: EdgeType.optional(),
  /** "Sibel'in işleri için bana haber verilebilir" (5b). */
  notifyMe: z.boolean().optional(),
}).openapi("UpdateConnectionCommand");
export const RemoveConnectionCommand = keyed({ personId: Id }).openapi("RemoveConnectionCommand");

// ---- devices and photos ---------------------------------------------------------------------

export const RegisterDeviceRequest = z
  .object({
    /** Null on first launch; afterwards the id this endpoint returned. */
    deviceId: Id.nullable(),
    /** Firebase Installation ID of this install (ADR 0015). */
    fid: z.string().min(1),
    platform: z.enum(["android", "ios"]),
    appVersion: z.string().min(1),
    permissions: PermissionSummary,
  })
  .openapi("RegisterDeviceRequest");
export const RegisterDeviceResult = z
  .object({ deviceId: Id, reachability: Reachability })
  .openapi("RegisterDeviceResult");

export const BrandFixQuery = z.object({
  manufacturer: z
    .string()
    .max(64)
    .openapi({ param: { name: "manufacturer", in: "query" }, description: "Build.MANUFACTURER" }),
});
const BrandFixTarget = z
  .object({
    /** An explicit activity, `package/class`. */
    component: z.string().optional(),
    /** A settings action. */
    action: z.string().optional(),
    /** The action needs `package:<application id>` as its data. */
    withPackage: z.boolean().optional(),
  })
  .openapi("BrandFixTarget");
const BrandFixStep = z
  .object({
    id: z.string(),
    title: z.string(),
    hint: z.string(),
    /** The device can tell by itself that the step is done; otherwise it counts once opened. */
    check: z.enum(["battery_optimization"]).optional(),
    /** Tried in order; the first that opens wins. */
    targets: z.array(BrandFixTarget).min(1),
  })
  .openapi("BrandFixStep");
export const BrandFix = z
  .object({
    /** The fix used: a brand key, or `default`. */
    brand: z.string(),
    name: z.string().nullable(),
    reason: z.string().nullable(),
    steps: z.array(BrandFixStep).min(1),
  })
  .openapi("BrandFix");

export const PhotoUploadUrlRequest = z
  .object({
    taskId: Id,
    /** As on photo_sent: lets a photo taken in time upload after the task was marked missed. */
    takenAt: Instant.optional(),
  })
  .openapi("PhotoUploadUrlRequest");
export const PhotoUploadUrlResult = z
  .object({
    /** Send this path back with `photo_sent`. */
    path: z.string(),
    /** PUT the image here. */
    url: z.string(),
  })
  .openapi("PhotoUploadUrlResult");

export const PhotoViewUrlRequest = z.object({ taskId: Id }).openapi("PhotoViewUrlRequest");
export const PhotoViewUrlResult = z
  .object({
    /** GET the image here; valid for a few minutes. */
    url: z.string(),
    sentAt: Instant,
  })
  .openapi("PhotoViewUrlResult");

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
    /** When the task becomes missed. For an `at` task the moment plus its grace; show `remindAt` instead. */
    deadline: Instant,
    /** Optional so an app reading an older server's sync treats a task as `by`. */
    timing: Timing.optional(),
    /** The moment of an `at` task. */
    remindAt: Instant.nullable().optional(),
    finishMode: FinishMode,
    /** Only for the sender. */
    rawText: z.string().nullable(),
    status: TaskStatus,
    late: z.boolean(),
    planVersion: z.int(),
    statusChangedAt: Instant,
    /** False once the task leaves the recipient's list (a missed card after one day). */
    inRecipientList: z.boolean(),
    /**
     * Snoozes so far: the device picks the next snooze's length and "son hakkın" from it. Always sent; optional so an
     * app reading an older server's sync treats it as 0 instead of failing the whole sync.
     */
    snoozesUsed: z.int().optional(),
    /** When the sender wrote it: "Sibel yazdı · 14:32" (4c). Optional for the same reason as snoozesUsed. */
    createdAt: Instant.optional(),
    /** When the last photo was sent: "08:24'te gönderdin" (4e), "Eren fotoğraf gönderdi · 08:24" (4f). */
    photoSentAt: Instant.nullable().optional(),
    /** The note of the last "Tekrar iste" while the task is REJECTED (4g); null otherwise. */
    reRequestNote: z.string().nullable().optional(),
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
          /**
           * For the Call and Message icons on cards (docs/product.md, Human channel). Both sides agreed to the
           * connection. Optional so an app reading an older server's sync does not fail.
           */
          phone: Phone.optional(),
          /** Since when the connection is active: "3 aydır bağlısınız" (5b). Optional like `phone`. */
          connectedAt: Instant.optional(),
          /** My "Sibel'in işleri için bana haber verilebilir" switch (5b). Optional like `phone`. */
          notifyMe: z.boolean().optional(),
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
          /** When the invite was made: "Davet 14:32'de paylaşıldı" (1g). */
          createdAt: Instant,
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
          /** "2 sa önce" in the bell (1c, 1d). */
          createdAt: Instant,
        })
        .openapi("SyncReceivedInvite"),
    ),
    tasks: z.array(SyncTask),
    events: z.array(SyncEvent),
    /** Only for tasks the caller receives: the device sets alarms from these. */
    plans: z.array(SyncPlan),
  })
  .openapi("SyncResponse");

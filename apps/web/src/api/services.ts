// What each command, sync and tick do: load from Postgres, call the pure domain, persist the
// result in one transaction, then send pushes and delete photos (docs/architecture.md, Layering
// rules; ADR 0006, 0007).
import { randomBytes, randomUUID } from "node:crypto";
import {
  acceptInvite,
  changeConnection,
  createInvite,
  declineInvite,
  deleteAccount as deleteAccountDomain,
  inviteStatusForInviter,
  removeConnection as removeConnectionDomain,
  revokeInvite,
} from "../domain/connections";
import {
  resolveExtraction,
  withFirstOccurrence,
  type Extraction,
  type MissingField,
} from "../domain/extraction";
import {
  capReachedUntil,
  capWindowStart,
  laterOf,
  SMS_PHONE_DAILY_CAP,
  SMS_TOTAL_DAILY_CAP,
} from "../domain/cost-caps";
import {
  announcementProblems,
  announcementPushes,
  type AnnouncementContent,
} from "../domain/announcements";
import { afterWrongCode, lockedUntil } from "../domain/otp-attempts";
import { humanChannelAllowed } from "../domain/plan-pokes";
import { profileIsStale, RECENT_TASKS, type ProfileInput } from "../domain/profile";
import type { PersonProfiler } from "../domain/ports";
import { nextOccurrenceAt, type RepeatRule } from "../domain/repeat";
import { addMs, DAY, HOUR, MINUTE } from "../domain/time";
import { quietMomentOf } from "../domain/quiet-hours";
import { pokeStartOf } from "../domain/timing";
import {
  checkPlan,
  HISTORY_DAYS,
  HISTORY_TASKS,
  historyItem,
  type HistoryItem,
  type PlannerInput,
  type PlanProposal,
} from "../domain/planner";
import {
  deriveReachability,
  type PermissionSummary,
  type Reachability,
} from "../domain/reachability";
import {
  adoptPlan,
  applyCommand,
  createTask as createTaskDomain,
  isOpen,
  MISSED_CARD_TTL_MS,
  removeMissedCard,
  acknowledge as acknowledgeDomain,
  PHOTO_CLEANUP_RETRY_MS,
  takenInTime,
  type Command,
  type Outcome,
  type TaskContext,
} from "../domain/task-machine";
import {
  reportFired as reportFiredDomain,
  tick as tickDomain,
  visibleToRecipient,
  type ActionContext,
  type ActionResult,
  type ClaimedAction,
  type TickPort,
} from "../domain/tick";
import type {
  DeviceLevel,
  EdgeType,
  FinishMode,
  Person,
  PersonId,
  PokeProfile,
  PushMessage,
  Task,
  TaskEvent,
  TaskId,
  TaskStatus,
  Timing,
} from "../domain/types";
import {
  activeConnections,
  claimDueActions,
  clearOtpAttempts,
  currentPlan,
  deleteDevice,
  deleteDevicesOf,
  devicesOf,
  edgesTouching,
  eventsOf,
  findEdge,
  findIdempotentResult,
  findOtpAttempts,
  findInviteByCode,
  openInvitePreview,
  findInviteById,
  findPerson,
  findTask,
  inSyncReadTx,
  insertEvents,
  closedTaskCountOf,
  savePokeProfile,
  deleteInvitesOf,
  deletionPreviewOf,
  deleteLlmCallsOf,
  deleteSignInRecordsOf,
  insertScheduledAction,
  purgePerson,
  linkAskedExtraction,
  insertInvite,
  inWriteTx,
  lockOpenAction,
  markActionDone,
  markPersonDeleted,
  markMissedCardRemoved,
  markStepFired,
  rescheduleAction,
  persistOutcome,
  saveAvatarPath,
  pokedTasks,
  recentClosedTasksOf,
  saveIdempotentResult,
  saveOtpAttempts,
  setInviteStatus,
  syncRows,
  touchDevice,
  touchEdgesTo,
  touchTask,
  unclosedTasksBetween,
  unclosedTasksOf,
  updatePersonSettings,
  upsertDevice,
  upsertEdges,
  upsertPerson,
  type Tx,
  insertSmsSend,
  smsSendWindow,
  findAnnouncement,
  livePeopleByPhone,
  saveAnnouncement,
} from "../infra/db/store";
import type { AppDeps } from "./deps";
import { ApiError } from "./errors";
import { errorText, LlmCall, llmCapReachedUntil } from "./llm-log";

interface Effects {
  pushes: readonly PushMessage[];
  deletePhotosFor: readonly TaskId[];
  /** Single files to remove from Storage: a replaced or removed profile picture (NUD-72). */
  deleteFiles?: readonly string[];
  /** New plan versions the LLM planner may improve after commit (ADR 0017). */
  replans?: readonly { taskId: TaskId; version: number }[];
  /** Supabase auth users of a deleted account (NUD-89). */
  deleteAuthUsers?: readonly PersonId[];
}

const noEffects: Effects = { pushes: [], deletePhotosFor: [] };

function effectsOf(o: Outcome): Effects {
  return {
    pushes: o.pushes,
    deletePhotosFor: o.deletePhotos ? [o.task.id] : [],
    replans:
      o.plan.steps.length > 0 && isOpen(o.task.status)
        ? [{ taskId: o.task.id, version: o.plan.version }]
        : [],
  };
}

/** After commit. A failed hint or cleanup does not undo a committed command; sync is the truth. */
async function afterCommit(deps: AppDeps, effects: Effects): Promise<void> {
  for (const p of effects.pushes) {
    try {
      await deps.push.send(p);
    } catch (error) {
      console.error("push failed", p, error);
    }
  }
  for (const taskId of effects.deletePhotosFor) {
    try {
      await deps.photos.deleteForTask(taskId);
    } catch (error) {
      // The task's photo_cleanup timer tries again (NUD-25).
      console.error("photo deletion failed", taskId, error);
    }
  }
  for (const path of effects.deleteFiles ?? []) {
    try {
      await deps.photos.deleteFile(path);
    } catch (error) {
      // An orphan file in the bucket; nothing points at it any more.
      console.error("file deletion failed", path, error);
    }
  }
  for (const r of effects.replans ?? []) {
    await deps.background(() => refinePlan(deps, r.taskId, r.version));
  }
  for (const userId of effects.deleteAuthUsers ?? []) {
    try {
      await deps.auth.deleteUser(userId);
    } catch (error) {
      // The account_purge timer deletes it again before the rows go.
      console.error("auth user deletion failed", userId, error);
    }
  }
}

/** The end of the task's latest snooze if it is still ahead, otherwise `now`. */
function snoozeEnd(history: readonly TaskEvent[], now: Date): Date {
  const until = history
    .filter((e) => e.type === "snoozed" && typeof e.payload.until === "string")
    .map((e) => new Date(e.payload.until as string))
    .at(-1);
  return until && until.getTime() > now.getTime() ? until : now;
}

const deviceRank: Record<DeviceLevel, number> = { SILENT: 0, SOUND: 1, FULL_SCREEN: 2 };

/**
 * After a new plan v0 is committed, ask the LLM planner for a better one and, when it passes
 * `checkPlan`, store it as the next plan version (ADR 0017). Any failure keeps v0. Runs after the
 * response, so the caller never waits for it.
 */
async function refinePlan(deps: AppDeps, taskId: TaskId, version: number): Promise<void> {
  const planner = deps.planner;
  if (!planner) return;
  try {
    const now = deps.clock.now();
    const task = await findTask(deps.db, taskId);
    if (!task || task.planVersion !== version || !isOpen(task.status)) return;
    // A one-time message has one step and no plan to improve (NUD-79).
    if (task.finishMode === "notice") return;
    const sender = await findPerson(deps.db, task.senderId);
    const recipient = await findPerson(deps.db, task.recipientId);
    if (!sender || !recipient) return;
    const self = sender.id === recipient.id;
    const senderEdge = self ? null : await findEdge(deps.db, sender.id, recipient.id);
    const backEdge = self ? null : await findEdge(deps.db, recipient.id, sender.id);
    const events = await eventsOf(deps.db, task.id);
    const v0 = await currentPlan(deps.db, task);
    // Plan v0 already starts at the level reached after a snooze or re-request.
    const floor = v0.steps
      .map((s) => s.level)
      .filter((l): l is DeviceLevel => l !== "HUMAN")
      .reduce<DeviceLevel>((low, l) => (deviceRank[l] < deviceRank[low] ? l : low), "FULL_SCREEN");
    const notBefore = snoozeEnd(events, now);

    const past = await recentClosedTasksOf(
      deps.db,
      recipient.id,
      addMs(now, -HISTORY_DAYS * DAY),
      HISTORY_TASKS,
    );
    const closed = await closedTaskCountOf(deps.db, recipient.id);
    const senders = new Map<PersonId, { name: string; relationship: EdgeType | null }>();
    for (const p of past) {
      const id = p.task.senderId;
      if (senders.has(id) || id === recipient.id) continue;
      const person = await findPerson(deps.db, id);
      const edge = await findEdge(deps.db, recipient.id, id);
      senders.set(id, { name: person?.displayName ?? "?", relationship: edge?.type ?? null });
    }
    const connections = await activeConnections(deps.db, recipient.id);
    const history = past.map((p) =>
      historyItem(
        p.task,
        p.events,
        p.fired,
        senders.get(p.task.senderId) ?? { name: "self", relationship: null },
      ),
    );
    // This plan reads the profile there is; a stale one is written again after the plan, for
    // the next one (ADR 0020), so a slow profiler never delays the pokes.
    const profile = recipient.pokeProfile;
    const profileStale = profileIsStale(profile, closed, now);
    const writeStaleProfile = async () => {
      if (deps.profiler && profileStale) {
        await writeProfile(deps, deps.profiler, recipient, history, closed, now);
      }
    };

    const input: PlannerInput = {
      now,
      timeZone: recipient.quietHours.timeZone,
      task: {
        rawText: task.rawText,
        title: task.title,
        detail: task.detail,
        finishMode: task.finishMode,
        timing: task.timing,
        time: task.remindAt ?? task.deadline,
        startAt: pokeStartOf(task) ?? task.deadline,
        leadMinutes: task.leadMinutes,
        deadline: task.deadline,
        notBefore,
        floor: v0.steps.length > 0 ? floor : "SILENT",
        snoozesSoFar: events.filter((e) => e.type === "snoozed").length,
      },
      sender: { name: sender.displayName, self },
      recipient: { name: recipient.displayName },
      relationship:
        self || !senderEdge
          ? null
          : { recipientIsToSender: senderEdge.type, senderIsToRecipient: backEdge?.type ?? null },
      recipientConnections: connections.map((c) => ({
        name: c.person.displayName,
        relationship: c.edge.type,
      })),
      quietHours: recipient.quietHours,
      reachability: await reachabilityOf(deps.db, recipient.id),
      humanAllowed: humanChannelAllowed({ task, recipient, sender, senderEdge }),
      profile: profile?.text ?? null,
      history: history.slice(0, RECENT_TASKS),
    };
    const call = new LlmCall(deps, {
      kind: "plan",
      personId: recipient.id,
      taskId,
      planVersion: version,
      input,
    });
    // Over the recipient's daily cap the model is not asked and v0 stays (ADR 0024).
    const capped = await llmCapReachedUntil(deps, recipient.id, "plan");
    if (capped) {
      console.warn("poke planner: daily cap reached, keeping v0", taskId, recipient.id);
      await call.record("capped", { error: `daily cap until ${capped.toISOString()}` });
      await writeStaleProfile();
      return;
    }
    let proposal: PlanProposal;
    try {
      proposal = await planner.plan(input, call.sink);
    } catch (error) {
      await call.record("failed", { error: errorText(error) });
      await writeStaleProfile();
      throw error;
    }
    const steps = checkPlan(proposal, {
      task,
      recipient,
      sender,
      senderEdge,
      notBefore,
      floor: input.task.floor,
    });
    if (!steps) {
      console.warn("poke planner: no valid plan, keeping v0", taskId, proposal.reason);
      await call.record("rejected", { error: "checkPlan left no device step" });
      await writeStaleProfile();
      return;
    }
    const out = await inWriteTx(deps.db, async (tx) => {
      const fresh = await findTask(tx, taskId, true);
      if (!fresh) return null;
      const txNow = deps.clock.now();
      const r = adoptPlan(fresh, steps, version, await taskContext(tx, fresh, txNow));
      if (!r.ok) return null;
      const o = adjust(deps, r.value, txNow);
      await persistOutcome(tx, o, txNow);
      return o;
    });
    if (!out) {
      // The task moved on (a new version, closed) while the model was thinking.
      await call.record("stale");
      await writeStaleProfile();
      return;
    }
    await call.record("adopted", { adoptedVersion: out.plan.version });
    console.info("poke planner: plan adopted", taskId, out.plan.version, proposal.reason);
    for (const p of out.pushes) {
      try {
        await deps.push.send(p);
      } catch (error) {
        console.error("push failed", p, error);
      }
    }
    await writeStaleProfile();
  } catch (error) {
    console.error("poke planner failed, keeping v0", taskId, error);
  }
}

/** Ask the profiler for the person's new profile and store it; null when the call failed. */
async function writeProfile(
  deps: AppDeps,
  profiler: PersonProfiler,
  recipient: Person,
  history: HistoryItem[],
  closed: number,
  now: Date,
): Promise<PokeProfile | null> {
  const input: ProfileInput = {
    now,
    timeZone: recipient.quietHours.timeZone,
    recipient: { name: recipient.displayName },
    previous: recipient.pokeProfile?.text ?? null,
    history,
  };
  const call = new LlmCall(deps, { kind: "profile", personId: recipient.id, input });
  const capped = await llmCapReachedUntil(deps, recipient.id, "profile");
  if (capped) {
    await call.record("capped", { error: `daily cap until ${capped.toISOString()}` });
    return null;
  }
  try {
    const text = await profiler.profile(input, call.sink);
    const profile: PokeProfile = { text, writtenAt: now, tasksSeen: closed };
    await savePokeProfile(deps.db, recipient.id, profile);
    await call.record("written");
    return profile;
  } catch (error) {
    console.error("profiler failed, keeping the old profile", recipient.id, error);
    await call.record("failed", { error: errorText(error) });
    return null;
  }
}

const adjust = (deps: AppDeps, o: Outcome, now: Date): Outcome =>
  deps.e2e ? deps.e2e.adjustOutcome(o, now) : o;

/**
 * Run a command once per idempotency key (ADR 0006): the first result is stored with the key in
 * the same transaction, and a replay returns it without side effects. Failed commands store
 * nothing, so a retry runs again.
 */
async function runCommand<R>(
  deps: AppDeps,
  callerId: PersonId,
  name: string,
  key: string,
  fn: (tx: Tx, now: Date) => Promise<{ result: R; effects: Effects }>,
): Promise<R> {
  const now = deps.clock.now();
  const out = await inWriteTx(deps.db, async (tx) => {
    const prior = await findIdempotentResult(tx, callerId, key);
    if (prior) {
      if (prior.command !== name) throw new ApiError("idempotency_key_reused");
      return { result: prior.result as R, effects: noEffects };
    }
    const r = await fn(tx, now);
    await saveIdempotentResult(tx, { callerId, key, command: name, result: r.result, now });
    return r;
  });
  await afterCommit(deps, out.effects);
  return out.result;
}

/** The registered, not deleted person behind a token. */
export async function requirePerson(deps: AppDeps, id: PersonId): Promise<Person> {
  const person = await findPerson(deps.db, id);
  if (!person || person.deletedAt !== null) throw new ApiError("not_registered");
  return person;
}

async function reachabilityOf(
  tx: Parameters<typeof devicesOf>[0],
  personId: PersonId,
): Promise<Reachability> {
  return deriveReachability(await devicesOf(tx, personId));
}

async function taskContext(tx: Tx, task: Task, now: Date): Promise<TaskContext> {
  const sender = await findPerson(tx, task.senderId);
  const recipient = await findPerson(tx, task.recipientId);
  if (!sender || !recipient) throw new Error(`task ${task.id} has no sender or recipient row`);
  return {
    sender,
    recipient,
    senderEdge: sender.id === recipient.id ? null : await findEdge(tx, sender.id, recipient.id),
    reachability: await reachabilityOf(tx, task.recipientId),
    history: await eventsOf(tx, task.id),
    now,
  };
}

/** A task the caller sends or receives, locked; anything else looks like it does not exist. */
async function lockTaskOf(tx: Tx, callerId: PersonId, taskId: TaskId): Promise<Task> {
  const task = await findTask(tx, taskId, true);
  if (!task || (task.senderId !== callerId && task.recipientId !== callerId)) {
    throw new ApiError("not_found");
  }
  return task;
}

// ---- people ---------------------------------------------------------------------------------

export async function register(
  deps: AppDeps,
  caller: { id: PersonId; phone: string },
  input: { idempotencyKey: string; displayName: string; timeZone: string },
): Promise<Person> {
  return runCommand(deps, caller.id, "register", input.idempotencyKey, async (tx, now) => {
    const person = await upsertPerson(tx, {
      id: caller.id,
      phone: caller.phone,
      displayName: input.displayName,
      timeZone: input.timeZone,
      now,
    });
    return { result: person, effects: noEffects };
  });
}

// ---- tasks ----------------------------------------------------------------------------------

interface NewTask {
  recipientId: PersonId;
  title: string;
  detail: string | null;
  /** The picked time: the moment of an `at` task. */
  deadline: Date;
  timing?: Timing;
  missAfterMinutes?: number | null;
  leadMinutes?: number | null;
  finishMode: FinishMode;
  rawText: string | null;
  /** The rule of a repeating task (ADR 0021); the deadline is its first occurrence. */
  repeat?: RepeatRule | null;
}

async function createTaskTx(
  tx: Tx,
  deps: AppDeps,
  senderId: PersonId,
  input: NewTask,
  now: Date,
): Promise<{
  result: { taskId: TaskId; status: TaskStatus; planVersion: number };
  effects: Effects;
}> {
  const sender = await findPerson(tx, senderId);
  const recipient = await findPerson(tx, input.recipientId);
  if (!sender) throw new ApiError("not_registered");
  if (!recipient) throw new ApiError("not_connected");
  const r = createTaskDomain({
    id: randomUUID(),
    sender,
    recipient,
    edge: sender.id === recipient.id ? null : await findEdge(tx, sender.id, recipient.id),
    ...input,
    reachability: await reachabilityOf(tx, recipient.id),
    now,
  });
  if (!r.ok) throw new ApiError(r.error);
  const o = adjust(deps, r.value, now);
  await persistOutcome(tx, o, now);
  return {
    result: { taskId: o.task.id, status: o.task.status, planVersion: o.task.planVersion },
    effects: effectsOf(o),
  };
}

export type WriteTaskResult =
  | { kind: "created"; taskId: TaskId; question: null; draft: null }
  | {
      kind: "ask";
      taskId: null;
      question: MissingField | "quiet_hours";
      draft: Omit<Extraction, "missing" | "reply">;
      /** For `quiet_hours`: the recipient's quiet hours and the moment that starts poking as they end. */
      quiet?: { start: string; end: string; suggested: string } | null;
    }
  | { kind: "chat"; taskId: null; question: null; draft: null; reply: string };

/**
 * Free text in the one input box: extract, then create the task (undo toast), ask one question, or
 * answer text that is not a task with one sentence
 * (docs/domain.md, LLM task extraction). The LLM call runs outside the write transaction.
 */
export async function writeTask(
  deps: AppDeps,
  caller: Person,
  input: { idempotencyKey: string; text: string },
): Promise<WriteTaskResult> {
  const prior = await findIdempotentResult(deps.db, caller.id, input.idempotencyKey);
  if (prior) {
    if (prior.command !== "write_task") throw new ApiError("idempotency_key_reused");
    return prior.result as WriteTaskResult;
  }
  // Over the writer's daily cap nothing reaches the model (ADR 0024).
  const writeWait = await llmCapReachedUntil(deps, caller.id, "extraction");
  if (writeWait) {
    throw new ApiError("write_limit", "too many texts in the last 24 hours", {
      retryAt: writeWait.toISOString(),
    });
  }
  const now = deps.clock.now();
  const connections = await activeConnections(deps.db, caller.id);
  const extractionInput = {
    text: input.text,
    now,
    timeZone: caller.quietHours.timeZone,
    senderName: caller.displayName,
    connections: connections.map((c) => ({
      name: c.person.displayName,
      relationship: c.edge.type,
    })),
  };
  const call = new LlmCall(deps, {
    kind: "extraction",
    personId: caller.id,
    writeKey: input.idempotencyKey,
    input: extractionInput,
  });
  let extraction: Extraction;
  try {
    extraction = withFirstOccurrence(
      await deps.extractor.extract(extractionInput, call.sink),
      input.text,
      now,
      caller.quietHours.timeZone,
    );
  } catch (error) {
    console.error("extraction failed", error);
    await call.record("failed", { error: errorText(error) });
    throw new ApiError("upstream_failed", "extraction failed");
  }
  const resolved = resolveExtraction(
    extraction,
    caller,
    connections.map((c) => ({ personId: c.person.id, name: c.person.displayName })),
    now,
    input.text,
  );
  if (resolved.kind === "chat") {
    await call.record("chat");
    return { kind: "chat", taskId: null, question: null, draft: null, reply: resolved.reply };
  }
  // A moment inside the recipient's quiet hours waits for the sender to choose (NUD-120).
  const quiet =
    resolved.kind === "create"
      ? await quietMomentFor(deps, resolved.recipientId, {
          timing: resolved.timing,
          moment: resolved.deadline,
          leadMinutes: resolved.leadMinutes,
        })
      : null;
  if (resolved.kind === "ask" || quiet !== null) {
    const question = resolved.kind === "ask" ? resolved.question : "quiet_hours";
    await call.record("ask", { error: `asked ${question}` });
    const { recipient, title, detail, deadline, timing, missAfterMinutes, finishMode } = extraction;
    const leadMinutes = extraction.leadMinutes ?? null;
    return {
      kind: "ask",
      taskId: null,
      question,
      draft: {
        recipient,
        title,
        detail,
        deadline,
        timing,
        missAfterMinutes,
        leadMinutes,
        finishMode,
        repeat: extraction.repeat ?? null,
      },
      quiet,
    };
  }
  const written = await runCommand(
    deps,
    caller.id,
    "write_task",
    input.idempotencyKey,
    async (tx, txNow) => {
      const created = await createTaskTx(
        tx,
        deps,
        caller.id,
        { ...resolved, rawText: input.text },
        txNow,
      );
      const result: WriteTaskResult = {
        kind: "created",
        taskId: created.result.taskId,
        question: null,
        draft: null,
      };
      return { result, effects: created.effects };
    },
  );
  await call.record("create", { taskId: written.taskId });
  return written;
}

/** The recipient's quiet hours when the moment would start poking inside them, else null. */
async function quietMomentFor(
  deps: AppDeps,
  recipientId: PersonId,
  task: { timing: Timing; moment: Date; leadMinutes: number | null },
): Promise<{ start: string; end: string; suggested: string } | null> {
  if (task.timing !== "at") return null;
  const recipient = await findPerson(deps.db, recipientId);
  if (!recipient) return null;
  const hit = quietMomentOf(task, recipient.quietHours);
  if (!hit) return null;
  const { start, end } = recipient.quietHours;
  return { start, end, suggested: hit.suggested.toISOString() };
}

/** The preview (2b) after its one question: create with explicit fields. */
export async function createTask(
  deps: AppDeps,
  caller: Person,
  input: NewTask & { idempotencyKey: string },
): Promise<{ taskId: TaskId; status: TaskStatus; planVersion: number }> {
  const { idempotencyKey, ...task } = input;
  return runCommand(deps, caller.id, "create_task", idempotencyKey, async (tx, now) => {
    const created = await createTaskTx(tx, deps, caller.id, task, now);
    // The extraction that asked the question this preview answered now has its task (ADR 0019).
    if (task.rawText) {
      await linkAskedExtraction(
        tx,
        caller.id,
        task.rawText,
        created.result.taskId,
        addMs(now, -HOUR),
      );
    }
    return created;
  });
}

/** A sender or recipient action on one task (docs/domain.md, Task states). */
export async function taskCommand(
  deps: AppDeps,
  caller: Person,
  name: string,
  input: { idempotencyKey: string; taskId: TaskId },
  cmd: Command,
): Promise<{ taskId: TaskId; status: TaskStatus; planVersion: number }> {
  if (cmd.type === "photo_sent" && !cmd.photoPath.startsWith(`tasks/${input.taskId}/`)) {
    throw new ApiError("validation_failed", "photoPath is not an upload of this task");
  }
  return runCommand(deps, caller.id, name, input.idempotencyKey, async (tx, now) => {
    const task = await lockTaskOf(tx, caller.id, input.taskId);
    const r = applyCommand(task, cmd, caller.id, await taskContext(tx, task, now));
    if (!r.ok) throw new ApiError(r.error);
    const o = adjust(deps, r.value, now);
    await persistOutcome(tx, o, now);
    // A sender's cancel ends a series; any other close brings the next occurrence (ADR 0021).
    const next =
      cmd.type === "cancel" ? noEffects : await spawnNextOccurrence(tx, deps, task, o, now);
    return {
      result: { taskId: o.task.id, status: o.task.status, planVersion: o.task.planVersion },
      effects: mergeEffects(effectsOf(o), next),
    };
  });
}

/**
 * Kime changed on an open task (NUD-114): the task closes as `reassigned` for its recipient, and the
 * same task starts for the new one with a plan of its own. A repeating task starts a new series. The
 * result names the new task.
 */
export async function reassignTask(
  deps: AppDeps,
  caller: Person,
  input: { idempotencyKey: string; taskId: TaskId; recipientId: PersonId },
): Promise<{ taskId: TaskId; status: TaskStatus; planVersion: number }> {
  return runCommand(deps, caller.id, "reassign", input.idempotencyKey, async (tx, now) => {
    const task = await lockTaskOf(tx, caller.id, input.taskId);
    const r = applyCommand(
      task,
      { type: "reassign", recipientId: input.recipientId },
      caller.id,
      await taskContext(tx, task, now),
    );
    if (!r.ok) throw new ApiError(r.error);
    const closed = adjust(deps, r.value, now);
    await persistOutcome(tx, closed, now);
    const created = await createTaskTx(
      tx,
      deps,
      caller.id,
      {
        recipientId: input.recipientId,
        title: task.title,
        detail: task.detail,
        deadline: task.remindAt ?? task.deadline,
        timing: task.timing,
        missAfterMinutes:
          task.remindAt === null
            ? null
            : Math.round((task.deadline.getTime() - task.remindAt.getTime()) / MINUTE),
        leadMinutes: task.leadMinutes,
        finishMode: task.finishMode,
        rawText: task.rawText,
        repeat: task.repeat,
      },
      now,
    );
    return { result: created.result, effects: mergeEffects(effectsOf(closed), created.effects) };
  });
}

function mergeEffects(a: Effects, b: Effects): Effects {
  return {
    pushes: [...a.pushes, ...b.pushes],
    deletePhotosFor: [...a.deletePhotosFor, ...b.deletePhotosFor],
    deleteFiles: [...(a.deleteFiles ?? []), ...(b.deleteFiles ?? [])],
    replans: [...(a.replans ?? []), ...(b.replans ?? [])],
  };
}

/**
 * The next occurrence of a repeating task, created when an occurrence just closed (ADR 0021): the
 * same task at the next time the rule gives after the occurrence's own time (or now, when it closed
 * late). Nothing when the task is a one-off, was already closed, or the two people are no longer
 * connected (the series ends quietly).
 */
async function spawnNextOccurrence(
  tx: Tx,
  deps: AppDeps,
  before: Task,
  o: Outcome,
  now: Date,
): Promise<Effects> {
  const t = o.task;
  if (t.repeat === null || t.seriesId === null) return noEffects;
  if (!isOpen(before.status) || isOpen(t.status)) return noEffects;
  const sender = await findPerson(tx, t.senderId);
  const recipient = await findPerson(tx, t.recipientId);
  if (!sender || !recipient) return noEffects;
  const own = t.remindAt ?? t.deadline;
  const after = own.getTime() > now.getTime() ? own : now;
  const time = nextOccurrenceAt(t.repeat, after, recipient.quietHours.timeZone);
  const r = createTaskDomain({
    id: randomUUID(),
    sender,
    recipient,
    edge: sender.id === recipient.id ? null : await findEdge(tx, sender.id, recipient.id),
    title: t.title,
    detail: t.detail,
    deadline: time,
    timing: t.timing,
    missAfterMinutes:
      t.remindAt === null
        ? null
        : Math.round((t.deadline.getTime() - t.remindAt.getTime()) / MINUTE),
    leadMinutes: t.leadMinutes,
    finishMode: t.finishMode,
    rawText: null,
    repeat: t.repeat,
    seriesId: t.seriesId,
    reachability: await reachabilityOf(tx, recipient.id),
    now,
  });
  if (!r.ok) return noEffects;
  const next = adjust(deps, r.value, now);
  await persistOutcome(tx, next, now);
  return effectsOf(next);
}

/** "Kaldır" on the recipient's missed card (NUD-49): off their list now; the task stays missed. */
export async function removeMissed(
  deps: AppDeps,
  caller: Person,
  input: { idempotencyKey: string; taskId: TaskId },
): Promise<{ taskId: TaskId; status: TaskStatus; planVersion: number }> {
  return runCommand(deps, caller.id, "remove_missed", input.idempotencyKey, async (tx, now) => {
    const task = await lockTaskOf(tx, caller.id, input.taskId);
    const r = removeMissedCard(task, caller.id, now);
    if (!r.ok) throw new ApiError(r.error);
    await markMissedCardRemoved(tx, r.value);
    return {
      result: { taskId: task.id, status: task.status, planVersion: task.planVersion },
      effects: noEffects,
    };
  });
}

/** The recipient's phone acknowledges a task: reached, or seen (NUD-68). Logged once each; the sender syncs. */
export async function acknowledge(
  deps: AppDeps,
  caller: Person,
  input: { idempotencyKey: string; taskId: TaskId; seen: boolean },
): Promise<{ taskId: TaskId; status: TaskStatus; planVersion: number }> {
  return runCommand(deps, caller.id, "acknowledge", input.idempotencyKey, async (tx, now) => {
    const task = await lockTaskOf(tx, caller.id, input.taskId);
    const logged = new Set((await eventsOf(tx, task.id)).map((e) => e.type));
    const r = acknowledgeDomain(task, caller.id, input.seen, logged, now);
    if (!r.ok) throw new ApiError(r.error);
    if (r.value.events.length > 0) {
      await insertEvents(tx, r.value.events);
      await touchTask(tx, task.id);
    }
    return {
      result: { taskId: task.id, status: task.status, planVersion: task.planVersion },
      effects: { pushes: r.value.pushes, deletePhotosFor: [] },
    };
  });
}

/** A device reports a fired step (docs/domain.md, Device execution, step 5). */
export async function reportFired(
  deps: AppDeps,
  caller: Person,
  input: {
    idempotencyKey: string;
    taskId: TaskId;
    deviceId: string;
    planVersion: number;
    seq: number;
    firedAt: Date;
    level: DeviceLevel;
    permissionMissing: boolean;
  },
): Promise<{ stopRinging: boolean }> {
  return runCommand(deps, caller.id, "report_fired", input.idempotencyKey, async (tx, now) => {
    await touchDevice(tx, caller.id, input.deviceId, now);
    const task = await lockTaskOf(tx, caller.id, input.taskId);
    if (task.recipientId !== caller.id) throw new ApiError("not_recipient");
    const plan = await currentPlan(tx, task);
    const r = reportFiredDomain({ task, plan }, input, now);
    if (r.firedSeq !== null) {
      await markStepFired(tx, task.id, task.planVersion, r.firedSeq, input.firedAt);
    }
    if (r.events.length > 0) {
      await insertEvents(tx, r.events);
      await touchTask(tx, task.id);
    }
    return {
      result: { stopRinging: r.pushes.some((p) => p.hint === "task_cancelled") },
      effects: { pushes: r.pushes, deletePhotosFor: [] },
    };
  });
}

export async function photoUploadUrl(
  deps: AppDeps,
  caller: Person,
  input: { taskId: TaskId; takenAt?: string },
): Promise<{ path: string; url: string }> {
  const { taskId } = input;
  const task = await findTask(deps.db, taskId);
  if (!task || (task.senderId !== caller.id && task.recipientId !== caller.id)) {
    throw new ApiError("not_found");
  }
  if (task.recipientId !== caller.id) throw new ApiError("not_recipient");
  if (task.finishMode !== "photo") throw new ApiError("wrong_finish_mode");
  const takenAt = input.takenAt === undefined ? undefined : new Date(input.takenAt);
  const lateButInTime = task.status === "MISSED" && takenInTime(task, takenAt, deps.clock.now());
  if (!isOpen(task.status) && !lateButInTime) throw new ApiError("wrong_status");
  return deps.photos.createUploadUrl(taskId);
}

/**
 * The photo waiting for approval (4f), for the two people on the task: a short-lived signed URL to the
 * last one sent. Only while the task awaits approval; a closed task's photos are gone.
 */
export async function photoViewUrl(
  deps: AppDeps,
  caller: Person,
  taskId: TaskId,
): Promise<{ url: string; sentAt: Date }> {
  const task = await findTask(deps.db, taskId);
  if (!task || (task.senderId !== caller.id && task.recipientId !== caller.id)) {
    throw new ApiError("not_found");
  }
  if (task.status !== "AWAITING_APPROVAL") throw new ApiError("wrong_status");
  const sent = (await eventsOf(deps.db, taskId)).filter((e) => e.type === "photo_sent").at(-1);
  const path = sent?.payload.photoPath;
  if (!sent || typeof path !== "string") throw new ApiError("not_found");
  return { url: await deps.photos.createViewUrl(path), sentAt: sent.at };
}

// ---- profile picture (NUD-72) ------------------------------------------------------------------

const avatarFolder = (personId: PersonId) => `avatars/${personId}/`;

/** A signed upload URL for my profile picture; `set_avatar` with the path makes it mine. */
export async function avatarUploadUrl(
  deps: AppDeps,
  caller: Person,
): Promise<{ path: string; url: string }> {
  return deps.photos.createAvatarUploadUrl(caller.id);
}

/**
 * My profile picture: an uploaded path under my folder, or null for the initial again. The one
 * before is deleted after commit; my connections get the new path on their next sync.
 */
export async function setAvatar(
  deps: AppDeps,
  caller: Person,
  input: { idempotencyKey: string; avatarPath: string | null },
): Promise<Person> {
  const path = input.avatarPath;
  if (path !== null && (!path.startsWith(avatarFolder(caller.id)) || path.includes(".."))) {
    throw new ApiError("validation_failed");
  }
  return runCommand(deps, caller.id, "set_avatar", input.idempotencyKey, async (tx) => {
    await saveAvatarPath(tx, caller.id, path);
    await touchEdgesTo(tx, caller.id);
    const updated = await findPerson(tx, caller.id);
    if (!updated) throw new ApiError("not_registered");
    const deleteFiles =
      caller.avatarPath !== null && caller.avatarPath !== path ? [caller.avatarPath] : [];
    return { result: updated, effects: { pushes: [], deletePhotosFor: [], deleteFiles } };
  });
}

/** A short-lived signed URL to my own picture or a connection's; `not_found` when there is none. */
export async function avatarViewUrl(
  deps: AppDeps,
  caller: Person,
  personId: PersonId,
): Promise<{ url: string; avatarPath: string }> {
  if (personId !== caller.id) {
    const edge = await findEdge(deps.db, caller.id, personId);
    if (!edge || edge.status !== "active") throw new ApiError("not_connected");
  }
  const person = personId === caller.id ? caller : await findPerson(deps.db, personId);
  const path = person?.deletedAt === null ? person.avatarPath : null;
  if (path === null) throw new ApiError("not_found");
  return { url: await deps.photos.createViewUrl(path), avatarPath: path };
}

// ---- invites and account ----------------------------------------------------------------------

function inviteCode(): string {
  return randomBytes(9).toString("base64url");
}

export async function createInviteCommand(
  deps: AppDeps,
  caller: Person,
  input: { idempotencyKey: string; phone: string; relationship: EdgeType; prefilledName: string },
): Promise<{ inviteId: string; code: string }> {
  return runCommand(deps, caller.id, "create_invite", input.idempotencyKey, async (tx, now) => {
    const r = createInvite(
      {
        id: randomUUID(),
        inviterId: caller.id,
        inviteePhone: input.phone,
        relationship: input.relationship,
        prefilledName: input.prefilledName,
        code: inviteCode(),
      },
      caller,
    );
    if (!r.ok) throw new ApiError(r.error);
    await insertInvite(tx, r.value, now);
    return { result: { inviteId: r.value.id, code: r.value.code }, effects: noEffects };
  });
}

/**
 * Public: an invite link opened before sign-in (0b, 0e; ADR 0016). Only an open invite answers;
 * every other code is `not_found`, so a closed invite looks like one that never existed.
 */
export async function invitePreview(
  deps: AppDeps,
  code: string,
): Promise<{ inviterName: string; prefilledName: string }> {
  const preview = await openInvitePreview(deps.db, code);
  if (!preview) throw new ApiError("not_found");
  return preview;
}

export async function acceptInviteCommand(
  deps: AppDeps,
  caller: Person,
  input: { idempotencyKey: string; code: string; relationship: EdgeType },
): Promise<{ ok: true }> {
  return runCommand(deps, caller.id, "accept_invite", input.idempotencyKey, async (tx, now) => {
    const invite = await findInviteByCode(tx, input.code);
    if (!invite) throw new ApiError("not_found");
    const r = acceptInvite(invite, caller, input.relationship);
    if (!r.ok) throw new ApiError(r.error);
    await setInviteStatus(tx, invite.id, r.value.invite.status, now, true);
    await upsertEdges(tx, r.value.edges, now);
    return {
      result: { ok: true as const },
      effects: { pushes: r.value.pushes, deletePhotosFor: [] },
    };
  });
}

export async function declineInviteCommand(
  deps: AppDeps,
  caller: Person,
  input: { idempotencyKey: string; code: string },
): Promise<{ ok: true }> {
  return runCommand(deps, caller.id, "decline_invite", input.idempotencyKey, async (tx, now) => {
    const invite = await findInviteByCode(tx, input.code);
    if (!invite) throw new ApiError("not_found");
    const r = declineInvite(invite, caller);
    if (!r.ok) throw new ApiError(r.error);
    // Never shown to the inviter: the sync cursor does not move.
    await setInviteStatus(tx, invite.id, r.value.status, now, false);
    return { result: { ok: true as const }, effects: noEffects };
  });
}

export async function revokeInviteCommand(
  deps: AppDeps,
  caller: Person,
  input: { idempotencyKey: string; inviteId: string },
): Promise<{ ok: true }> {
  return runCommand(deps, caller.id, "revoke_invite", input.idempotencyKey, async (tx, now) => {
    const invite = await findInviteById(tx, input.inviteId);
    if (!invite) throw new ApiError("not_found");
    const r = revokeInvite(invite, caller);
    if (!r.ok) throw new ApiError(r.error);
    // Visible to both: the inviter's card goes, and the invitee's bell no longer offers it.
    await setInviteStatus(tx, invite.id, r.value.status, now, true);
    return { result: { ok: true as const }, effects: noEffects };
  });
}

export async function logOut(
  deps: AppDeps,
  caller: Person,
  input: { idempotencyKey: string; deviceId: string },
): Promise<{ ok: true }> {
  return runCommand(deps, caller.id, "log_out", input.idempotencyKey, async (tx) => {
    await deleteDevice(tx, caller.id, input.deviceId);
    await touchEdgesTo(tx, caller.id);
    return { result: { ok: true as const }, effects: noEffects };
  });
}

/**
 * Connections are removed and every unclosed task the person sends or receives is cancelled; the
 * other side is told (docs/product.md, Identity). The phone, name, picture, devices, invites, LLM
 * calls and the auth user go now, so the number can sign up again at once; the person row (with
 * `deleted_at`) and the tasks go with `account_purge` a week later (NUD-89).
 */
export async function deleteAccount(
  deps: AppDeps,
  caller: Person,
  input: { idempotencyKey: string },
): Promise<{ ok: true }> {
  return runCommand(deps, caller.id, "delete_account", input.idempotencyKey, async (tx, now) => {
    const open = await unclosedTasksOf(tx, caller.id);
    const contexts = [];
    for (const task of open) contexts.push({ task, ctx: await taskContext(tx, task, now) });
    const r = deleteAccountDomain(caller, await edgesTouching(tx, caller.id), contexts, now);
    await markPersonDeleted(tx, r.person);
    await deleteInvitesOf(tx, caller.id, caller.phone);
    await deleteSignInRecordsOf(tx, caller.phone);
    await insertScheduledAction(tx, r.purge, now);
    await upsertEdges(tx, r.edges, now);
    const pushes: PushMessage[] = [];
    const deletePhotosFor: TaskId[] = [];
    for (const cancelled of r.cancelled) {
      const o = adjust(deps, cancelled, now);
      await persistOutcome(tx, o, now);
      const e = effectsOf(o);
      pushes.push(...e.pushes);
      deletePhotosFor.push(...e.deletePhotosFor);
    }
    await deleteDevicesOf(tx, caller.id);
    await deleteLlmCallsOf(tx, caller.id);
    await touchEdgesTo(tx, caller.id);
    // The profile picture goes with the account (NUD-72).
    const deleteFiles = caller.avatarPath === null ? [] : [caller.avatarPath];
    return {
      result: { ok: true as const },
      effects: { pushes, deletePhotosFor, deleteFiles, deleteAuthUsers: [caller.id] },
    };
  });
}

export interface DeletionPreview {
  personId: PersonId;
  displayName: string;
  tasks: number;
  openTasks: number;
  connections: number;
}

/**
 * A deletion requested by email without the app (NUD-88): what `deleteAccountByPhone` would
 * touch, or null when no live account has that number.
 */
export async function deletionPreview(
  deps: AppDeps,
  phone: string,
): Promise<DeletionPreview | null> {
  const found = await deletionPreviewOf(deps.db, phone);
  if (!found) return null;
  const { person, ...counts } = found;
  return { personId: person.id, displayName: person.displayName, ...counts };
}

/** The same deletion as in the app, for the account with this number (NUD-88). */
export async function deleteAccountByPhone(
  deps: AppDeps,
  phone: string,
): Promise<DeletionPreview | null> {
  const preview = await deletionPreview(deps, phone);
  if (!preview) return null;
  await deleteAccount(deps, await requirePerson(deps, preview.personId), {
    idempotencyKey: randomUUID(),
  });
  return preview;
}

// ---- settings and connections (5a, 5b, 7g) -------------------------------------------------

/** Persist each outcome and collect what to do after commit. */
async function persistAll(
  tx: Tx,
  deps: AppDeps,
  outcomes: readonly Outcome[],
  now: Date,
): Promise<Effects> {
  const pushes: PushMessage[] = [];
  const deletePhotosFor: TaskId[] = [];
  for (const outcome of outcomes) {
    const o = adjust(deps, outcome, now);
    await persistOutcome(tx, o, now);
    const e = effectsOf(o);
    pushes.push(...e.pushes);
    deletePhotosFor.push(...e.deletePhotosFor);
  }
  return { pushes, deletePhotosFor };
}

/** New plans for tasks still being poked after a setting they depend on changed. */
async function replan(tx: Tx, deps: AppDeps, list: readonly Task[], now: Date): Promise<Effects> {
  const outcomes: Outcome[] = [];
  const seen = new Set<TaskId>();
  for (const task of list) {
    if (seen.has(task.id)) continue;
    seen.add(task.id);
    const r = applyCommand(task, { type: "replan" }, null, await taskContext(tx, task, now));
    if (r.ok) outcomes.push(r.value);
  }
  return persistAll(tx, deps, outcomes, now);
}

/** Quiet hours (7g) and the two human channel switches (5a). Plans that depend on them are redone. */
export async function updateSettings(
  deps: AppDeps,
  caller: Person,
  input: {
    idempotencyKey: string;
    quietHours?: { start: string; end: string };
    useConnectionsForMyTasks?: boolean;
    notifyMeForOthersTasks?: boolean;
  },
): Promise<Person> {
  return runCommand(deps, caller.id, "update_settings", input.idempotencyKey, async (tx, now) => {
    const updated = await updatePersonSettings(tx, caller.id, {
      quietHours: input.quietHours,
      useConnectionsForMyTasks: input.useConnectionsForMyTasks,
      notifyMeForOthersTasks: input.notifyMeForOthersTasks,
    });
    const asRecipient =
      updated.quietHours.start !== caller.quietHours.start ||
      updated.quietHours.end !== caller.quietHours.end ||
      updated.useConnectionsForMyTasks !== caller.useConnectionsForMyTasks;
    const asSender = updated.notifyMeForOthersTasks !== caller.notifyMeForOthersTasks;
    const affected = [
      ...(asRecipient ? await pokedTasks(tx, { recipientId: caller.id }) : []),
      ...(asSender ? await pokedTasks(tx, { senderId: caller.id }) : []),
    ].filter((t) => t.senderId !== t.recipientId || asRecipient);
    return { result: updated, effects: await replan(tx, deps, affected, now) };
  });
}

/** My relationship to the person and my switch for their tasks (5b). */
export async function updateConnection(
  deps: AppDeps,
  caller: Person,
  input: {
    idempotencyKey: string;
    personId: PersonId;
    relationship?: EdgeType;
    notifyMe?: boolean;
  },
): Promise<{ ok: true }> {
  return runCommand(deps, caller.id, "update_connection", input.idempotencyKey, async (tx, now) => {
    const mine = await findEdge(tx, caller.id, input.personId);
    const r = changeConnection(mine, input);
    if (!r.ok) throw new ApiError(r.error);
    await upsertEdges(tx, [r.value], now);
    const effects =
      r.value.notifyMe === mine?.notifyMe
        ? noEffects
        : await replan(
            tx,
            deps,
            await pokedTasks(tx, { senderId: caller.id, recipientId: input.personId }),
            now,
          );
    return { result: { ok: true as const }, effects };
  });
}

/** "Bağlantıyı kaldır" (5b): both edges go, tasks between the two are cancelled, nobody is told. */
export async function removeConnection(
  deps: AppDeps,
  caller: Person,
  input: { idempotencyKey: string; personId: PersonId },
): Promise<{ ok: true }> {
  return runCommand(deps, caller.id, "remove_connection", input.idempotencyKey, async (tx, now) => {
    const edges = [
      await findEdge(tx, caller.id, input.personId),
      await findEdge(tx, input.personId, caller.id),
    ].filter((e) => e !== null);
    const contexts = [];
    for (const task of await unclosedTasksBetween(tx, caller.id, input.personId)) {
      contexts.push({ task, ctx: await taskContext(tx, task, now) });
    }
    const r = removeConnectionDomain(caller, edges, contexts);
    if (!r.ok) throw new ApiError(r.error);
    await upsertEdges(tx, r.value.edges, now);
    return {
      result: { ok: true as const },
      effects: await persistAll(tx, deps, r.value.cancelled, now),
    };
  });
}

// ---- devices --------------------------------------------------------------------------------

/** Sent on every launch and whenever a permission or channel changes (docs/domain.md). */
export async function registerDevice(
  deps: AppDeps,
  caller: Person,
  input: {
    deviceId: string | null;
    fid: string;
    platform: "android" | "ios";
    appVersion: string;
    permissions: PermissionSummary;
  },
): Promise<{ deviceId: string; reachability: Reachability }> {
  const now = deps.clock.now();
  return inWriteTx(deps.db, async (tx) => {
    const deviceId = await upsertDevice(tx, {
      ...input,
      id: input.deviceId,
      personId: caller.id,
      now,
    });
    // Connections see the new reachability.
    await touchEdgesTo(tx, caller.id);
    return { deviceId, reachability: await reachabilityOf(tx, caller.id) };
  });
}

// ---- sync -----------------------------------------------------------------------------------

export async function sync(
  deps: AppDeps,
  caller: Person,
  query: { cursor: number; deviceId: string | null },
) {
  const now = deps.clock.now();
  if (query.deviceId !== null) await touchDevice(deps.db, caller.id, query.deviceId, now);
  const rows = await inSyncReadTx(deps.db, (tx) => syncRows(tx, caller, query.cursor));
  const recipientOf = new Map(rows.tasks.map((t) => [t.id, t.recipientId]));
  return {
    cursor: String(rows.cursor),
    me: caller,
    connections: rows.connections.map((c) => ({
      personId: c.person.id,
      displayName: c.person.displayName,
      relationship: c.edge.type,
      status: c.edge.status,
      reachability: c.person.deletedAt === null ? deriveReachability(c.devices) : ("NONE" as const),
      // Only while connected: a removed connection or a deleted account keeps no way to reach the person.
      phone: c.edge.status === "active" && c.person.deletedAt === null ? c.person.phone : undefined,
      connectedAt: c.connectedAt,
      notifyMe: c.edge.notifyMe,
      avatarPath: c.person.avatarPath,
    })),
    sentInvites: rows.sentInvites.map((i) => ({ ...i, status: inviteStatusForInviter(i) })),
    receivedInvites: rows.receivedInvites,
    tasks: rows.tasks.map((t) => ({
      ...t,
      rawText: t.senderId === caller.id ? t.rawText : null,
      inRecipientList: visibleToRecipient(t, MISSED_CARD_TTL_MS, now),
    })),
    // The plan is hidden (docs/domain.md, Poke plan): only the recipient sees plan events.
    events: rows.events.filter(
      (e) => e.type !== "plan_generated" || recipientOf.get(e.taskId) === caller.id,
    ),
    plans: rows.plans.map((p) => ({
      ...p,
      steps: p.steps.flatMap((s) =>
        s.level === "HUMAN" ? [] : [{ seq: s.seq, plannedAt: s.plannedAt, level: s.level }],
      ),
    })),
    announcements: rows.announcements.map((a) => ({
      id: a.id,
      title: a.title,
      summary: a.summary,
      body: a.body,
      button: a.button,
      sentAt: a.sentAt,
    })),
  };
}

// ---- announcements (ADR 0025) -----------------------------------------------------------------

export interface AnnounceResult {
  /** Problems with the file; nothing is stored or sent while there are any. */
  problems: string[];
  /** Numbers given with --to that belong to no live account. */
  unknownPhones: string[];
  /** People who get it now (and a notification); people who already had it are not counted. */
  newRecipients: number;
  /** People who already had it; they get the new text without a notification. */
  alreadySent: number;
  /** Whether the text differs from what was sent before under this slug. */
  textChanged: boolean;
  sent: boolean;
}

/** `mise run announce`: stores an announcement and tells the new recipients to sync. */
export async function announce(
  deps: AppDeps,
  content: AnnouncementContent,
  to: readonly string[] | "*",
  confirm: boolean,
): Promise<AnnounceResult> {
  const problems = announcementProblems(content);
  const people_ = await livePeopleByPhone(deps.db, to);
  const known = new Set(people_.map((p) => p.phone));
  const unknownPhones = to === "*" ? [] : to.filter((p) => !known.has(p));
  const existing = await findAnnouncement(deps.db, content.slug);
  const textChanged =
    existing !== null && JSON.stringify(existing.content) !== JSON.stringify(content);
  const fresh = people_.filter((p) => !(existing?.recipients.has(p.id) ?? false));
  const preview = {
    problems,
    unknownPhones,
    newRecipients: fresh.length,
    alreadySent: existing?.recipients.size ?? 0,
    textChanged,
    sent: false,
  };
  if (!confirm || problems.length > 0) return preview;
  const now = deps.clock.now();
  const { added } = await inWriteTx(deps.db, (tx) =>
    saveAnnouncement(
      tx,
      content,
      people_.map((p) => p.id),
      now,
    ),
  );
  await afterCommit(deps, { pushes: announcementPushes(added), deletePhotosFor: [] });
  return { ...preview, newRecipients: added.length, sent: true };
}

// ---- server timers --------------------------------------------------------------------------

const CLAIM_LEASE_MS = 60_000;
const CLAIM_LIMIT = 200;

/** TickPort over Postgres (ADR 0007). */
class PgTickPort implements TickPort {
  constructor(private readonly deps: AppDeps) {}

  claimDue(now: Date): Promise<ClaimedAction[]> {
    return claimDueActions(this.deps.db, now, CLAIM_LEASE_MS, CLAIM_LIMIT);
  }

  async loadContext(action: ClaimedAction, now: Date): Promise<ActionContext | null> {
    if (action.taskId === null) return null;
    const { db } = this.deps;
    const task = await findTask(db, action.taskId);
    if (!task) return null;
    const sender = await findPerson(db, task.senderId);
    const recipient = await findPerson(db, task.recipientId);
    if (!sender || !recipient) return null;
    return {
      sender,
      recipient,
      history: await eventsOf(db, task.id),
      now,
      task,
      plan: await currentPlan(db, task),
      recipientDevices: await devicesOf(db, task.recipientId),
    };
  }

  private cleanUpPhotos(action: ClaimedAction, taskId: TaskId, now: Date): Promise<void> {
    return cleanUpPhotosOnce(this.deps, action, taskId, now);
  }

  async complete(action: ClaimedAction, result: ActionResult): Promise<void> {
    const now = this.deps.clock.now();
    if (result.kind === "delete_photos") {
      await this.cleanUpPhotos(action, result.taskId, now);
      return;
    }
    if (result.kind === "purge_account") {
      await purgeAccountOnce(this.deps, action, result.personId, now);
      return;
    }
    const effects = await inWriteTx(this.deps.db, async (tx): Promise<Effects> => {
      // A task transition since the claim already finished this timer.
      if (!(await lockOpenAction(tx, action.id))) return noEffects;
      let effects = noEffects;
      switch (result.kind) {
        case "transition": {
          const current = await findTask(tx, result.outcome.task.id, true);
          if (current?.planVersion === result.outcome.task.planVersion - 1) {
            const o = adjust(this.deps, result.outcome, now);
            await persistOutcome(tx, o, now);
            // A missed or auto-approved occurrence of a series brings the next one (ADR 0021).
            effects = mergeEffects(
              effectsOf(o),
              await spawnNextOccurrence(tx, this.deps, current, o, now),
            );
          }
          break;
        }
        case "notify":
          await insertEvents(tx, result.events);
          // New events or a changed list visibility (missed card expiry) for sync.
          if (action.taskId !== null) await touchTask(tx, action.taskId);
          effects = { pushes: result.pushes, deletePhotosFor: [] };
          break;
        case "noop":
          break;
      }
      await markActionDone(tx, action.id, now);
      return effects;
    });
    await afterCommit(this.deps, effects);
  }
}

/** Done once Storage deleted them; otherwise the same timer runs again later (NUD-25). */
async function cleanUpPhotosOnce(
  deps: AppDeps,
  action: ClaimedAction,
  taskId: TaskId,
  now: Date,
): Promise<void> {
  try {
    await deps.photos.deleteForTask(taskId);
  } catch (error) {
    console.error("photo deletion failed again", taskId, error);
    await rescheduleAction(deps.db, action.id, new Date(now.getTime() + PHOTO_CLEANUP_RETRY_MS));
    return;
  }
  await inWriteTx(deps.db, async (tx) => {
    if (await lockOpenAction(tx, action.id)) await markActionDone(tx, action.id, now);
  });
}

/**
 * The rest of a deleted account (NUD-89). The auth user goes first, again in case the delete
 * right after the command failed; while Supabase does not answer the rows stay and the timer runs
 * again later.
 */
async function purgeAccountOnce(
  deps: AppDeps,
  action: ClaimedAction,
  personId: PersonId,
  now: Date,
): Promise<void> {
  try {
    await deps.auth.deleteUser(personId);
  } catch (error) {
    console.error("auth user deletion failed again", personId, error);
    await rescheduleAction(deps.db, action.id, new Date(now.getTime() + PHOTO_CLEANUP_RETRY_MS));
    return;
  }
  await inWriteTx(deps.db, async (tx) => {
    if (!(await lockOpenAction(tx, action.id))) return;
    await purgePerson(tx, personId);
    await markActionDone(tx, action.id, now);
  });
}

/** Run every due server timer once. */
export function runTick(deps: AppDeps): Promise<number> {
  return tickDomain(deps.clock.now(), new PgTickPort(deps));
}

// ---- sign-in code attempts (screen 6c, ADR 0011) ---------------------------------------------

const otpLocked = (until: Date) =>
  new ApiError("otp_locked", "too many wrong codes", { retryAt: until.toISOString() });

/** No code is sent or checked during the wait after the last wrong code. */
export async function refuseWhileOtpLocked(deps: AppDeps, phone: string): Promise<void> {
  const until = lockedUntil(await findOtpAttempts(deps.db, phone), deps.clock.now());
  if (until) throw otpLocked(until);
}

/** When the next code to the phone may go out, over the cap per phone or in total (ADR 0024). */
async function smsCapReachedUntil(deps: AppDeps, phone: string): Promise<Date | null> {
  const now = deps.clock.now();
  const since = capWindowStart(now);
  const perPhone = await smsSendWindow(deps.db, phone, since);
  const total = await smsSendWindow(deps.db, null, since);
  return laterOf(
    capReachedUntil(perPhone, SMS_PHONE_DAILY_CAP, now),
    capReachedUntil(total, SMS_TOTAL_DAILY_CAP, now),
  );
}

/** No code is asked for over the daily SMS caps; the hook checks them again (ADR 0024). */
export async function refuseOverSmsCaps(deps: AppDeps, phone: string): Promise<void> {
  const until = await smsCapReachedUntil(deps, phone);
  if (until) {
    throw new ApiError("otp_limit", "too many codes in the last 24 hours", {
      retryAt: until.toISOString(),
    });
  }
}

/**
 * The Send SMS hook's code to the provider, unless a daily cap is reached (ADR 0024). Every place
 * a sign-in starts ends here, so the caps hold even for a request that skipped our API. Returns
 * whether it was sent.
 */
export async function sendSignInCode(deps: AppDeps, phone: string, code: string): Promise<boolean> {
  if (await smsCapReachedUntil(deps, phone)) return false;
  await deps.otp.send(phone, code);
  const now = deps.clock.now();
  await insertSmsSend(deps.db, randomUUID(), phone, now, capWindowStart(now));
  return true;
}

/** The session for a right code; a wrong one counts, and the last one starts the wait. */
export async function verifyOtp(
  deps: AppDeps,
  phone: string,
  code: string,
): Promise<NonNullable<Awaited<ReturnType<AppDeps["auth"]["verifyOtp"]>>>> {
  await refuseWhileOtpLocked(deps, phone);
  const session = await deps.auth.verifyOtp(phone, code);
  if (session) {
    await clearOtpAttempts(deps.db, phone);
    return session;
  }
  const now = deps.clock.now();
  const wrong = afterWrongCode(await findOtpAttempts(deps.db, phone), now);
  await saveOtpAttempts(deps.db, phone, wrong.state, now);
  const until = lockedUntil(wrong.state, now);
  if (until) throw otpLocked(until);
  throw new ApiError("invalid_otp", "wrong or expired code", {
    remainingAttempts: wrong.remaining,
  });
}

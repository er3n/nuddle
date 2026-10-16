// Recording fakes for the external service interfaces (docs/testing.md, Ground rules 2).
// Assertions read the records.
import { randomUUID } from "node:crypto";
import type { PlannerInput, PlanProposal } from "../../src/domain/planner";
import type { ProfileInput } from "../../src/domain/profile";
import type {
  Clock,
  OtpSender,
  PhotoStorage,
  ExchangeSink,
  PersonProfiler,
  PokePlanner,
  PushSender,
} from "../../src/domain/ports";
import type { PersonId, PushMessage, TaskId } from "../../src/domain/types";
import type { AuthGateway, Session } from "../../src/infra/auth/supabase-auth";

export class FakeClock implements Clock {
  private current: Date;

  constructor(start: Date | string) {
    this.current = new Date(start);
  }

  now(): Date {
    return new Date(this.current);
  }

  set(t: Date): void {
    if (t.getTime() < this.current.getTime()) throw new Error("FakeClock cannot go backwards");
    this.current = new Date(t);
  }

  advance(ms: number): void {
    this.set(new Date(this.current.getTime() + ms));
  }
}

export interface RecordedPush extends PushMessage {
  at: Date;
}

export class RecordingPushSender implements PushSender {
  readonly sent: RecordedPush[] = [];

  constructor(private readonly clock: Clock) {}

  send(message: PushMessage): Promise<void> {
    this.sent.push({ ...message, at: this.clock.now() });
    return Promise.resolve();
  }

  to(personId: string): RecordedPush[] {
    return this.sent.filter((p) => p.to === personId);
  }
}

export class RecordingOtpSender implements OtpSender {
  readonly sent: { phone: string; code: string }[] = [];

  send(phone: string, code: string): Promise<void> {
    this.sent.push({ phone, code });
    return Promise.resolve();
  }
}

export class FakePhotoStorage implements PhotoStorage {
  readonly stored = new Map<TaskId, string[]>();
  readonly deleted: TaskId[] = [];
  /** Profile pictures handed out per person, and the single files deleted (NUD-72). */
  readonly avatars = new Map<PersonId, string[]>();
  readonly deletedFiles: string[] = [];
  /** A Storage outage: deletes fail until it is set back. */
  failing = false;

  createAvatarUploadUrl(personId: PersonId): Promise<{ path: string; url: string }> {
    const paths = this.avatars.get(personId) ?? [];
    const path = `avatars/${personId}/${String(paths.length + 1)}.jpg`;
    this.avatars.set(personId, [...paths, path]);
    return Promise.resolve({ path, url: `https://storage.test/upload/${path}` });
  }

  deleteFile(path: string): Promise<void> {
    if (this.failing) return Promise.reject(new Error("storage unavailable"));
    this.deletedFiles.push(path);
    return Promise.resolve();
  }

  createUploadUrl(taskId: TaskId): Promise<{ path: string; url: string }> {
    const paths = this.stored.get(taskId) ?? [];
    const path = `tasks/${taskId}/${String(paths.length + 1)}.jpg`;
    this.stored.set(taskId, [...paths, path]);
    return Promise.resolve({ path, url: `https://storage.test/upload/${path}` });
  }

  createViewUrl(path: string): Promise<string> {
    return Promise.resolve(`https://storage.test/view/${path}`);
  }

  deleteForTask(taskId: TaskId): Promise<void> {
    if (this.failing) return Promise.reject(new Error("storage unavailable"));
    this.stored.delete(taskId);
    this.deleted.push(taskId);
    return Promise.resolve();
  }
}

/** Supabase Auth for HTTP tests: code "123456" signs in, anything else is wrong. */
export class FakeAuthGateway implements AuthGateway {
  readonly otpSentTo: string[] = [];
  readonly deletedUsers: string[] = [];
  /** Makes the next `deleteUser` throw, as a Supabase outage would. */
  failNextDelete = false;
  private readonly users = new Map<string, string>();

  constructor(private readonly mint: (userId: string, phone: string) => Promise<string>) {}

  sendOtp(phone: string): Promise<void> {
    this.otpSentTo.push(phone);
    return Promise.resolve();
  }

  async verifyOtp(phone: string, code: string): Promise<Session | null> {
    if (code !== "123456") return null;
    const userId = await this.ensureUser(phone);
    return {
      accessToken: await this.mint(userId, phone),
      refreshToken: `refresh-${userId}`,
      expiresAt: new Date("2030-01-01T00:00:00Z"),
      userId,
    };
  }

  async refresh(refreshToken: string): Promise<Session | null> {
    const entry = [...this.users].find(([, id]) => `refresh-${id}` === refreshToken);
    return entry ? this.verifyOtp(entry[0], "123456") : null;
  }

  ensureUser(phone: string): Promise<string> {
    const id = this.users.get(phone) ?? randomUUID();
    this.users.set(phone, id);
    return Promise.resolve(id);
  }

  deleteUser(userId: string): Promise<void> {
    if (this.failNextDelete) {
      this.failNextDelete = false;
      return Promise.reject(new Error("auth unavailable"));
    }
    for (const [phone, id] of this.users) if (id === userId) this.users.delete(phone);
    this.deletedUsers.push(userId);
    return Promise.resolve();
  }
}

/** A poke planner that answers with whatever the test gives it and records what it was asked. */
/** A profiler that answers from a function and records what it was asked (ADR 0020). */
export class ScriptedProfiler implements PersonProfiler {
  readonly calls: ProfileInput[] = [];

  constructor(private readonly answer: (input: ProfileInput) => string | Promise<string>) {}

  async profile(input: ProfileInput, onExchange?: ExchangeSink): Promise<string> {
    this.calls.push(input);
    const text = await this.answer(input);
    onExchange?.({ model: "scripted", system: "", prompt: "", output: { profile: text } });
    return text;
  }
}

export class ScriptedPlanner implements PokePlanner {
  readonly calls: PlannerInput[] = [];

  constructor(
    private readonly answer: (input: PlannerInput) => PlanProposal | Promise<PlanProposal>,
  ) {}

  async plan(input: PlannerInput, onExchange?: ExchangeSink): Promise<PlanProposal> {
    this.calls.push(input);
    const proposal = await this.answer(input);
    onExchange?.({ model: "scripted", system: "", prompt: "", output: proposal });
    return proposal;
  }
}

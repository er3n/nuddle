// Recording fakes for the external service interfaces (docs/testing.md, Ground rules 2).
// Assertions read the records.
import { randomUUID } from "node:crypto";
import type { PlannerInput, PlanProposal } from "../../src/domain/planner";
import type { ProfileInput } from "../../src/domain/profile";
import type { OtpLanguage } from "../../src/domain/otp-channel";
import type {
  Clock,
  Delay,
  OtpSender,
  SentMessage,
  PhotoStorage,
  ExchangeSink,
  PersonProfiler,
  PokePlanner,
  PushSender,
} from "../../src/domain/ports";
import type { PersonId, PushMessage, TaskId } from "../../src/domain/types";
import type { AuthGateway, Session } from "../../src/infra/auth/supabase-auth";
import { decodeJwt } from "jose";

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

/** Twilio for sign-in codes: records what went where and hands out message SIDs (ADR 0029). */
export class RecordingOtpSender implements OtpSender {
  /** SMS sent, oldest first. */
  readonly sent: { phone: string; code: string }[] = [];
  /** The SID of each SMS in `sent`, in the same order. */
  readonly smsSids: string[] = [];
  /** WhatsApp templates sent, oldest first. */
  readonly whatsAppSent: { phone: string; code: string; language: OtpLanguage; sid: string }[] = [];
  readonly whatsApp: boolean;
  /** What `priceOf` answers per SID; absent is not priced yet. */
  readonly prices = new Map<string, number>();
  /** Twilio refuses the next WhatsApp request itself (not a delivery failure). */
  failNextWhatsApp = false;
  private next = 0;
  private held: Promise<void> | null = null;
  /** SMS requests waiting on `holdNextSms`. */
  inFlight = 0;

  /** The next SMS request stays in flight until the returned function is called. */
  holdNextSms(): () => void {
    let release: (() => void) | undefined;
    this.held = new Promise<void>((resolve) => {
      release = resolve;
    });
    return () => {
      release?.();
    };
  }

  /** WhatsApp is off unless asked for, as before the sender was approved. */
  constructor(options: { whatsApp?: boolean } = {}) {
    this.whatsApp = options.whatsApp ?? false;
  }

  sendWhatsApp(phone: string, code: string, language: OtpLanguage): Promise<SentMessage> {
    if (this.failNextWhatsApp) {
      this.failNextWhatsApp = false;
      return Promise.reject(new Error("twilio: 400 bad request"));
    }
    const sid = this.sid("MM");
    this.whatsAppSent.push({ phone, code, language, sid });
    return Promise.resolve({ sid });
  }

  async sendSms(phone: string, code: string): Promise<SentMessage> {
    const held = this.held;
    this.held = null;
    if (held) {
      this.inFlight++;
      await held;
      this.inFlight--;
    }
    const sid = this.sid("SM");
    this.sent.push({ phone, code });
    this.smsSids.push(sid);
    return { sid };
  }

  priceOf(sid: string): Promise<number | null> {
    return Promise.resolve(this.prices.get(sid) ?? null);
  }

  private sid(prefix: string): string {
    this.next++;
    return `${prefix}${String(this.next).padStart(32, "0")}`;
  }
}

/** A wait that ends only when the test says so (ADR 0029, the 12 s fallback). */
export class FakeDelay implements Delay {
  /** Every wait asked for, in ms. */
  readonly waits: number[] = [];
  private pending: (() => void)[] = [];

  wait(ms: number): Promise<void> {
    this.waits.push(ms);
    return new Promise((resolve) => {
      this.pending.push(resolve);
    });
  }

  /** Ends every wait asked for so far. */
  elapse(): void {
    const ending = this.pending;
    this.pending = [];
    for (const resolve of ending) resolve();
  }
}

/** Vercel's `waitUntil` for tests: work after the response is collected and awaited on demand. */
export class PendingWork {
  private readonly work: Promise<unknown>[] = [];

  readonly waitUntil = (work: Promise<unknown>): void => {
    this.work.push(work);
  };

  /** Awaits everything handed over so far, and whatever that hands over in turn. */
  async settle(): Promise<void> {
    for (let work = this.work.shift(); work !== undefined; work = this.work.shift()) await work;
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
  /** Access tokens `signOutOthers` was called with (ADR 0027). */
  readonly signedOutOthersOf: string[] = [];
  /** Makes the next `deleteUser` throw, as a Supabase outage would. */
  failNextDelete = false;
  /** Makes the next `signOutOthers` throw, as a Supabase outage would. */
  failNextSignOut = false;
  /** What Supabase Auth does for a real number before it answers: call the Send SMS hook. */
  onSendOtp: ((phone: string) => Promise<void>) | null = null;
  private readonly users = new Map<string, string>();
  /** Refresh token to its phone and session: a refresh keeps the session id, as in Supabase. */
  private readonly sessions = new Map<string, { phone: string; sessionId: string }>();

  constructor(
    private readonly mint: (userId: string, phone: string, sessionId: string) => Promise<string>,
  ) {}

  async sendOtp(phone: string): Promise<void> {
    this.otpSentTo.push(phone);
    await this.onSendOtp?.(phone);
  }

  async verifyOtp(phone: string, code: string): Promise<Session | null> {
    if (code !== "123456") return null;
    return this.session(phone, randomUUID());
  }

  async refresh(refreshToken: string): Promise<Session | null> {
    const entry = this.sessions.get(refreshToken);
    if (!entry) return null;
    this.sessions.delete(refreshToken);
    return this.session(entry.phone, entry.sessionId);
  }

  private async session(phone: string, sessionId: string): Promise<Session> {
    const userId = await this.ensureUser(phone);
    const refreshToken = `refresh-${randomUUID()}`;
    this.sessions.set(refreshToken, { phone, sessionId });
    return {
      accessToken: await this.mint(userId, phone, sessionId),
      refreshToken,
      expiresAt: new Date("2030-01-01T00:00:00Z"),
      userId,
    };
  }

  /** Ends every other session of the token's user: their refresh tokens stop working. */
  signOutOthers(accessToken: string): Promise<void> {
    if (this.failNextSignOut) {
      this.failNextSignOut = false;
      return Promise.reject(new Error("auth unavailable"));
    }
    this.signedOutOthersOf.push(accessToken);
    const { sub, session_id: keep } = decodeJwt(accessToken);
    const phone = [...this.users].find(([, id]) => id === sub)?.[0];
    for (const [token, s] of this.sessions) {
      if (s.phone === phone && s.sessionId !== keep) this.sessions.delete(token);
    }
    return Promise.resolve();
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

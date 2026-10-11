// Recording fakes for the external service interfaces (docs/testing.md, Ground rules 2).
// Assertions read the records.
import { randomUUID } from "node:crypto";
import type { Clock, OtpSender, PhotoStorage, PushSender } from "../../src/domain/ports";
import type { PushMessage, TaskId } from "../../src/domain/types";
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

  createUploadUrl(taskId: TaskId): Promise<{ path: string; url: string }> {
    const paths = this.stored.get(taskId) ?? [];
    const path = `tasks/${taskId}/${String(paths.length + 1)}.jpg`;
    this.stored.set(taskId, [...paths, path]);
    return Promise.resolve({ path, url: `https://storage.test/upload/${path}` });
  }

  deleteForTask(taskId: TaskId): Promise<void> {
    this.stored.delete(taskId);
    this.deleted.push(taskId);
    return Promise.resolve();
  }
}

/** Supabase Auth for HTTP tests: code "123456" signs in, anything else is wrong. */
export class FakeAuthGateway implements AuthGateway {
  readonly otpSentTo: string[] = [];
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
}

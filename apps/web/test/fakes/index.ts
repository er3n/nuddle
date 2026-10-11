// Recording fakes for the external service interfaces (docs/testing.md, Ground rules 2).
// Assertions read the records.
import type { Clock, OtpSender, PhotoStorage, PushSender } from "../../src/domain/ports";
import type { PushMessage, TaskId } from "../../src/domain/types";

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

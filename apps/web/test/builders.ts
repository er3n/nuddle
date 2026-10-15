// Test data builders with the Sibel and Eren cast (docs/testing.md, Ground rules 5; glossary.md).
import type { PermissionSummary } from "../src/domain/reachability";
import type { Person, Task } from "../src/domain/types";

const phones: Record<string, string> = {
  Sibel: "+905320000001",
  Eren: "+905320000002",
  Defne: "+905320000003",
  Can: "+905320000004",
};

export function aPerson(name: string, overrides: Partial<Person> = {}): Person {
  return {
    id: name.toLowerCase(),
    phone: phones[name] ?? `+90532${String(name.length).padStart(7, "9")}`,
    displayName: name,
    quietHours: { start: "23:00", end: "07:00", timeZone: "Europe/Istanbul" },
    useConnectionsForMyTasks: true,
    notifyMeForOthersTasks: true,
    avatarPath: null,
    pokeProfile: null,
    deletedAt: null,
    ...overrides,
  };
}

export const allPermissions: PermissionSummary = {
  notifications: true,
  exactAlarm: true,
  fullScreen: true,
  channels: { poke_silent: true, poke_alarm: true },
};

class TaskBuilder {
  private task: Task = {
    id: "task-1",
    senderId: "sibel",
    recipientId: "eren",
    title: "Temizlikçi için para çek",
    detail: "1.000 TL",
    deadline: new Date("2026-10-12T09:00:00+03:00"),
    timing: "by",
    remindAt: null,
    leadMinutes: 0,
    finishMode: "photo",
    rawText: "eren yarın 9dan önce temizlikçi için 1000 çeksin",
    status: "SCHEDULED",
    late: false,
    planVersion: 1,
    statusChangedAt: new Date("2026-10-11T20:00:00+03:00"),
    removedByRecipientAt: null,
  };

  from(sender: Person): this {
    this.task.senderId = sender.id;
    return this;
  }

  forRecipient(recipient: Person): this {
    this.task.recipientId = recipient.id;
    return this;
  }

  withDeadline(deadline: Date | string): this {
    this.task.deadline = new Date(deadline);
    return this;
  }

  /** An `at` task (ADR 0017): the moment, and the deadline that many minutes after it. */
  at(moment: Date | string, missAfterMinutes = 60): this {
    this.task.timing = "at";
    this.task.remindAt = new Date(moment);
    this.task.deadline = new Date(new Date(moment).getTime() + missAfterMinutes * 60_000);
    return this;
  }

  withFinishMode(mode: Task["finishMode"]): this {
    this.task.finishMode = mode;
    return this;
  }

  withStatus(status: Task["status"]): this {
    this.task.status = status;
    return this;
  }

  build(): Task {
    return { ...this.task };
  }
}

export function aTask(): TaskBuilder {
  return new TaskBuilder();
}

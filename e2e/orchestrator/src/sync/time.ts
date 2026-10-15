// Fake time for the E2E sets (docs/testing.md, Fake time; NUD-65, NUD-66): the server's clock and both phones' clocks
// move forward together, then the server's timers run. A case says "to the next poke" or "past the deadline" instead
// of waiting for it.
import postgres from "postgres";
import { testApi } from "../api";
import type { Device } from "../devices";

type Level = "SILENT" | "SOUND" | "FULL_SCREEN";

export class FakeTime {
  private readonly db: postgres.Sql;

  constructor(
    databaseUrl: string,
    private readonly phones: Device[],
  ) {
    this.db = postgres(databaseUrl, { max: 1, onnotice: () => undefined });
  }

  async close(): Promise<void> {
    await this.db.end();
  }

  /** The server's now. */
  async now(): Promise<Date> {
    return new Date((await testApi.advanceClock(0)).now);
  }

  /** [stopped] is a phone whose app is force stopped: its clock moves without starting the app. */
  async advance(ms: number, stopped?: Device): Promise<void> {
    await testApi.advanceClock(ms);
    for (const phone of this.phones) {
      await (phone === stopped ? phone.advanceStopped(ms) : phone.advance(ms));
    }
    await testApi.tick();
  }

  /** To [ms] after the last step of the task's current plan. */
  async pastLastStep(taskId: string, ms: number, stopped?: Device): Promise<void> {
    const now = await this.now();
    const [last] = await this.db<{ plannedAt: Date }[]>`
      select max(s.planned_at) as "plannedAt" from poke_steps s
      join tasks t on t.id = s.task_id and s.plan_version = t.plan_version
      where t.id = ${taskId} and s.level <> 'HUMAN'`;
    if (!last?.plannedAt) throw new Error(`no steps for task ${taskId}`);
    await this.advance(Math.max(0, last.plannedAt.getTime() - now.getTime()) + ms, stopped);
  }

  /**
   * To just after the next step of the task's current plan (of [level] when given): the recipient's phone rings it at
   * once. Throws when there is none.
   */
  async toStep(taskId: string, level?: Level): Promise<void> {
    const now = await this.now();
    const step = await this.nextStep(taskId, now, level);
    await this.advance(step.getTime() - now.getTime() + 1000);
  }

  private async nextStep(taskId: string, now: Date, level?: Level): Promise<Date> {
    const [step] = await this.db<{ plannedAt: Date }[]>`
      select s.planned_at as "plannedAt" from poke_steps s
      join tasks t on t.id = s.task_id and s.plan_version = t.plan_version
      where t.id = ${taskId} and s.level <> 'HUMAN' and s.planned_at > ${now}
        and (${level ?? null}::text is null or s.level::text = ${level ?? null})
      order by s.planned_at limit 1`;
    if (!step) throw new Error(`no ${level ?? ""} step ahead for task ${taskId}`);
    return step.plannedAt;
  }

  /**
   * To [msBefore] before the next step of the task's current plan, which then comes due in real time.
   */
  async toJustBefore(taskId: string, msBefore: number): Promise<void> {
    const now = await this.now();
    const step = await this.nextStep(taskId, now);
    await this.advance(Math.max(0, step.getTime() - now.getTime() - msBefore));
  }

  /** To [ms] after the task's deadline; the deadline timer runs. */
  async pastDeadline(taskId: string, ms = 60_000): Promise<void> {
    const now = await this.now();
    const [task] = await this.db<
      { deadline: Date }[]
    >`select deadline from tasks where id = ${taskId}`;
    if (!task) throw new Error(`no task ${taskId}`);
    await this.advance(Math.max(0, task.deadline.getTime() - now.getTime()) + ms);
  }
}

// The only place on the server that reads the wall clock (ADR 0009). Everything else receives a
// `Clock` or `now`.
import type { Clock } from "../domain/ports";

export const systemClock: Clock = {
  now: () => new Date(),
};

/** E2E mode (ADR 0009): the real clock shifted by `/api/test/advance-clock`. */
export class OffsetClock implements Clock {
  private offsetMs = 0;

  constructor(private readonly base: Clock) {}

  now(): Date {
    return new Date(this.base.now().getTime() + this.offsetMs);
  }

  advance(ms: number): void {
    this.offsetMs += ms;
  }

  reset(): void {
    this.offsetMs = 0;
  }
}

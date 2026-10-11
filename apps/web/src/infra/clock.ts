// The only place on the server that reads the wall clock (ADR 0009). Everything else receives a
// `Clock` or `now`.
import type { Clock } from "../domain/ports";

export const systemClock: Clock = {
  now: () => new Date(),
};

// API error codes (docs/architecture.md, Error codes). Every error response carries a stable
// `DRT-xxxx` code that the app shows on the unrecoverable error screen (6f) and sends to
// Crashlytics. 1xxx: request and auth, 4xxx: domain rules, 5xxx: server and upstream.
import type { ContentfulStatusCode } from "hono/utils/http-status";
import type { ConnectionError } from "../domain/connections";
import type { DomainError } from "../domain/task-machine";

const errors = {
  validation_failed: { code: "DRT-1001", status: 400 },
  unauthorized: { code: "DRT-1002", status: 401 },
  not_registered: { code: "DRT-1003", status: 403 },
  not_found: { code: "DRT-1004", status: 404 },
  idempotency_key_reused: { code: "DRT-1005", status: 422 },
  invalid_otp: { code: "DRT-1006", status: 401 },
  forbidden: { code: "DRT-1007", status: 403 },
  otp_locked: { code: "DRT-1008", status: 429 },
  otp_limit: { code: "DRT-1009", status: 429 },
  write_limit: { code: "DRT-1010", status: 429 },
  otp_resend_unavailable: { code: "DRT-1011", status: 409 },

  wrong_status: { code: "DRT-4001", status: 409 },
  not_sender: { code: "DRT-4002", status: 403 },
  not_recipient: { code: "DRT-4003", status: 403 },
  wrong_finish_mode: { code: "DRT-4004", status: 409 },
  deadline_not_in_future: { code: "DRT-4005", status: 409 },
  not_connected: { code: "DRT-4006", status: 409 },
  recipient_unavailable: { code: "DRT-4007", status: 409 },
  snooze_not_allowed: { code: "DRT-4008", status: 409 },
  start_not_in_window: { code: "DRT-4009", status: 409 },
  invite_not_open: { code: "DRT-4101", status: 409 },
  wrong_invitee: { code: "DRT-4102", status: 403 },
  self_invite: { code: "DRT-4103", status: 409 },
  not_inviter: { code: "DRT-4104", status: 403 },

  internal: { code: "DRT-5001", status: 500 },
  upstream_failed: { code: "DRT-5002", status: 502 },
} as const satisfies Record<ErrorName, { code: `DRT-${string}`; status: ContentfulStatusCode }>;

type RequestError =
  | "validation_failed"
  | "unauthorized"
  | "not_registered"
  | "not_found"
  | "idempotency_key_reused"
  | "invalid_otp"
  | "forbidden"
  | "otp_locked"
  | "otp_limit"
  | "write_limit"
  | "otp_resend_unavailable";

export type ErrorName =
  RequestError | DomainError | ConnectionError | "internal" | "upstream_failed";

export const errorNames = Object.keys(errors) as [ErrorName, ...ErrorName[]];

/** What the app needs to say more than the code: attempts left (6c), when a wait ends. */
export interface ErrorDetails {
  remainingAttempts?: number;
  retryAt?: string;
}

export class ApiError extends Error {
  constructor(
    override readonly name: ErrorName,
    message?: string,
    readonly details?: ErrorDetails,
  ) {
    super(message ?? name);
  }

  get code(): string {
    return errors[this.name].code;
  }

  get status(): ContentfulStatusCode {
    return errors[this.name].status;
  }
}

export function errorBody(
  name: ErrorName,
  message: string,
  requestId: string,
  details?: ErrorDetails,
): {
  error: {
    code: string;
    name: ErrorName;
    message: string;
    requestId: string;
    details?: ErrorDetails;
  };
} {
  return {
    error: { code: errors[name].code, name, message, requestId, ...(details ? { details } : {}) },
  };
}

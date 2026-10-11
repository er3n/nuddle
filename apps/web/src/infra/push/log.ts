// PushSender for local development without Firebase credentials: logs the hint.
import type { PushSender } from "../../domain/ports";
import type { PushMessage } from "../../domain/types";

export const logPushSender: PushSender = {
  send(message: PushMessage): Promise<void> {
    console.info(`push (not sent, no FIREBASE_SERVICE_ACCOUNT): ${JSON.stringify(message)}`);
    return Promise.resolve();
  },
};

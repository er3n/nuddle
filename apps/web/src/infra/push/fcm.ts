// PushSender over FCM data messages with firebase-admin (docs/architecture.md, Push). Messages are
// hints only; the device syncs on any of them.
import { cert, getApps, initializeApp, type ServiceAccount } from "firebase-admin/app";
import { getMessaging, type Messaging } from "firebase-admin/messaging";
import type { PushSender } from "../../domain/ports";
import type { PersonId, PushMessage } from "../../domain/types";

export class FcmPushSender implements PushSender {
  constructor(
    private readonly messaging: Messaging,
    private readonly tokensOf: (personId: PersonId) => Promise<string[]>,
  ) {}

  /** `serviceAccountJson` is the key file content (FIREBASE_SERVICE_ACCOUNT). */
  static fromServiceAccount(
    serviceAccountJson: string,
    tokensOf: (personId: PersonId) => Promise<string[]>,
  ): FcmPushSender {
    const account = JSON.parse(serviceAccountJson) as ServiceAccount;
    const app =
      getApps().find((a) => a.name === "nuddle") ??
      initializeApp({ credential: cert(account) }, "nuddle");
    return new FcmPushSender(getMessaging(app), tokensOf);
  }

  async send(message: PushMessage): Promise<void> {
    const tokens = await this.tokensOf(message.to);
    if (tokens.length === 0) return;
    // eslint-disable-next-line @typescript-eslint/no-deprecated -- docs/domain.md and devices.fcm_token use FCM registration tokens; moving to Firebase Installation IDs is NUD-26.
    const result = await this.messaging.sendEachForMulticast({
      tokens,
      data: { hint: message.hint, taskId: message.taskId ?? "", event: message.event },
      // High priority so a plan reaches a dozing phone (docs/domain.md, Device execution).
      android: { priority: "high" },
    });
    // A dead token stays until the device re-registers or logs out; a failed hint is not fatal
    // because sync is the source of truth.
    if (result.failureCount > 0) {
      console.warn(`fcm: ${String(result.failureCount)} of ${String(tokens.length)} failed`);
    }
  }
}

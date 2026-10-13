// PushSender over FCM data messages with firebase-admin (docs/architecture.md, Push). Messages are
// hints only; the device syncs on any of them.
import { cert, getApps, initializeApp, type ServiceAccount } from "firebase-admin/app";
import { getMessaging, type Messaging } from "firebase-admin/messaging";
import type { PushSender } from "../../domain/ports";
import type { PersonId, PushMessage } from "../../domain/types";

export class FcmPushSender implements PushSender {
  constructor(
    private readonly messaging: Messaging,
    private readonly fidsOf: (personId: PersonId) => Promise<string[]>,
  ) {}

  /** `serviceAccountJson` is the key file content (FIREBASE_SERVICE_ACCOUNT). */
  static fromServiceAccount(
    serviceAccountJson: string,
    fidsOf: (personId: PersonId) => Promise<string[]>,
  ): FcmPushSender {
    const account = JSON.parse(serviceAccountJson) as ServiceAccount;
    const app =
      getApps().find((a) => a.name === "nuddle") ??
      initializeApp({ credential: cert(account) }, "nuddle");
    return new FcmPushSender(getMessaging(app), fidsOf);
  }

  async send(message: PushMessage): Promise<void> {
    // Devices are addressed by Firebase Installation ID (ADR 0015).
    const fids = await this.fidsOf(message.to);
    if (fids.length === 0) return;
    const result = await this.messaging.sendEachForMulticast({
      fids,
      data: { hint: message.hint, taskId: message.taskId ?? "", event: message.event },
      // High priority so a plan reaches a dozing phone (docs/domain.md, Device execution).
      android: { priority: "high" },
    });
    // A dead FID stays until the device re-registers or logs out; a failed hint is not fatal
    // because sync is the source of truth.
    if (result.failureCount > 0) {
      console.warn(`fcm: ${String(result.failureCount)} of ${String(fids.length)} failed`);
    }
  }
}

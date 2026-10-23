// Sign-in code channel decisions (ADR 0029): which channel, the 30 day memory, how statuses move.
import { describe, expect, it } from "vitest";
import {
  chooseChannel,
  codeExpiredAtOrBefore,
  fallbackDueAtOrBefore,
  isFinalStatus,
  languageFor,
  nextStatus,
  otpRetentionStart,
  priceUsd,
} from "../../src/domain/otp-channel";
import { DAY, MINUTE } from "../../src/domain/time";

const now = new Date("2026-10-23T12:00:00Z");
const ago = (ms: number) => new Date(now.getTime() - ms);

describe("chooseChannel", () => {
  it("sends SMS while the WhatsApp sender is not configured", () => {
    expect(chooseChannel(false, null, now)).toBe("sms");
  });

  it("sends WhatsApp to a number with no failed WhatsApp send", () => {
    expect(chooseChannel(true, null, now)).toBe("whatsapp");
  });

  it("sends SMS directly for 30 days after a failed or undelivered WhatsApp send", () => {
    expect(chooseChannel(true, ago(MINUTE), now)).toBe("sms");
    expect(chooseChannel(true, ago(30 * DAY - 1), now)).toBe("sms");
  });

  it("forgets the failure once it is exactly 30 days old", () => {
    expect(chooseChannel(true, ago(30 * DAY), now)).toBe("whatsapp");
    expect(chooseChannel(true, ago(31 * DAY), now)).toBe("whatsapp");
  });
});

describe("languageFor", () => {
  it("is Turkish for +90 and English for any other number", () => {
    expect(languageFor("+905320000002")).toBe("tr");
    expect(languageFor("+4915112345678")).toBe("en");
    expect(languageFor("+9055")).toBe("tr");
    expect(languageFor("+9715012345")).toBe("en");
  });
});

describe("time limits", () => {
  it("the fallback is due 12 s after the send, the code lives 10 minutes, rows 31 days", () => {
    expect(fallbackDueAtOrBefore(now)).toEqual(ago(12_000));
    expect(codeExpiredAtOrBefore(now)).toEqual(ago(10 * MINUTE));
    expect(otpRetentionStart(now)).toEqual(ago(31 * DAY));
  });
});

describe("nextStatus", () => {
  it("takes the first status and moves forward", () => {
    expect(nextStatus(null, "queued")).toBe("queued");
    expect(nextStatus("queued", "sent")).toBe("sent");
    expect(nextStatus("sent", "delivered")).toBe("delivered");
    expect(nextStatus("delivered", "read")).toBe("read");
    expect(nextStatus("sent", "failed")).toBe("failed");
    expect(nextStatus("sent", "undelivered")).toBe("undelivered");
  });

  it("changes nothing for a duplicate callback", () => {
    expect(nextStatus("sent", "sent")).toBeNull();
    expect(nextStatus("delivered", "delivered")).toBeNull();
    expect(nextStatus("failed", "failed")).toBeNull();
  });

  it("never moves back from delivered or read", () => {
    expect(nextStatus("delivered", "sent")).toBeNull();
    expect(nextStatus("delivered", "queued")).toBeNull();
    expect(nextStatus("delivered", "failed")).toBeNull();
    expect(nextStatus("delivered", "undelivered")).toBeNull();
    expect(nextStatus("read", "delivered")).toBeNull();
    expect(nextStatus("read", "failed")).toBeNull();
  });

  it("keeps a final failure against a late sent", () => {
    expect(nextStatus("failed", "sent")).toBeNull();
    expect(nextStatus("undelivered", "queued")).toBeNull();
  });
});

describe("isFinalStatus", () => {
  it("is final once Twilio will price the message", () => {
    for (const s of ["delivered", "read", "failed", "undelivered", "canceled"]) {
      expect(isFinalStatus(s)).toBe(true);
    }
    for (const s of [null, "queued", "accepted", "sending", "sent"]) {
      expect(isFinalStatus(s)).toBe(false);
    }
  });
});

describe("priceUsd", () => {
  it("reads Twilio's negative price string as what it cost", () => {
    expect(priceUsd("-0.0305")).toBe(0.0305);
    expect(priceUsd("0.0050")).toBe(0.005);
  });

  it("is null while Twilio has not priced the message", () => {
    expect(priceUsd(null)).toBeNull();
    expect(priceUsd(undefined)).toBeNull();
    expect(priceUsd("")).toBeNull();
    expect(priceUsd("n/a")).toBeNull();
  });
});

import { describe, expect, it } from "vitest";
import { systemClock } from "../../src/infra/clock";
import { FakeClock, FakePhotoStorage, RecordingOtpSender, RecordingPushSender } from "../fakes";

describe("clocks", () => {
  it("the system clock returns a fresh instant", () => {
    expect(systemClock.now()).toBeInstanceOf(Date);
  });

  it("the fake clock only moves when told and never backwards", () => {
    const clock = new FakeClock("2026-10-11T20:00:00+03:00");
    clock.advance(60_000);
    expect(clock.now()).toEqual(new Date("2026-10-11T20:01:00+03:00"));
    expect(() => {
      clock.set(new Date("2026-10-11T19:00:00+03:00"));
    }).toThrow();
  });
});

describe("recording fakes", () => {
  it("record what was sent", async () => {
    const clock = new FakeClock("2026-10-11T20:00:00+03:00");
    const push = new RecordingPushSender(clock);
    await push.send({ to: "eren", hint: "sync", taskId: null, event: "invite_accepted" });
    expect(push.to("eren")).toHaveLength(1);

    const otp = new RecordingOtpSender();
    await otp.sendSms("+905320000002", "123456");
    expect(otp.sent).toEqual([{ phone: "+905320000002", code: "123456" }]);

    const photos = new FakePhotoStorage();
    const { path } = await photos.createUploadUrl("task-1");
    expect(photos.stored.get("task-1")).toEqual([path]);
    await photos.deleteForTask("task-1");
    expect(photos.stored.has("task-1")).toBe(false);
  });
});

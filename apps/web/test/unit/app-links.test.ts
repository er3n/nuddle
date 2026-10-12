import { describe, expect, it } from "vitest";
import { assetLinks } from "../../src/api/app-links";

describe("assetLinks", () => {
  it("lists the production app with the configured fingerprints", () => {
    expect(assetLinks({ ANDROID_APP_LINK_FINGERPRINTS: " ab:cd , EF:01" })).toEqual([
      {
        relation: ["delegate_permission/common.handle_all_urls"],
        target: {
          namespace: "android_app",
          package_name: "app.nuddle",
          sha256_cert_fingerprints: ["AB:CD", "EF:01"],
        },
      },
    ]);
  });

  it("is empty until a fingerprint is set", () => {
    expect(assetLinks({})).toEqual([]);
    expect(assetLinks({ ANDROID_APP_LINK_FINGERPRINTS: " , " })).toEqual([]);
  });
});

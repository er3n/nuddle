import { describe, expect, it } from "vitest";
import { assetLinks } from "../../src/api/app-links";

describe("assetLinks", () => {
  it("lists both Play apps with the configured fingerprints", () => {
    const statement = (packageName: string) => ({
      relation: ["delegate_permission/common.handle_all_urls"],
      target: {
        namespace: "android_app",
        package_name: packageName,
        sha256_cert_fingerprints: ["AB:CD", "EF:01"],
      },
    });
    expect(assetLinks({ ANDROID_APP_LINK_FINGERPRINTS: " ab:cd , EF:01" })).toEqual([
      statement("com.dokukoza.nuddle"),
      statement("app.nuddle"),
    ]);
  });

  it("is empty until a fingerprint is set", () => {
    expect(assetLinks({})).toEqual([]);
    expect(assetLinks({ ANDROID_APP_LINK_FINGERPRINTS: " , " })).toEqual([]);
  });
});

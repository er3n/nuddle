// Digital Asset Links for invite links (open-questions#13): https://nuddle.app/i/{code} opens the
// app directly once Android verifies this file against the app's signing certificate.

/**
 * The Play builds: com.dokukoza.nuddle in production, app.nuddle in the earlier testing account (ADR 0028).
 * Debug builds (app.nuddle.debug) are signed with a local key and not listed.
 */
const PACKAGES = ["com.dokukoza.nuddle", "app.nuddle"];

/**
 * `ANDROID_APP_LINK_FINGERPRINTS`: comma separated SHA-256 fingerprints of the signing certificates
 * ("AB:CD:..."), set in Vercel by the human (docs/human-checklist.md). Unset means an empty list, so
 * Android does not verify the links and offers the browser instead.
 */
export function assetLinks(env: Record<string, string | undefined>): unknown[] {
  const fingerprints = (env.ANDROID_APP_LINK_FINGERPRINTS ?? "")
    .split(",")
    .map((f) => f.trim().toUpperCase())
    .filter((f) => f !== "");
  if (fingerprints.length === 0) return [];
  return PACKAGES.map((packageName) => ({
    relation: ["delegate_permission/common.handle_all_urls"],
    target: {
      namespace: "android_app",
      package_name: packageName,
      sha256_cert_fingerprints: fingerprints,
    },
  }));
}

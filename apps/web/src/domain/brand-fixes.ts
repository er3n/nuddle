// Brand fixes (docs/ux/platform.md, Missed-ring detection; screen 2h; open-questions #22): the
// settings that stop a phone brand from killing the app, keyed by `Build.MANUFACTURER`. The steps
// live in brand-fixes.json so they change with a deploy, without an app update.
import data from "./brand-fixes.json";

/** Where a step's "Ayarı aç" goes. The device tries the targets in order and opens the first that exists. */
interface BrandFixTarget {
  /** An explicit activity, `package/class`. */
  component?: string;
  /** A settings action, e.g. `android.settings.APPLICATION_DETAILS_SETTINGS`. */
  action?: string;
  /** The action needs `package:<our application id>` as its data. */
  withPackage?: boolean;
}

interface BrandFixStep {
  id: string;
  title: string;
  hint: string;
  /** The device can tell by itself that this step is done; otherwise it counts once opened. */
  check?: "battery_optimization";
  targets: BrandFixTarget[];
}

export interface BrandFix {
  /** The key of the fix used: a brand, or `default` for every other phone. */
  brand: string;
  /** The brand as the user knows it ("Redmi"), null for `default`. */
  name: string | null;
  /** Why this brand loses pokes, with the name filled in; null for `default`. */
  reason: string | null;
  steps: BrandFixStep[];
}

interface FixData {
  reason: string | null;
  steps: BrandFixStep[];
}

const aliases: Record<string, { fix: string; name: string } | undefined> = data.aliases;
const fixes = data.fixes as Record<string, FixData | undefined>;

const DEFAULT_BRAND = "default";

/** The fix for a phone's `Build.MANUFACTURER` ("Xiaomi", "samsung", "HUAWEI"), the default one when unknown. */
export function brandFix(manufacturer: string): BrandFix {
  const alias = aliases[manufacturer.trim().toLowerCase()];
  const fix = alias ? fixes[alias.fix] : undefined;
  if (!alias || !fix) {
    const fallback = fixes[DEFAULT_BRAND];
    if (!fallback) throw new Error("brand-fixes.json has no default fix");
    return { brand: DEFAULT_BRAND, name: null, reason: null, steps: fallback.steps };
  }
  return {
    brand: alias.fix,
    name: alias.name,
    reason: fix.reason?.replaceAll("{name}", alias.name) ?? null,
    steps: fix.steps,
  };
}

/** Every fix in the file, for the check that it matches the API schema. */
export function allBrandFixes(): BrandFix[] {
  return Object.keys(aliases).map(brandFix).concat(brandFix(""));
}

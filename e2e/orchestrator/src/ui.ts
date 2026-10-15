// The screen as UI Automator sees it (ADR 0019): every node with its test tag (the resource id the `e2e` flavor
// exposes), its text and where it is. Flows find elements only by tag, and by text only to check copy.

export interface UiNode {
  tag: string;
  text: string;
  /** Center, for `input tap`. */
  x: number;
  y: number;
}

const ENTITIES: Record<string, string> = { quot: '"', amp: "&", apos: "'", lt: "<", gt: ">" };

function decode(value: string): string {
  return value.replace(/&(#\d+|\w+);/g, (whole, name: string) =>
    name.startsWith("#") ? String.fromCodePoint(Number(name.slice(1))) : (ENTITIES[name] ?? whole),
  );
}

function attribute(node: string, name: string): string {
  return decode(new RegExp(` ${name}="([^"]*)"`).exec(node)?.[1] ?? "");
}

export function parseDump(xml: string): UiNode[] {
  return [...xml.matchAll(/<node [^>]*>/g)].map(([node]) => {
    const [x1, y1, x2, y2] = (/bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"/.exec(node) ?? [])
      .slice(1)
      .map(Number) as [number, number, number, number];
    return {
      tag: attribute(node, "resource-id"),
      text: attribute(node, "text") || attribute(node, "content-desc"),
      x: (x1 + x2) >> 1,
      y: (y1 + y2) >> 1,
    };
  });
}

/** What a flow looks for: a test tag, or text (a regular expression over the whole text, as copy checks need). */
export type Target = string | { text: RegExp };

export function matches(node: UiNode, target: Target): boolean {
  return typeof target === "string" ? node.tag === target : target.text.test(node.text);
}

export function describe(target: Target): string {
  return typeof target === "string" ? target : `text ${String(target.text)}`;
}

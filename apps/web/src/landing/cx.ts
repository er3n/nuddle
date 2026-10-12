/** Joins CSS module class names, skipping the ones that are not set. */
export function cx(...names: (string | false | undefined)[]): string {
  return names.filter(Boolean).join(" ");
}

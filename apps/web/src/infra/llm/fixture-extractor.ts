// Fixture TaskExtractor keyed by the exact input text, for tests and E2E mode (docs/testing.md).
// Unknown text comes back with every field missing, which makes the preview ask. A fixture
// without `timing` (written before ADR 0017) is a `by` task.
import type { Extraction, ExtractionInput } from "../../domain/extraction";
import type { TaskExtractor } from "../../domain/ports";

export type FixtureExtraction = Omit<Extraction, "timing" | "missAfterMinutes"> &
  Partial<Pick<Extraction, "timing" | "missAfterMinutes">>;

export class FixtureExtractor implements TaskExtractor {
  readonly calls: ExtractionInput[] = [];

  constructor(private readonly fixtures: ReadonlyMap<string, FixtureExtraction>) {}

  extract(input: ExtractionInput): Promise<Extraction> {
    this.calls.push(input);
    const fixture = this.fixtures.get(input.text.trim());
    return Promise.resolve(
      fixture
        ? { timing: "by", missAfterMinutes: null, ...fixture }
        : {
            recipient: null,
            title: null,
            detail: null,
            deadline: null,
            timing: "by",
            missAfterMinutes: null,
            finishMode: "done",
            missing: ["recipient", "title", "deadline"],
          },
    );
  }
}

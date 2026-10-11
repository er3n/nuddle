// Fixture TaskExtractor keyed by the exact input text, for tests and E2E mode (docs/testing.md).
// Unknown text comes back with every field missing, which makes the preview ask.
import type { Extraction, ExtractionInput } from "../../domain/extraction";
import type { TaskExtractor } from "../../domain/ports";

export class FixtureExtractor implements TaskExtractor {
  readonly calls: ExtractionInput[] = [];

  constructor(private readonly fixtures: ReadonlyMap<string, Extraction>) {}

  extract(input: ExtractionInput): Promise<Extraction> {
    this.calls.push(input);
    return Promise.resolve(
      this.fixtures.get(input.text.trim()) ?? {
        recipient: null,
        title: null,
        detail: null,
        deadline: null,
        finishMode: "done",
        missing: ["recipient", "title", "deadline"],
      },
    );
  }
}

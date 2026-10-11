// Vitest global setup for the HTTP and scenario projects: one migrated template database per run.
import type { TestProject } from "vitest/node";
import { createTemplateDatabase } from "./db";

declare module "vitest" {
  export interface ProvidedContext {
    templateDatabase: string;
  }
}

export default async function setup(project: TestProject): Promise<() => Promise<void>> {
  const template = await createTemplateDatabase();
  project.provide("templateDatabase", template.name);
  return template.drop;
}

import { describe, it, expect } from "vitest";
import { readFileSync, writeFileSync, mkdirSync } from "node:fs";
import { resolve, dirname } from "node:path";
import { buildReferenceRangeSeed } from "../../../../scripts/export-reference-ranges";

/**
 * Drift guard: the committed governed seed (consumed by ZIBO) MUST equal the generator's output from the
 * single-source UI vitals assets. Run with UPDATE_SEED=1 to regenerate after intentionally changing the
 * vitals reference assets; otherwise this fails if the UI assets and the backend seed have diverged.
 */
const SEED_PATH = resolve(
  process.cwd(),
  "../../services/zibo-service/src/main/resources/seed/observation-definitions.seed.json",
);

describe("reference-range seed export", () => {
  it("matches the committed ZIBO seed (no UI↔backend drift)", () => {
    const generated = buildReferenceRangeSeed();

    if (process.env.UPDATE_SEED) {
      mkdirSync(dirname(SEED_PATH), { recursive: true });
      writeFileSync(SEED_PATH, JSON.stringify(generated, null, 2) + "\n", "utf8");
    }

    const committed = JSON.parse(readFileSync(SEED_PATH, "utf8"));
    expect(committed).toEqual(generated);
  });

  it("emits cited DRAFT ObservationDefinitions with LOINC + qualified intervals", () => {
    const seed = buildReferenceRangeSeed();
    expect(seed.definitions.length).toBeGreaterThan(0);
    for (const d of seed.definitions) {
      expect(d.content.resourceType).toBe("ObservationDefinition");
      expect(d.content.status).toBe("draft");
      const coding = (d.content.code as { coding: { system: string; code: string }[] }).coding;
      expect(coding[0].system).toContain("loinc");
      expect(coding[0].code).toBeTruthy();
      expect((d.content.qualifiedInterval as unknown[]).length).toBeGreaterThan(0);
    }
  });
});

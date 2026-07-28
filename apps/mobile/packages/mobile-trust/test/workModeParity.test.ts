/**
 * Golden-thread parity (Phase G2) — mobile's WorkMode contract vs the canonical
 * contracts/trust/types/work-mode.ts.
 *
 * apps/mobile is its own pnpm workspace and Metro's watchFolders stop at
 * apps/mobile, so mobile cannot today `import` the canonical file the way
 * ui/one-ui-shell does (a relative import escaping its own workspace). Until
 * that bundler work lands and can be verified against a real Metro build,
 * src/workMode.ts is a hand-mirror — and a hand-mirror with no test is exactly
 * the drift the web side has ~60 golden-thread tests to prevent.
 *
 * This test removes the drift risk without the bundler change: it reads the
 * canonical source directly and fails the build the moment the two disagree.
 * The canonical file is pure TypeScript with zero imports, so it can be loaded
 * as-is here.
 */
import { describe, it, expect } from "vitest";
import { fileURLToPath } from "node:url";
import path from "node:path";
import { existsSync } from "node:fs";
import { WORK_MODES, WORK_MODE_DEFINITIONS, grantsIdentifiedClinicalRead } from "../src/workMode";

const CANONICAL_PATH = path.resolve(
  path.dirname(fileURLToPath(import.meta.url)),
  "../../../../../contracts/trust/types/work-mode.ts"
);

describe("WorkMode parity with contracts/trust", () => {
  it("can locate the canonical contract (guards against a silently-vacuous suite)", () => {
    expect(existsSync(CANONICAL_PATH)).toBe(true);
  });

  it("declares exactly the canonical WORK_MODES, in the same order", async () => {
    const canonical = await import(CANONICAL_PATH);
    expect(WORK_MODES).toEqual(canonical.WORK_MODES);
  });

  it("agrees on anchorKind and clinicalDataAccess for every mode", async () => {
    const canonical = await import(CANONICAL_PATH);
    for (const mode of canonical.WORK_MODES) {
      const theirs = canonical.WORK_MODE_DEFINITIONS[mode];
      const ours = WORK_MODE_DEFINITIONS[mode as keyof typeof WORK_MODE_DEFINITIONS];
      expect(ours, `mobile is missing a definition for ${mode}`).toBeDefined();
      expect(ours.anchorKind, `anchorKind drift on ${mode}`).toBe(theirs.anchorKind);
      expect(ours.clinicalDataAccess, `clinicalDataAccess drift on ${mode}`).toBe(theirs.clinicalDataAccess);
      expect(ours.label, `label drift on ${mode}`).toBe(theirs.label);
    }
  });

  it("defines no mode the canonical contract does not (a mobile-invented mode is a denial)", async () => {
    const canonical = await import(CANONICAL_PATH);
    expect(Object.keys(WORK_MODE_DEFINITIONS).sort()).toEqual(Object.keys(canonical.WORK_MODE_DEFINITIONS).sort());
  });

  it("agrees on which modes grant identified clinical read — the access-control-bearing rule", async () => {
    const canonical = await import(CANONICAL_PATH);
    for (const mode of canonical.WORK_MODES) {
      expect(grantsIdentifiedClinicalRead(mode), `identified-clinical-read drift on ${mode}`).toBe(
        canonical.grantsIdentifiedClinicalRead(mode)
      );
    }
  });
});

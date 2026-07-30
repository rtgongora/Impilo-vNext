/**
 * Golden-thread parity (Phase G2) — mobile's WorkMode contract vs the canonical
 * contracts/trust/types/work-mode.ts.
 *
 * These assertions used to guard a hand-mirror. src/workMode.ts now re-exports
 * the canonical file directly — both apps' metro.config.js watch the repo-root
 * contracts/ directory, and an `expo export` proves Metro bundles it — so most
 * of what follows is trivially true.
 *
 * They are kept, and one was added, because "trivially true" is a property of
 * the current implementation, not of the contract. The equality checks are what
 * fail if the re-export is ever unwound back into a copy that then drifts; the
 * structural check below is what fails the moment it is unwound at all, while
 * the copy is still correct and every other assertion here still passes.
 */
import { describe, it, expect } from "vitest";
import { fileURLToPath } from "node:url";
import path from "node:path";
import { existsSync, readFileSync } from "node:fs";
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

  it("re-exports the canonical file rather than declaring its own definitions", () => {
    const source = readFileSync(
      path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../src/workMode.ts"),
      "utf8"
    );

    expect(source).toContain("contracts/trust/types/work-mode");
    expect(
      source,
      "a local WORK_MODE_DEFINITIONS literal means the mirror is back, and a copy that is " +
        "correct on the day it lands is exactly how the last one started"
    ).not.toMatch(/WORK_MODE_DEFINITIONS[^=\n]*=\s*\{/);
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

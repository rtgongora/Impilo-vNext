/**
 * Guards the specialty tool registry.
 *
 * The root defect this pins: the panel used to pick a tool's form BY LIST POSITION, so a
 * clinical instrument's behaviour had nothing to do with what it was named. Partograph
 * rendered as a free-text notes box because it was first in its array; "Risk Assessment"
 * rendered a two-number adder because it was fourth. Position is now never consulted.
 *
 * Burns history this must not let back in: %TBSA and Parkland are owned by the Emergency
 * pack and live in `libs/burn-domain`, age-banded via `libs/paediatric-domain` on `bandKey`
 * (not `libs/emergency-domain` — %TBSA drives excision timing, graft planning, nutrition and
 * mortality prediction for months after the emergency episode closes, so the arithmetic is
 * shared with surgery; lease §5b Decision 1). The copy this app used to carry was adult-only,
 * had no injury-time clock or first-8h/second-16h split, and persisted nothing while telling
 * the clinician it had saved.
 *
 * Four properties are asserted:
 *  1. every label in SPECIALTY_WORKSPACES has an explicit registry entry, so no fallback can
 *     reappear and a new label fails the build rather than inheriting a stub;
 *  2. nothing in the panel computes a clinical value;
 *  3. every non-wired entry names an owner, so there are no orphan withdrawals;
 *  4. a replacement is only done when the thing it replaced is unreachable — APGAR points at
 *     the real screen rather than sitting beside it as a second copy.
 */
import { describe, expect, it } from "vitest";
import { SPECIALTY_WORKSPACES } from "../../data/specialtyWorkspaces";
import { SPECIALTY_TOOL_REGISTRY } from "../../data/specialtyToolRegistry";
import { resolveTool } from "../../screens/provider/SpecialtyWorkspacePanel";

const ALL_LABELS = SPECIALTY_WORKSPACES.flatMap((w) => w.tools);

describe("every advertised tool is registered", () => {
  it("covers all 108 advertised labels", () => {
    expect(ALL_LABELS.length).toBe(108);
    expect(ALL_LABELS.filter((t) => !resolveTool(t))).toEqual([]);
  });

  it("registers nothing that is not advertised", () => {
    const advertised = new Set(ALL_LABELS);
    expect(Object.keys(SPECIALTY_TOOL_REGISTRY).filter((k) => !advertised.has(k))).toEqual([]);
  });
});

describe("position is never consulted", () => {
  it("takes only the label, so a positional argument cannot be reintroduced silently", () => {
    expect(resolveTool.length).toBe(1);
  });

  it("resolves every label to a stable disposition", () => {
    for (const label of ALL_LABELS) {
      expect(resolveTool(label)).toBe(resolveTool(label));
    }
  });
});

describe("no tool computes a clinical value", () => {
  it("exposes no calculating form in the app", () => {
    // Nothing may compute here. The only real implementation reachable from this panel is a
    // CONSOLIDATED one that persists server-side. A WIRED entry would have to persist too.
    expect(Object.entries(SPECIALTY_TOOL_REGISTRY).filter(([, d]) => d.state === "WIRED")).toEqual([]);
  });

  it("keeps burns arithmetic out of the app", () => {
    for (const label of ["Burns Assessment (Rule of 9s)", "Fluid Resuscitation (Parkland)"]) {
      const d = resolveTool(label);
      expect(d?.state).toBe("IN_DEVELOPMENT");
      expect(d?.state === "IN_DEVELOPMENT" && d.owner).toBe("Emergency");
    }
  });

  it("withdraws psychiatry Risk Assessment rather than summing two numbers", () => {
    const d = resolveTool("Risk Assessment");
    expect(d?.state).toBe("IN_DEVELOPMENT");
    expect(d?.state === "IN_DEVELOPMENT" && d.withdrawnForSafety).toBe(true);
  });
});

describe("APGAR is consolidated onto the real screen, not duplicated", () => {
  it("points at APGARScreen instead of carrying a second copy", () => {
    const d = resolveTool("APGAR Record");
    expect(d?.state).toBe("CONSOLIDATED");
    expect(d?.state === "CONSOLIDATED" && d.surface).toBe("APGARScreen");
  });
});

describe("no orphan withdrawals", () => {
  it("names an owner, wave and reason on every in-development entry", () => {
    const unnamed = Object.entries(SPECIALTY_TOOL_REGISTRY).filter(
      ([, d]) => d.state === "IN_DEVELOPMENT" && (!d.owner || !d.wave || !d.note),
    );
    expect(unnamed).toEqual([]);
  });

  it("cites a completion of record where the owning lane has already delivered one", () => {
    for (const label of ["Partograph", "CTG Interpretation"]) {
      const d = resolveTool(label);
      expect(d?.state === "IN_DEVELOPMENT" && d.owner).toBe("RMNP");
      expect(d?.state === "IN_DEVELOPMENT" && d.evidence).toBeTruthy();
    }
  });
});

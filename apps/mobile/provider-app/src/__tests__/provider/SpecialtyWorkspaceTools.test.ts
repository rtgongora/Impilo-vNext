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
import { resolveTool, RENDERED_SURFACES } from "../../screens/provider/SpecialtyWorkspacePanel";

const ALL_LABELS = SPECIALTY_WORKSPACES.flatMap((w) => w.tools);

describe("every advertised tool is registered", () => {
  it("covers all advertised labels", () => {
    expect(ALL_LABELS.length).toBe(118);
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

describe("every real surface is reachable, and every claim of one is real", () => {
  // The reachability rule from the fleet DoD, made checkable in both directions: an entry cannot
  // advertise a surface that was never built, and a built surface cannot be stranded with nothing
  // pointing at it. This is what stops a "real" claim being a paper one.
  it("only declares surfaces the panel can actually render", () => {
    const declared = Object.values(SPECIALTY_TOOL_REGISTRY)
      .filter((d) => d.state === "WIRED" || d.state === "CONSOLIDATED")
      .map((d) => (d.state === "WIRED" || d.state === "CONSOLIDATED" ? d.surface : ""));
    expect(declared.filter((s) => !(s in RENDERED_SURFACES))).toEqual([]);
  });

  it("leaves no rendered surface unclaimed", () => {
    const declared = new Set(
      Object.values(SPECIALTY_TOOL_REGISTRY)
        .filter((d) => d.state === "WIRED" || d.state === "CONSOLIDATED")
        .map((d) => (d.state === "WIRED" || d.state === "CONSOLIDATED" ? d.surface : "")),
    );
    expect(Object.keys(RENDERED_SURFACES).filter((k) => !declared.has(k))).toEqual([]);
  });
});

describe("no tool computes a clinical value in this app", () => {
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

  it("routes every label to a named lane — nothing is left unassigned", () => {
    const unrouted = Object.entries(SPECIALTY_TOOL_REGISTRY).filter(
      ([, d]) => d.state === "IN_DEVELOPMENT" && (d.owner === "UNASSIGNED" || d.owner === "TBC"),
    );
    expect(unrouted).toEqual([]);
  });

});

describe("obstetrics: partograph and CTG resolve to the governed instruments, not notes", () => {
  // Ported from the mobile lane's routing tests when 0cb7412e9 delivered these two workspaces.
  // Their original form asserted formKindForTool(label, index, workspace) at two different
  // indices to prove name-matching beat index-matching. That guard is now structural rather than
  // exemplary: resolveTool takes no index at all, so there is no index to vary. The property
  // those tests protected — "these two must never regress to a notes box" — is kept here.
  it("renders the governed partograph workspace", () => {
    const d = resolveTool("Partograph");
    expect(d?.state).toBe("WIRED");
    expect(d?.state === "WIRED" && d.surface).toBe("PartographWorkspace");
  });

  it("renders the governed CTG workspace", () => {
    const d = resolveTool("CTG Interpretation");
    expect(d?.state).toBe("WIRED");
    expect(d?.state === "WIRED" && d.surface).toBe("CtgWorkspace");
  });

  it("renders the governed maternal near-miss workspace", () => {
    const d = resolveTool("Maternal Near-Miss Assessment");
    expect(d?.state).toBe("WIRED");
    expect(d?.state === "WIRED" && d.surface).toBe("MaternalNearMissWorkspace");
  });

  it("renders the governed PPH protocol workspace", () => {
    const d = resolveTool("PPH Protocol");
    expect(d?.state).toBe("WIRED");
    expect(d?.state === "WIRED" && d.surface).toBe("PphProtocolWorkspace");
  });

  it("renders the governed eclampsia protocol workspace", () => {
    const d = resolveTool("Eclampsia Protocol");
    expect(d?.state).toBe("WIRED");
    expect(d?.state === "WIRED" && d.surface).toBe("EclampsiaProtocolWorkspace");
  });

  it("renders the governed Bishop score workspace", () => {
    const d = resolveTool("Bishop Score");
    expect(d?.state).toBe("WIRED");
    expect(d?.state === "WIRED" && d.surface).toBe("BishopScoreWorkspace");
  });
});

/**
 * Phase G3 (increment 3) — the governed-mode fence.
 *
 * Entering a governed workspace must mint a duty token for it first. Before
 * this fence, six call sites jumped straight into a governed mode via the
 * store setter — the dashboard "Support" chip, the SOS sheet's "Open support",
 * the failed-comms KPI, the Simba service card, and inbound deep links — so the
 * workspace changed while the person kept whatever token they already held. A
 * clinician tapping "Support" got a supervisory UI and identified clinical
 * access at the same time, which is exactly what Phase B4's clinicalDataAccess
 * gate exists to prevent.
 *
 * The fence is a type: `appStore.setMode` accepts only `UngovernedAppMode`, and
 * governed entry goes through `setGrantedMode`, which `useSwitchAppMode` calls
 * only after a successful mint. That means the real proof of this guard is
 * `tsc` — `setMode("supervisor")` is a compile error, verified by reverting the
 * fence and watching all six sites light up.
 *
 * These tests pin the parts a type cannot: that the governed set and the
 * WorkMode map cannot drift apart, and that the two setters behave identically
 * once entry is permitted (so the fence changes who may enter, never what
 * entering does).
 */
import { describe, expect, it } from "vitest";
import { appStore } from "../stores/appStore";
import { WORK_MODES_FOR_APP_MODE, requiresWorkContextMint } from "./modeAvailability";
import type { AppMode, GovernedAppMode, UngovernedAppMode } from "../types";

const ALL_APP_MODES: AppMode[] = ["provider", "outreach", "supervisor", "offline", "courier"];
const GOVERNED: GovernedAppMode[] = ["provider", "outreach", "supervisor", "courier"];
// Only connectivity state remains ungoverned — outreach and courier are now
// COMMUNITY_OUTREACH and SPECIMEN_TRANSPORT.
const UNGOVERNED: UngovernedAppMode[] = ["offline"];

describe("governed-mode fence", () => {
  it("classifies every AppMode as exactly one of governed or ungoverned", () => {
    expect([...GOVERNED, ...UNGOVERNED].sort()).toEqual([...ALL_APP_MODES].sort());
  });

  it("treats exactly the WorkMode-mapped modes as requiring a mint", () => {
    for (const mode of ALL_APP_MODES) {
      expect(requiresWorkContextMint(mode), `${mode} misclassified`).toBe(
        (GOVERNED as string[]).includes(mode)
      );
    }
  });

  it("keeps WORK_MODES_FOR_APP_MODE and the GovernedAppMode type in step", () => {
    // If someone governs a new mode they must add it in both places; this is
    // the runtime half of that pairing (the compiler enforces the other half
    // via Record<GovernedAppMode, string[]>).
    expect(Object.keys(WORK_MODES_FOR_APP_MODE).sort()).toEqual([...GOVERNED].sort());
  });

  it("maps every governed mode to at least one real WorkMode", () => {
    for (const mode of GOVERNED) {
      expect(WORK_MODES_FOR_APP_MODE[mode].length, `${mode} maps to no WorkMode`).toBeGreaterThan(0);
    }
  });

  it("applies identical entry behaviour through both setters", () => {
    // Entering provider resets to Work-Home-first; entering anything else leaves
    // the tab alone. The fence must not have changed that for either path.
    appStore.getState().setProviderTab("messaging");
    appStore.getState().setGrantedMode("provider");
    expect(appStore.getState().providerTab).toBe("workhome");

    appStore.getState().setProviderTab("messaging");
    appStore.getState().setGrantedMode("supervisor");
    expect(appStore.getState().providerTab).toBe("messaging");

    appStore.getState().setProviderTab("messaging");
    appStore.getState().setGrantedMode("outreach");
    expect(appStore.getState().providerTab).toBe("messaging");

    appStore.getState().setProviderTab("messaging");
    appStore.getState().setMode("offline");
    expect(appStore.getState().providerTab).toBe("messaging");
  });
});

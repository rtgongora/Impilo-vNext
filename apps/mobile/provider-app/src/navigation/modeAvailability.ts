/**
 * Phase G3 (increment 1) — augments ModeSwitcher's role-based mode gating
 * with real resolved work contexts, without replacing it.
 *
 * `ModeSwitcher`'s existing `MODE_ROLES` gate matches raw Keycloak realm
 * roles — a different, frequently out-of-sync vocabulary from the
 * WGV/VASHANDI role templates and resolved assignments the BFF's work-context
 * resolver actually proves (Phase B's role_template_catalog work established
 * this split — canonical roles and Keycloak roles have never been the same
 * list in this codebase). This module lets a REAL proven assignment unlock
 * "provider" or "supervisor" even when the Keycloak role string hasn't
 * caught up.
 *
 * Deliberately ADDITIVE ONLY: a mode present in `roleBasedModes` is never
 * removed here, and resolved-context evidence is used only to ADD modes, not
 * to withhold them — because work-context resolution is async/best-effort
 * (see useAutoResolveWorkContext), a slow or empty resolution must never look
 * like "you no longer have this role" to a session that had it a moment ago.
 * The actual security boundary stays server-side (PolicyEngine's
 * clinicalDataAccess-gated role folding, Phase B4) — this only changes which
 * buttons are offered, never what a token can do once minted.
 *
 * `outreach`, `courier`, and `offline` have no WorkMode analogue in the
 * resolver yet (confirmed absent from the BFF's 10-mode catalog against this
 * app's 5-mode AppMode enum) — they are left untouched by this function,
 * still gated purely by the existing role match, honestly reflecting that no
 * real backend assignment exists yet to confirm or extend them.
 */
import type { ResolvedWorkContextView } from "@impilo/mobile-trust";
import type { AppMode, GovernedAppMode } from "../types";

const CLINICAL_WORK_MODES = new Set(["CLINICAL_CARE", "VIRTUAL_CARE"]);
const SUPERVISORY_WORK_MODES = new Set([
  "DEPARTMENT_MANAGEMENT",
  "FACILITY_MANAGEMENT",
  "JURISDICTION_OPERATIONS",
  "PROGRAMME_MANAGEMENT",
]);

/**
 * Which governed WorkModes an AppMode may be entered under, most-preferred
 * first. An AppMode absent from this map has NO WorkMode analogue in the
 * resolver (outreach, courier, offline) and stays a local navigation choice —
 * see useSwitchAppMode.
 *
 * This is what makes "supervisor grants no automatic patient access" structural
 * rather than cosmetic: entering supervisor mints a management-mode token, and
 * the PDP's clinicalDataAccess gate (Phase B4) then declines to fold the
 * clinical role every clinical policy rule matches on.
 */
export const WORK_MODES_FOR_APP_MODE: Record<GovernedAppMode, string[]> = {
  provider: ["CLINICAL_CARE", "VIRTUAL_CARE"],
  supervisor: ["FACILITY_MANAGEMENT", "DEPARTMENT_MANAGEMENT", "JURISDICTION_OPERATIONS", "PROGRAMME_MANAGEMENT"],
};

/**
 * True when entering this AppMode must be backed by a freshly minted duty token.
 *
 * A type predicate, not a plain boolean, so the compiler carries the answer to
 * the call site: the ungoverned branch narrows to `UngovernedAppMode` (the only
 * thing `appStore.setMode` accepts) and the governed branch narrows to
 * `GovernedAppMode` (the only thing `setGrantedMode` accepts). Adding a mode to
 * `WORK_MODES_FOR_APP_MODE` without widening `GovernedAppMode` is a compile
 * error, and vice versa — the map and the type cannot drift apart.
 */
export function requiresWorkContextMint(appMode: AppMode): appMode is GovernedAppMode {
  return appMode in WORK_MODES_FOR_APP_MODE;
}

/**
 * Picks the context + WorkMode to enter `appMode` under, preferring to stay in
 * the context the person is already working in. Returns null when no resolved
 * assignment grants the mode — the caller must refuse, never fall back to a
 * mode the backend has not granted.
 */
export function selectContextForAppMode(
  appMode: GovernedAppMode,
  contexts: ResolvedWorkContextView[] | null,
  currentContextId?: string
): { context: ResolvedWorkContextView; workMode: string } | null {
  const candidates = WORK_MODES_FOR_APP_MODE[appMode];
  if (!candidates || !contexts?.length) return null;

  const ordered = [
    ...contexts.filter((c) => c.contextId === currentContextId),
    ...contexts.filter((c) => c.contextId !== currentContextId),
  ];

  for (const workMode of candidates) {
    const context = ordered.find((c) => c.availableModes?.includes(workMode as never));
    if (context) return { context, workMode };
  }
  return null;
}

function anyContextGrants(contexts: ResolvedWorkContextView[], modes: Set<string>): boolean {
  return contexts.some((c) => c.availableModes?.some((m) => modes.has(m)));
}

export function deriveAvailableAppModes(
  roleBasedModes: AppMode[],
  resolvedContexts: ResolvedWorkContextView[] | null
): AppMode[] {
  const modes = new Set<AppMode>(roleBasedModes);

  if (resolvedContexts && resolvedContexts.length > 0) {
    if (anyContextGrants(resolvedContexts, CLINICAL_WORK_MODES)) {
      modes.add("provider");
    }
    if (anyContextGrants(resolvedContexts, SUPERVISORY_WORK_MODES)) {
      modes.add("supervisor");
    }
  }

  return Array.from(modes);
}

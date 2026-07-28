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
import type { AppMode } from "../types";

const CLINICAL_WORK_MODES = new Set(["CLINICAL_CARE", "VIRTUAL_CARE"]);
const SUPERVISORY_WORK_MODES = new Set([
  "DEPARTMENT_MANAGEMENT",
  "FACILITY_MANAGEMENT",
  "JURISDICTION_OPERATIONS",
  "PROGRAMME_MANAGEMENT",
]);

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

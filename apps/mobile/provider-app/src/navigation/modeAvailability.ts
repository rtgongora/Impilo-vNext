/**
 * Phase G3 — augments ModeSwitcher's role-based mode gating with real resolved
 * work contexts, without replacing it.
 *
 * Additive only: a mode present in `roleBasedModes` is never removed here.
 * Resolved-context evidence ADDS modes — including outreach and courier once
 * those WorkModes exist on a proven context.
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
const OUTREACH_WORK_MODES = new Set(["COMMUNITY_OUTREACH"]);
const COURIER_WORK_MODES = new Set(["SPECIMEN_TRANSPORT"]);

/**
 * Which governed WorkModes an AppMode may be entered under, most-preferred
 * first. An AppMode absent from this map has NO WorkMode analogue — now only
 * `offline`, which is connectivity state rather than a kind of work.
 */
export const WORK_MODES_FOR_APP_MODE: Record<GovernedAppMode, string[]> = {
  provider: ["CLINICAL_CARE", "VIRTUAL_CARE"],
  outreach: ["COMMUNITY_OUTREACH"],
  supervisor: ["FACILITY_MANAGEMENT", "DEPARTMENT_MANAGEMENT", "JURISDICTION_OPERATIONS", "PROGRAMME_MANAGEMENT"],
  courier: ["SPECIMEN_TRANSPORT"],
};

export function requiresWorkContextMint(appMode: AppMode): appMode is GovernedAppMode {
  return appMode in WORK_MODES_FOR_APP_MODE;
}

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
    if (anyContextGrants(resolvedContexts, OUTREACH_WORK_MODES)) {
      modes.add("outreach");
    }
    if (anyContextGrants(resolvedContexts, COURIER_WORK_MODES)) {
      modes.add("courier");
    }
  }

  return Array.from(modes);
}

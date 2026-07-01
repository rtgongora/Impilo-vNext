/**
 * Cadre Decision Service — resolves the adaptive Encounter Cockpit spine from the PCT Cadre Engine.
 *
 * Backend: experience-bff `POST /internal/v1/encounters/cadre-decision` (the same live route the web
 * AdaptiveEncounterCockpit uses via useCadreDecision). The cockpit renders STRICTLY from the returned
 * `cockpitSpine`: a disabled action is shown greyed with its reason, never a live/dead button. Authz-gated
 * actions still traverse Tshepo ext_authz at execution — this only drives visibility/enablement.
 * Closes part of GAP-19 (encounter cockpit had no cadre wiring on mobile). Form-content depth is GAP-10.
 */

import { apiClient } from "@impilo/mobile-api-client";

export interface CadreCockpitAction {
  action: string;
  enabled: boolean;
  requiresStepUp: boolean;
}

export interface CadreCockpitTab {
  tab: string;
  enabled: boolean;
  actions: CadreCockpitAction[];
}

export interface CadreDecision {
  permittedWorkflows: string[];
  cockpitSpine: CadreCockpitTab[];
  escalation: {
    breakGlassAvailable: boolean;
    supervisorRequiredFor: string[];
  };
  auditRef: string;
}

export interface CadreDecisionRequest {
  role?: string;
  cadre?: string;
  scope?: string;
  visitType?: string;
  acuity?: number;
  context?: string;
  accessState?: string;
  journeyId?: string;
  encounterId?: string;
}

/** Resolve the cadre decision for an encounter context (audited server-side on each call). */
export async function resolveCadreDecision(
  request: CadreDecisionRequest,
): Promise<CadreDecision> {
  const response = await apiClient.post<{ data: CadreDecision }>(
    "/internal/v1/encounters/cadre-decision",
    request,
  );
  return response.data.data;
}

/**
 * Experience UI — Cadre Engine decision hook (shared read-model C9).
 *
 * Resolves the adaptive Encounter Cockpit spine from PCT via the BFF. The cockpit renders STRICTLY from
 * `cockpitSpine`; a disabled action is greyed out (with its escalation reason), never a live dead button.
 * Authz-gated actions still call the existing Tshepo ext_authz path at execution time.
 */

import { useMutation } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

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

type CadreDecisionResponse = ApiResponse<CadreDecision>;

/**
 * Resolve a Cadre Engine decision. A mutation (not a query) because each resolution is audited server-side
 * and is parameterised by live context (acuity, access-state) the caller assembles at cockpit-open time.
 */
export function useResolveCadreDecision() {
  return useMutation<CadreDecision, Error, CadreDecisionRequest>({
    mutationFn: async (request) => {
      // request<T> returns the parsed body directly; the BFF wraps the decision under `data`.
      const response = await apiClient.post<CadreDecisionResponse>(
        "/internal/v1/encounters/cadre-decision",
        request,
      );
      return response.data;
    },
  });
}

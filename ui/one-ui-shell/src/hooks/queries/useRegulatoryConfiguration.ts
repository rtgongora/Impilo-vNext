/**
 * The regulator's own configuration, read-only (NCZ-W1A).
 *
 * These types mirror the BFF's composed response exactly. They are deliberately NOT the shape of
 * the org-registry entities: a raw passthrough would tie this hook to whatever those entities
 * happen to serialise, and that coupling has broken typed hooks in this codebase before.
 */

import { useQuery } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";

/** Whether the version-controlled source pack agrees with what the Registry holds. */
export type ConfigPackLoadState = "NEVER_LOADED" | "LOADED" | "LOAD_FAILED";

export interface ConfigPackSource {
  loadState: ConfigPackLoadState;
  loadFailureReason: string | null;
  loadCheckedAt: string | null;
  sourceRef: string | null;
  /**
   * Always false. A load failure means the FILE disagrees with the Registry; it says nothing about
   * the activated release, which was separately approved and is what live cases are pinned to.
   */
  activatedConfigurationAffected: boolean;
}

export interface ConfigPolicyState {
  /** null when the definition carries no regulator-set value at all. */
  status: "CONFIRMED" | "PENDING_REGULATOR_APPROVAL" | null;
  /** The council decision this value is waiting on, e.g. "NCZ-DEC-002". */
  decisionRef: string | null;
  valueSet: boolean;
}

export interface ConfigDefinitionSummary {
  typeCode: string;
  definitionKey: string;
  label: string;
  semanticVersion: string;
  selfServiceRoute: string | null;
  contentHash: string;
  pinnedAtMoment: string | null;
  policy: ConfigPolicyState;
  /**
   * What the definition actually says. Withholding it would leave a council able to see that a
   * rule exists but not what it holds, and would leave role-aware navigation with no source for
   * the capability lists it must honour.
   */
  payload: Record<string, unknown> | null;
}

export interface ConfigReleaseSummary {
  id: string;
  releaseKey: string;
  label: string;
  lifecycleState: string;
  submittedBy: string | null;
  activatedAt: string | null;
}

export interface ConfigPackSummary {
  packId: string;
  packKey: string;
  label: string;
  description: string | null;
  source: ConfigPackSource;
  releases: ConfigReleaseSummary[];
  /** False when no release has been activated yet — the ordinary state of a freshly seeded pack. */
  activated: boolean;
  definitions: ConfigDefinitionSummary[];
  /** "TYPE_CODE:definition-key" -> definition id, for version history. */
  definitionIndex: Record<string, string>;
}

export interface RegulatoryConfiguration {
  organizationId: string;
  packs: ConfigPackSummary[];
}

export interface ConfigVersionHistoryEntry {
  id: string;
  semanticVersion: string;
  lifecycleState: string;
  authoredBy: string | null;
  approvedAt: string | null;
  effectiveFrom: string | null;
  contentHash: string;
  policyStatus: string | null;
  policyDecisionRef: string | null;
  sourcePackRef: string | null;
}

export function useRegulatoryConfiguration(organizationId: string | undefined) {
  return useQuery<RegulatoryConfiguration>({
    queryKey: ["regulatory-configuration", organizationId],
    enabled: Boolean(organizationId),
    queryFn: async () =>
      apiClient.get<RegulatoryConfiguration>(
        `/api/v1/regulatory/organizations/${encodeURIComponent(String(organizationId))}/configuration`,
      ),
  });
}

export function useConfigVersionHistory(definitionId: string | undefined) {
  return useQuery<{ definitionId: string; versions: ConfigVersionHistoryEntry[] }>({
    queryKey: ["regulatory-configuration-versions", definitionId],
    enabled: Boolean(definitionId),
    queryFn: async () =>
      apiClient.get<{ definitionId: string; versions: ConfigVersionHistoryEntry[] }>(
        `/api/v1/regulatory/configuration/definitions/${encodeURIComponent(String(definitionId))}/versions`,
      ),
  });
}

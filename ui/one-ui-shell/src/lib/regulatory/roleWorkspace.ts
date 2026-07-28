/**
 * Role-aware regulatory navigation, driven by the council's own configuration (NCZ-W1B).
 *
 * The brief's requirement is a separation of duties: a finance officer sees no examination marks,
 * an examination officer sees no refunds. That separation is expressed as data — the ROLE_WORKSPACE
 * definitions in the council's activated pack — rather than as a switch statement, because the next
 * council will draw the lines differently and must not need a code change to do it.
 *
 * WHAT THIS IS NOT: security. Hiding a surface in the shell is not an access control, and this file
 * must never be mistaken for one. The boundary is the PDP, evaluated per action against the
 * org-scoped work-context session. What this does is keep the interface honest — it stops offering
 * a council officer work they are not appointed to do, and it refuses to show a capability the
 * platform has not built yet as though it were merely empty.
 */

import type {
  ConfigDefinitionSummary,
  RegulatoryConfiguration,
} from "@/hooks/queries/useRegulatoryConfiguration";

export interface RoleWorkspace {
  roleCode: string;
  /** Capability tokens this role may be offered. */
  sees: Set<string>;
  /** Capability tokens this role must never be offered, even if something else grants them. */
  neverSees: Set<string>;
  requiresFacilityAssignment: boolean;
  /** Set when the platform has not built this role's surfaces yet. */
  capabilityStatus: string | null;
  capabilityNote: string | null;
  capabilityDecisionRefs: string[];
}

interface RoleWorkspacePayload {
  roleCode?: string;
  sees?: string[];
  neverSees?: string[];
  requiresFacilityAssignment?: boolean;
  capabilityStatus?: string;
  capabilityNote?: string;
  capabilityDecisionRefs?: string[];
}

function payloadOf(definition: ConfigDefinitionSummary): RoleWorkspacePayload {
  return (definition.payload ?? {}) as RoleWorkspacePayload;
}

/** Every role workspace the regulator's activated configuration defines. */
export function roleWorkspaces(config: RegulatoryConfiguration | undefined): RoleWorkspace[] {
  if (!config) return [];
  return config.packs
    .flatMap((pack) => pack.definitions)
    .filter((definition) => definition.typeCode === "ROLE_WORKSPACE")
    .map((definition) => {
      const payload = payloadOf(definition);
      return {
        roleCode: payload.roleCode ?? definition.definitionKey,
        sees: new Set(payload.sees ?? []),
        neverSees: new Set(payload.neverSees ?? []),
        requiresFacilityAssignment: payload.requiresFacilityAssignment ?? false,
        capabilityStatus: payload.capabilityStatus ?? null,
        capabilityNote: payload.capabilityNote ?? null,
        capabilityDecisionRefs: payload.capabilityDecisionRefs ?? [],
      };
    });
}

export function roleWorkspaceFor(
  config: RegulatoryConfiguration | undefined,
  roleCode: string | null | undefined,
): RoleWorkspace | undefined {
  if (!roleCode) return undefined;
  return roleWorkspaces(config).find((workspace) => workspace.roleCode === roleCode);
}

export type CapabilityVerdict = "OFFER" | "WITHHOLD" | "AWAITING_IMPLEMENTATION";

/**
 * Whether a capability may be offered to this role.
 *
 * `neverSees` wins over `sees`. A capability named in both is a configuration mistake, and the safe
 * reading of a mistake about separation of duties is the restrictive one — a council that has said
 * "this role must never see refunds" has said something more deliberate than a broad grant.
 *
 * With no role workspace resolved (no active regulatory session, or a role the pack does not
 * define) every capability is OFFERED. That is not a gap: this file is not the access boundary, and
 * silently hiding a regulator's own work because a configuration lookup failed would be a worse
 * failure than showing a link the PDP then refuses.
 */
export function capabilityVerdict(
  workspace: RoleWorkspace | undefined,
  capability: string,
): CapabilityVerdict {
  if (!workspace) return "OFFER";
  if (workspace.neverSees.has(capability)) return "WITHHOLD";
  if (workspace.capabilityStatus === "AWAITING_IMPLEMENTATION" && workspace.sees.has(capability)) {
    return "AWAITING_IMPLEMENTATION";
  }
  if (workspace.sees.size === 0) return "OFFER";
  return workspace.sees.has(capability) ? "OFFER" : "WITHHOLD";
}

/**
 * Council staff are org-attached, not facility-attached. Asking a registration officer which
 * facility they are working from is asking the wrong question, and a picker that demands one would
 * block a regulator from working at all.
 */
export function requiresFacilityAssignment(workspace: RoleWorkspace | undefined): boolean {
  return workspace?.requiresFacilityAssignment ?? false;
}

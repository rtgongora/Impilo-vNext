import { getAdminGovernance, postAdminGovernance } from "./client";
import type { AdminGovernanceActionResponse, LookupEnvelope } from "../types";

export async function listAuthorisedRepresentatives(orgId: string) {
  return getAdminGovernance<LookupEnvelope<{ items: Record<string, unknown>[] }>>(
    `/organisations/${encodeURIComponent(orgId)}/authorised-representatives`,
  );
}

export async function inviteAuthorisedRepresentative(orgId: string, body: Record<string, unknown>) {
  return postAdminGovernance<AdminGovernanceActionResponse>(
    `/organisations/${encodeURIComponent(orgId)}/authorised-representatives`,
    body,
  );
}

export async function suspendAuthorisedRepresentative(orgId: string, repId: string) {
  return postAdminGovernance<AdminGovernanceActionResponse>(
    `/organisations/${encodeURIComponent(orgId)}/authorised-representatives/${encodeURIComponent(repId)}/suspend`,
    {},
  );
}

export async function revokeAuthorisedRepresentative(orgId: string, repId: string) {
  return postAdminGovernance<AdminGovernanceActionResponse>(
    `/organisations/${encodeURIComponent(orgId)}/authorised-representatives/${encodeURIComponent(repId)}/revoke`,
    {},
  );
}

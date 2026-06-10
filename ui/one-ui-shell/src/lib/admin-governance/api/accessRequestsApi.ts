import type { AccessRequest, AccessRequestListResponse, AdminGovernanceActionResponse } from "../types";
import { getAdminGovernance, isActionResponse, postAdminGovernance } from "./client";

export async function listAccessRequests(): Promise<AccessRequest[] | AdminGovernanceActionResponse> {
  const result = await getAdminGovernance<AccessRequestListResponse>(
    "/access-requests",
    "Access request queue is not yet available from the BFF.",
  );
  if (isActionResponse(result)) return result;
  return result.items ?? [];
}

export async function getAccessRequest(requestId: string): Promise<AccessRequest | AdminGovernanceActionResponse> {
  const result = await getAdminGovernance<AccessRequest>(
    `/access-requests/${requestId}`,
    "Access request detail is not yet available from the BFF.",
  );
  return isActionResponse(result) ? result : result;
}

export async function decideAccessRequest(
  requestId: string,
  decision: "approve" | "reject" | "request-more-information" | "escalate" | "revoke",
  notes?: string,
): Promise<AdminGovernanceActionResponse> {
  const result = await postAdminGovernance<AdminGovernanceActionResponse>(
    `/access-requests/${requestId}/${decision}`,
    { decision, notes },
    "Access request decisions are not yet available from the BFF.",
  );
  return isActionResponse(result) ? result : result;
}

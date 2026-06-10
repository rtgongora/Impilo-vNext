import type { AdminGovernanceActionResponse, AuditEventEnvelope } from "../types";
import { getAdminGovernance, isActionResponse } from "./client";

export async function listAdminGovernanceAudit(): Promise<AuditEventEnvelope[] | AdminGovernanceActionResponse> {
  const result = await getAdminGovernance<{ items: AuditEventEnvelope[] }>(
    "/audit",
    "Audit feed is not yet available from the BFF.",
  );
  if (isActionResponse(result)) return result;
  return result.items ?? [];
}

import type { AdminGovernanceActionResponse, OpaPrecheckRequest } from "../types";
import { isActionResponse, postAdminGovernance } from "./client";

export async function runOpaPrecheck(request: OpaPrecheckRequest): Promise<AdminGovernanceActionResponse> {
  const result = await postAdminGovernance<AdminGovernanceActionResponse>(
    "/precheck",
    request,
    "Precheck unavailable — backend integration pending. Do not submit until precheck is available.",
  );
  return isActionResponse(result) ? result : result;
}

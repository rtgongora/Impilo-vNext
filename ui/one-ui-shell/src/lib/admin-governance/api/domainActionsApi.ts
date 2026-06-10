import type { AdminGovernanceActionResponse } from "../types";
import { isActionResponse, postAdminGovernance } from "./client";

export async function submitDomainGovernanceAction(
  action: string,
  body: Record<string, unknown>,
): Promise<AdminGovernanceActionResponse> {
  const result = await postAdminGovernance<AdminGovernanceActionResponse>(
    `/actions/${action}`,
    body,
    "This governance action is not yet wired to the backend.",
  );
  return isActionResponse(result) ? result : result;
}

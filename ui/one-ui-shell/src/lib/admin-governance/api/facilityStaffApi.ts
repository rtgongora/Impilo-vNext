import type { AdminGovernanceActionResponse, FacilityStaffAssignmentRequest } from "../types";
import { isActionResponse, postAdminGovernance } from "./client";

export async function createFacilityStaffAssignment(
  request: FacilityStaffAssignmentRequest,
): Promise<AdminGovernanceActionResponse> {
  const result = await postAdminGovernance<AdminGovernanceActionResponse>(
    `/facility/${request.facilityId}/staff-assignments`,
    request,
    "Facility staff assignment endpoint is not yet available — an access request may be recorded instead.",
  );
  return isActionResponse(result) ? result : result;
}

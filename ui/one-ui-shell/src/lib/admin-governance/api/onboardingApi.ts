import type { AdminGovernanceActionResponse, OnboardingFlowSubmission, OpaPrecheckRequest } from "../types";
import { isActionResponse, postAdminGovernance } from "./client";

export async function submitOnboardingFlow(
  flowId: string,
  submission: OnboardingFlowSubmission,
): Promise<AdminGovernanceActionResponse> {
  const result = await postAdminGovernance<AdminGovernanceActionResponse>(
    `/onboarding/${flowId}`,
    submission,
    "Onboarding submission endpoint is not yet available — submit an access request instead.",
  );
  return isActionResponse(result) ? result : result;
}

export function buildOnboardingPrecheck(flowId: string, submission: OnboardingFlowSubmission): OpaPrecheckRequest {
  const actionMap: Record<string, string> = {
    citizen: "ONBOARD_CITIZEN",
    "provider-worker": "ONBOARD_PROVIDER_WORKER",
    "public-sector-worker": "ONBOARD_PUBLIC_SECTOR_WORKER",
    "hsc-user": "ONBOARD_HSC_USER",
    "municipal-user": "ONBOARD_MUNICIPAL_USER",
    "private-facility-user": "ONBOARD_PRIVATE_FACILITY_USER",
    "regulator-user": "ONBOARD_REGULATOR_USER",
    "madi-user": "ONBOARD_MADI_USER",
    "marketplace-user": "ONBOARD_MARKETPLACE_USER",
    "payer-user": "ONBOARD_PAYER_USER",
    "training-user": "ONBOARD_TRAINING_USER",
    "external-partner-user": "ONBOARD_EXTERNAL_PARTNER",
    "system-admin": "ONBOARD_SYSTEM_ADMIN",
  };
  return {
    requestedAction: actionMap[flowId] ?? `ONBOARD_${flowId.toUpperCase()}`,
    sourcePage: `/work/administration-governance/onboard/${flowId}`,
    targetOrganisationId: submission.organisationId,
    targetSubjectId: submission.targetProviderWorkerId ?? submission.targetHealthId,
    roleTemplate: submission.roleTemplate,
    requestedScope: submission.requestedScope,
    requestedEnvironment: submission.requestedEnvironment,
    requestedDataScope: submission.requestedDataScope,
    requestedDurationDays: submission.requestedDurationDays,
    crossBorderFlag: submission.crossBorderFlag,
    context: submission.profile,
  };
}

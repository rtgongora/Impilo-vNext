/**
 * GDHCN readiness cockpit BFF client (Wave B B6). Reads the per-tenant readiness assessment
 * owned by tshepo-authz (proxied by the experience-bff); updates are admin + step-up gated
 * server-side.
 */
import type { AdminGovernanceActionResponse } from "../types";
import { getAdminGovernance, isActionResponse, postAdminGovernance } from "./client";

export interface GdhcnReadinessDomainRow {
  domainKey: string;
  title: string;
  currentMaturity: string;
  targetMaturity: string;
  owner: string | null;
  evidenceStatus: string | null;
  remediationStatus: string | null;
  linkedComponent: string | null;
  updatedBy: string | null;
  updatedAt: string | null;
}

interface ReadinessListResponse {
  domains: GdhcnReadinessDomainRow[];
}

export type GdhcnReadinessPatch = Partial<
  Pick<
    GdhcnReadinessDomainRow,
    "currentMaturity" | "targetMaturity" | "owner" | "evidenceStatus" | "remediationStatus" | "linkedComponent"
  >
>;

export async function listGdhcnReadiness(): Promise<GdhcnReadinessDomainRow[] | AdminGovernanceActionResponse> {
  const result = await getAdminGovernance<ReadinessListResponse>(
    "/gdhcn-readiness",
    "GDHCN readiness is not yet available from the BFF.",
  );
  if (isActionResponse(result)) return result;
  return result.domains ?? [];
}

export async function updateGdhcnReadiness(
  domainKey: string,
  patch: GdhcnReadinessPatch,
): Promise<AdminGovernanceActionResponse> {
  const result = await postAdminGovernance<AdminGovernanceActionResponse>(
    `/gdhcn-readiness/${domainKey}`,
    patch,
    "GDHCN readiness updates are not yet available from the BFF.",
  );
  return isActionResponse(result) ? result : result;
}

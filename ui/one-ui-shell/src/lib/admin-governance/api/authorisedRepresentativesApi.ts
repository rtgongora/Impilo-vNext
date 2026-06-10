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

export async function resendAuthorisedRepresentativeInvitation(orgId: string, repId: string) {
  return postAdminGovernance<AdminGovernanceActionResponse>(
    `/organisations/${encodeURIComponent(orgId)}/authorised-representatives/${encodeURIComponent(repId)}/invitations/resend`,
    {},
  );
}

export async function revokeAuthorisedRepresentativeInvitation(orgId: string, repId: string) {
  return postAdminGovernance<AdminGovernanceActionResponse>(
    `/organisations/${encodeURIComponent(orgId)}/authorised-representatives/${encodeURIComponent(repId)}/invitations/revoke`,
    {},
  );
}

export async function getAuthorisedRepresentativeInvitationAuditTrail(orgId: string, repId: string) {
  return getAdminGovernance<LookupEnvelope<{ invitation: Record<string, unknown>; auditTrail: Record<string, unknown>[] }>>(
    `/organisations/${encodeURIComponent(orgId)}/authorised-representatives/${encodeURIComponent(repId)}/invitations/audit-trail`,
  );
}

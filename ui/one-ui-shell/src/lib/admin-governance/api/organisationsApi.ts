import { getAdminGovernance, postAdminGovernance, isActionResponse } from "./client";
import type { AdminGovernanceActionResponse, LookupEnvelope, OrganisationRecord } from "../types";

export async function listOrganisations(): Promise<LookupEnvelope<{ items: OrganisationRecord[] }> | AdminGovernanceActionResponse> {
  return getAdminGovernance<LookupEnvelope<{ items: OrganisationRecord[] }>>(
    "/organisations",
    "Organisation registry is not currently available.",
  );
}

export async function createOrganisation(body: Record<string, unknown>): Promise<AdminGovernanceActionResponse> {
  const result = await postAdminGovernance<AdminGovernanceActionResponse>("/organisations", body);
  return isActionResponse(result) ? result : (result as AdminGovernanceActionResponse);
}

export async function getOrganisation(orgId: string) {
  return getAdminGovernance<LookupEnvelope>("/organisations/" + orgId);
}

export async function verifyOrganisation(orgId: string) {
  return postAdminGovernance<AdminGovernanceActionResponse>("/organisations/" + orgId + "/verify", {});
}

export async function suspendOrganisation(orgId: string) {
  return postAdminGovernance<AdminGovernanceActionResponse>("/organisations/" + orgId + "/suspend", {});
}

export async function offboardOrganisation(orgId: string) {
  return postAdminGovernance<AdminGovernanceActionResponse>("/organisations/" + orgId + "/offboard", {});
}

export async function listOrganisationUsers(orgId: string) {
  return getAdminGovernance<LookupEnvelope<{ items: Record<string, unknown>[] }>>("/organisations/" + orgId + "/users");
}

export async function addOrganisationUser(orgId: string, body: Record<string, unknown>) {
  return postAdminGovernance<AdminGovernanceActionResponse>("/organisations/" + orgId + "/users", body);
}

export function buildOrganisationCreatePayload(form: Record<string, string>) {
  return {
    organisationCode: form.organisationCode,
    organisationType: form.organisationType,
    legalName: form.legalName,
    tradingName: form.tradingName,
    country: form.country,
    registrationNumber: form.registrationNumber,
    regulatorRegistrationNumber: form.regulatorRegistrationNumber,
    parentOrganisationId: form.parentOrganisationId || undefined,
    ownershipType: form.ownershipType,
    publicPrivateClassification: form.publicPrivateClassification,
    operatingJurisdictions: form.operatingJurisdictions
      ? form.operatingJurisdictions.split(",").map((value) => value.trim()).filter(Boolean)
      : [],
  };
}

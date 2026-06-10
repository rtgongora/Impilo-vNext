import { getAdminGovernance, postAdminGovernance } from "./client";
import type { AdminGovernanceActionResponse, LookupEnvelope } from "../types";

export async function uploadImportBatch(body: Record<string, unknown>) {
  return postAdminGovernance<LookupEnvelope | AdminGovernanceActionResponse>("/imports", body);
}

export async function listImportBatches(organisationId: string) {
  return getAdminGovernance<LookupEnvelope<{ items: Record<string, unknown>[] }>>(`/imports?organisationId=${encodeURIComponent(organisationId)}`);
}

export async function approveImportBatch(importBatchId: string) {
  return postAdminGovernance<AdminGovernanceActionResponse>(`/imports/${encodeURIComponent(importBatchId)}/approve`, {});
}

export async function sendImportInvitations(importBatchId: string) {
  return postAdminGovernance<AdminGovernanceActionResponse>(`/imports/${encodeURIComponent(importBatchId)}/send-invitations`, {});
}

export async function listImportRows(importBatchId: string) {
  return getAdminGovernance<LookupEnvelope<{ items: Record<string, unknown>[] }>>(`/imports/${encodeURIComponent(importBatchId)}/rows`);
}

export async function resendImportRowInvitation(importBatchId: string, rowId: string) {
  return postAdminGovernance<AdminGovernanceActionResponse>(
    `/imports/${encodeURIComponent(importBatchId)}/rows/${encodeURIComponent(rowId)}/invitations/resend`,
    {},
  );
}

export async function revokeImportRowInvitation(importBatchId: string, rowId: string) {
  return postAdminGovernance<AdminGovernanceActionResponse>(
    `/imports/${encodeURIComponent(importBatchId)}/rows/${encodeURIComponent(rowId)}/invitations/revoke`,
    {},
  );
}

export async function getImportTemplate(importType: string) {
  return getAdminGovernance<LookupEnvelope>(`/imports/templates/${encodeURIComponent(importType)}`);
}

export const IMPORT_TYPES = [
  "organisation_users",
  "hsc_employment_records",
  "council_professional_register",
  "facility_staff_list",
  "madi_blood_service_users",
  "marketplace_organisation_users",
  "payer_users",
  "external_partner_users",
] as const;

export function parseCsvPreview(content: string): Array<Record<string, string>> {
  const lines = content.replace(/\r/g, "").split("\n").filter(Boolean);
  if (lines.length < 2) return [];
  const headers = lines[0].split(",").map((h) => h.trim().toLowerCase());
  return lines.slice(1).map((line) => {
    const values = line.split(",");
    const row: Record<string, string> = {};
    headers.forEach((header, index) => {
      row[header] = (values[index] ?? "").trim();
    });
    return row;
  });
}

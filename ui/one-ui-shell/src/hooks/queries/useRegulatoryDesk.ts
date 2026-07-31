/**
 * Org-scoped regulatory desk reads — restrictions list and configuration audit.
 */

import { useQuery } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";

export interface RegisterEntryRestrictionRow {
  id: number | null;
  restrictionType: string | null;
  description: string | null;
  status: string | null;
  validFrom: string | null;
  validTo: string | null;
  imposedBy: string | null;
  decisionRef: string | null;
  registrationRecordId: number | null;
  registrationNumber: string | null;
  providerId: number | null;
  registerCode: string | null;
  registerName: string | null;
}

export interface RestrictionsCatalogue {
  organizationId: string;
  linked: boolean;
  councilId: number | null;
  councilCode: string | null;
  restrictions: RegisterEntryRestrictionRow[];
  note: string | null;
}

export interface ConfigAuditEventRow {
  id: string | null;
  eventType: string | null;
  subjectType: string | null;
  subjectId: string | null;
  actorHealthId: string | null;
  actorRole: string | null;
  occurredAt: string | null;
  correlationId: string | null;
}

export interface OrgAuditCatalogue {
  organizationId: string;
  events: ConfigAuditEventRow[];
  scope: string;
  note: string | null;
}

const base = (organizationId: string) =>
  `/api/v1/regulatory/organizations/${encodeURIComponent(organizationId)}`;

export function useRegisterEntryRestrictions(organizationId: string | undefined) {
  return useQuery<RestrictionsCatalogue>({
    queryKey: ["regulatory-restrictions", organizationId],
    enabled: Boolean(organizationId),
    queryFn: async () => apiClient.get<RestrictionsCatalogue>(`${base(String(organizationId))}/restrictions`),
  });
}

export function useRegulatoryAuditEvents(organizationId: string | undefined) {
  return useQuery<OrgAuditCatalogue>({
    queryKey: ["regulatory-audit-events", organizationId],
    enabled: Boolean(organizationId),
    queryFn: async () =>
      apiClient.get<OrgAuditCatalogue>(`${base(String(organizationId))}/audit-events`),
  });
}

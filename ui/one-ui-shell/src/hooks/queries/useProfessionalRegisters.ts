/**
 * Operator view of a council's materialised professional registers (NCZ task #97).
 *
 * Types mirror the BFF's composed response — not the varapi entity serialisation — so provenance
 * fields stay stable when the entity gains columns.
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";

export type RegisterSource = "CONFIG_PACK" | "MIGRATION_SEED" | string;
export type RegisterStatus = "ACTIVE" | "RETIRED" | string;

export interface ProfessionalRegisterRow {
  id: number | null;
  registerCode: string;
  name: string;
  description: string | null;
  status: RegisterStatus;
  registerAxis: string | null;
  statutoryTitle: string | null;
  statutoryTitleDecisionRef: string | null;
  source: RegisterSource;
  configDefinitionKey: string | null;
  configSemanticVersion: string | null;
  configContentHash: string | null;
  materialisedAt: string | null;
  retiredAt: string | null;
}

export interface ProfessionalRegisterCatalogue {
  organizationId: string;
  linked: boolean;
  councilId: number | null;
  councilCode: string | null;
  councilName: string | null;
  note: string | null;
  registers: ProfessionalRegisterRow[];
  summary: {
    total: number;
    fromConfiguration: number;
    fromMigrationSeed: number;
    retired: number;
  };
}

export interface RegisterReconcileReport {
  organizationId: string;
  linked: boolean;
  outcome: string;
  councilCode: string | null;
  declared: number;
  created: number;
  updated: number;
  unchanged: number;
  retired: number;
  reinstated: number;
  note: string | null;
  notes: string[];
}

const base = (organizationId: string) =>
  `/api/v1/regulatory/organizations/${encodeURIComponent(organizationId)}/registers`;

export function useProfessionalRegisters(organizationId: string | undefined) {
  return useQuery<ProfessionalRegisterCatalogue>({
    queryKey: ["professional-registers", organizationId],
    enabled: Boolean(organizationId),
    queryFn: async () =>
      apiClient.get<ProfessionalRegisterCatalogue>(base(String(organizationId))),
  });
}

export function useReconcileProfessionalRegisters(organizationId: string | undefined) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async () =>
      apiClient.post<RegisterReconcileReport>(`${base(String(organizationId))}/reconcile`),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["professional-registers", organizationId] });
    },
  });
}

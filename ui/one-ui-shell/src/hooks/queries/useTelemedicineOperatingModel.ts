/**
 * Telemedicine operating-model registry hooks — reads the governed
 * doctrine-as-data documents the experience-bff serves from
 * `TelemedicineOperatingModelRegistry`:
 *
 *   GET /internal/v1/telemedicine/operating-model/virtual-hospitals
 *   GET /internal/v1/telemedicine/operating-model/virtual-hospitals/{id}
 *   GET /internal/v1/telemedicine/operating-model/clinical-groups
 *
 * Read-only consumers: the registry is versioned configuration (no tenant
 * data), the BFF resources being the canonical serialization of the TS seed
 * spec in `@/lib/telemedicine` (pinned by a drift test there).
 */

import { useQuery } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";
import type { ClinicalGroupTypeDescriptor } from "@/lib/telemedicine/clinical-groups";
import type { VirtualHospitalDefinition } from "@/lib/telemedicine/virtual-hospitals";

/** BFF registry envelope: `{ data: { type, attributes }, meta }`. */
interface RegistryEnvelope<T> {
  data?: { type?: string; attributes?: T };
}

function unwrap<T>(res: unknown): T | null {
  return (res as RegistryEnvelope<T> | null)?.data?.attributes ?? null;
}

/** Full governed virtual-hospital directory (strategic + provincial). */
export function useVirtualHospitalDirectory() {
  return useQuery({
    queryKey: ["telemedicine-operating-model", "virtual-hospitals"],
    staleTime: Infinity,
    retry: false,
    queryFn: async (): Promise<VirtualHospitalDefinition[]> => {
      const res = await apiClient.get<unknown>(
        "/internal/v1/telemedicine/operating-model/virtual-hospitals",
      );
      const attributes = unwrap<{ virtualHospitals?: VirtualHospitalDefinition[] }>(res);
      return attributes?.virtualHospitals ?? [];
    },
  });
}

/** One institution by Virtual Hospital ID (null when not configured — honest 404). */
export function useVirtualHospitalDetail(id: string) {
  return useQuery({
    queryKey: ["telemedicine-operating-model", "virtual-hospital", id],
    enabled: !!id,
    staleTime: Infinity,
    retry: false,
    queryFn: async (): Promise<VirtualHospitalDefinition | null> => {
      try {
        const res = await apiClient.get<unknown>(
          `/internal/v1/telemedicine/operating-model/virtual-hospitals/${encodeURIComponent(id)}`,
        );
        return unwrap<VirtualHospitalDefinition>(res);
      } catch {
        return null;
      }
    },
  });
}

export interface ClinicalGroupTaxonomy {
  /** Group creation stays fail-closed until the HO-5 governed backend exists. */
  creationBlocked: boolean;
  creationBlockedReason: string;
  governanceRequirements: string[];
  groupTypes: ClinicalGroupTypeDescriptor[];
}

/** Clinical-group taxonomy + the fail-closed creation governance (HO-5). */
export function useClinicalGroupTaxonomy() {
  return useQuery({
    queryKey: ["telemedicine-operating-model", "clinical-groups"],
    staleTime: Infinity,
    retry: false,
    queryFn: async (): Promise<ClinicalGroupTaxonomy | null> => {
      const res = await apiClient.get<unknown>(
        "/internal/v1/telemedicine/operating-model/clinical-groups",
      );
      return unwrap<ClinicalGroupTaxonomy>(res);
    },
  });
}

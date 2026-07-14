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

/** Directory payload with registry provenance (TUSO-backed since Lane E W2). */
export interface VirtualHospitalDirectoryPayload {
  virtualHospitals: VirtualHospitalDefinition[];
  /** Where the directory came from: the TUSO registry or the static doctrine fallback. */
  source: "TUSO" | "STATIC_FALLBACK";
  /** True when TUSO was unreachable and a cached/static document is being served. */
  registryDegraded: boolean;
}

/**
 * Full governed virtual-hospital directory (strategic + provincial), with
 * source + degradation provenance. Registry data is TUSO-truth; the BFF falls
 * back to cached-last-good, then the static doctrine document.
 */
export function useVirtualHospitals() {
  return useQuery({
    queryKey: ["telemedicine-operating-model", "virtual-hospitals"],
    staleTime: 60_000,
    retry: false,
    queryFn: async (): Promise<VirtualHospitalDirectoryPayload> => {
      const res = await apiClient.get<unknown>(
        "/internal/v1/telemedicine/operating-model/virtual-hospitals",
      );
      const attributes = unwrap<{
        virtualHospitals?: VirtualHospitalDefinition[];
        source?: "TUSO" | "STATIC_FALLBACK";
        registryDegraded?: boolean;
      }>(res);
      return {
        virtualHospitals: attributes?.virtualHospitals ?? [],
        source: attributes?.source ?? "STATIC_FALLBACK",
        registryDegraded: attributes?.registryDegraded ?? false,
      };
    },
  });
}

/** Directory list only (back-compat shape for existing pages). */
export function useVirtualHospitalDirectory() {
  const query = useVirtualHospitals();
  return { ...query, data: query.data?.virtualHospitals };
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

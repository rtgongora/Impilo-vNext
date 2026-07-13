/**
 * Registrar-facing certificate lifecycle for a provider (VARAPI CertificateController via BFF).
 * Distinct from useMyCertificates (self-service portal) — this keys on the numeric registry id.
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";

export interface RegistryCertificate {
  id: number;
  certificateType?: string;
  certificateNumber?: string;
  status?: string;
  issueDate?: string;
  expiryDate?: string;
  issuedUnderAuthority?: string;
  [key: string]: unknown;
}

interface ListResponse {
  data: RegistryCertificate[] | { attributes?: RegistryCertificate[] };
}

function normalize(payload: ListResponse["data"]): RegistryCertificate[] {
  if (Array.isArray(payload)) return payload;
  if (payload && "attributes" in payload && Array.isArray(payload.attributes)) return payload.attributes;
  return [];
}

const BASE = "/internal/v1/registry/provider-certificates";

export function useProviderCertificatesAdmin(providerPublicId?: string) {
  return useQuery<RegistryCertificate[]>({
    queryKey: ["registry-provider-certificates", providerPublicId ?? null],
    queryFn: async () => {
      const res = await apiClient.get<ListResponse>(`${BASE}/provider/${encodeURIComponent(providerPublicId!)}`);
      return normalize(res.data);
    },
    enabled: !!providerPublicId,
    staleTime: 30_000,
  });
}

export function useCreateCertificate(providerPublicId?: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: Record<string, unknown>) => apiClient.post(BASE, body),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["registry-provider-certificates", providerPublicId ?? null] }),
  });
}

/** action ∈ issue | renew | suspend | reinstate | revoke. suspend/revoke require a reason. */
export function useCertificateAction(providerPublicId?: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ certificateId, action, reason }: { certificateId: number; action: string; reason?: string }) =>
      apiClient.post(`${BASE}/${certificateId}/${action}`, reason ? { reason } : {}),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["registry-provider-certificates", providerPublicId ?? null] }),
  });
}

/**
 * Self-service: the authenticated provider's own certificates (VARAPI portal via BFF).
 */

import { useQuery } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";
import { useAuthStore } from "@/hooks/useAuthStore";

export interface MyCertificate {
  id: number;
  licenseType?: string;
  licenseNumber?: string;
  status?: string;
  validFrom?: string;
  validTo?: string;
  councilName?: string;
  issuedAt?: string;
}

interface CertificatesResponse {
  data: MyCertificate[] | { attributes?: MyCertificate[] };
}

function normalize(payload: CertificatesResponse["data"]): MyCertificate[] {
  if (Array.isArray(payload)) return payload;
  if (payload && "attributes" in payload && Array.isArray(payload.attributes)) return payload.attributes;
  return [];
}

export function useMyCertificates() {
  const { user, isAuthenticated } = useAuthStore();
  return useQuery<MyCertificate[]>({
    queryKey: ["identity-certificates", user?.id],
    queryFn: async () => {
      const res = await apiClient.get<CertificatesResponse>("/internal/v1/identity/certificates");
      return normalize(res.data);
    },
    enabled: isAuthenticated && !!user?.id,
    staleTime: 5 * 60 * 1000,
    refetchOnWindowFocus: false,
  });
}

/** Downloadable statuses — VARAPI also status-gates server-side, this just hides the button. */
export function isCertificateDownloadable(cert: MyCertificate): boolean {
  const status = String(cert.status ?? "").toUpperCase();
  if (status === "SUSPENDED" || status === "REVOKED") return false;
  if (cert.validTo && new Date(cert.validTo) < new Date()) return false;
  return true;
}

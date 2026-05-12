import { useQuery } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

export interface QrResolution {
  healthId: string;
  cpid: string;
  givenName?: string;
  familyName?: string;
  validUntil?: string;
}

export interface QrPublicKey {
  kty: string;
  kid?: string;
  use?: string;
  alg?: string;
  n?: string;
  e?: string;
  [key: string]: unknown;
}

/**
 * Resolves a QR token to a Health ID.
 * Enabled only when token is provided.
 */
export function useResolveQr(token: string | undefined) {
  return useQuery({
    queryKey: ["vito", "qr", "resolve", token],
    queryFn: () =>
      apiClient.get<ApiResponse<QrResolution>>(
        `/internal/v1/vito/qr/resolve/${encodeURIComponent(token!)}`
      ),
    enabled: !!token,
  });
}

/**
 * Retrieves the public key used to verify QR tokens.
 */
export function useQrPublicKey() {
  return useQuery({
    queryKey: ["vito", "qr", "public-key"],
    queryFn: () =>
      apiClient.get<ApiResponse<QrPublicKey>>("/internal/v1/vito/qr/public-key"),
  });
}

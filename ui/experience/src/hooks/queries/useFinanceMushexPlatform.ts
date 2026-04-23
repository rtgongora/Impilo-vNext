/**
 * MusheX custodial platform via Experience BFF mushex-platform proxy.
 */

import { useQuery } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";

export type FinanceMushexJson = unknown;

export function useFinanceMushexWallets(ownerRef: string, enabled: boolean) {
  const q = new URLSearchParams();
  if (ownerRef) q.set("ownerRef", ownerRef);
  const suffix = q.toString();

  return useQuery<FinanceMushexJson>({
    queryKey: ["finance", "mushex-platform", "wallets", ownerRef],
    queryFn: () =>
      apiClient.get<FinanceMushexJson>(
        `/internal/v1/finance/mushex-platform/wallets${suffix ? `?${suffix}` : ""}`,
      ),
    enabled: enabled && ownerRef.trim().length > 0,
  });
}

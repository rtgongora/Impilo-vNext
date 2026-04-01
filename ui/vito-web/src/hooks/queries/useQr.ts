import { useMutation } from "@tanstack/react-query";
import { vitoApiClient } from "@/lib/apiClient";
import type { QrResolveResponse } from "@/types/vito";

export const qrKeys = {
  all: ["qr"] as const,
};

export function useResolveQr() {
  return useMutation({
    mutationFn: (token: string) =>
      vitoApiClient.post<QrResolveResponse>("/v1/qr/resolve", { token }),
  });
}

export async function downloadEmergencyCapsule(healthId: string): Promise<void> {
  const blob = await vitoApiClient.blob(`/v1/slips/emergency-capsule/${healthId}`);
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = `emergency-capsule-${healthId}.pdf`;
  document.body.appendChild(a);
  a.click();
  a.remove();
  URL.revokeObjectURL(url);
}

export async function downloadPickupSlip(pickupId: string): Promise<void> {
  const blob = await vitoApiClient.blob(`/v1/slips/pickup/${pickupId}`);
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = `pickup-slip-${pickupId}.pdf`;
  document.body.appendChild(a);
  a.click();
  a.remove();
  URL.revokeObjectURL(url);
}

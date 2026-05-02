import { useMutation, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";

export interface PrintJobRequest {
  cardId: number;
  template?: string;
}

export interface PrintJobResult {
  cardId: number;
  cardNumber: string;
  status: string;
  message: string;
}

export interface PickupVerifyRequest {
  pickupToken: string;
  delegateOtp: string;
}

export interface PickupVerifyResult {
  valid: boolean;
  delegateName: string;
  facilityName: string;
  cardId: string;
  expiresAt: string;
}

export interface ConfirmHandoverRequest {
  pickupToken: string;
  delegateOtp: string;
}

export interface ConfirmHandoverResult {
  confirmed: boolean;
  confirmedAt: string;
  cardId: string;
  message: string;
}

/**
 * Vito Printing Hooks — Health OS §1 (Identity & Trust Services)
 *
 * Wraps the Experience BFF vito/print endpoints.
 */
export function useSubmitPrintJob() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (body: PrintJobRequest) =>
      apiClient.post<PrintJobResult>("/internal/v1/vito/print/card/job", body),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["vito", "print"] });
    },
  });
}

// TODO: BFF routes /internal/v1/vito/print/card/verify-pickup and
//       /internal/v1/vito/print/card/confirm-handover are wired in VitoPrintBffController
//       but VITO (PrintJobController) does not yet implement the upstream endpoints.
//       These hooks will work end-to-end once vito-service adds the corresponding routes.

export function useVerifyPickupToken() {
  return useMutation({
    mutationFn: (body: PickupVerifyRequest) =>
      apiClient.post<PickupVerifyResult>("/internal/v1/vito/print/card/verify-pickup", body),
  });
}

export function useConfirmHandover() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (body: ConfirmHandoverRequest) =>
      apiClient.post<ConfirmHandoverResult>("/internal/v1/vito/print/card/confirm-handover", body),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["vito", "cards"] });
    },
  });
}

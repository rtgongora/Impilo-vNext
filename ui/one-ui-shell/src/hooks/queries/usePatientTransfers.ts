/**
 * Patient transfers — CareEmergencyInpatientController.
 * GET    /internal/v1/transfers?patientId=
 * POST   /internal/v1/transfers
 * POST   /internal/v1/transfers/{id}/accept
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";

export type PatientTransferRow = Record<string, unknown> & {
  id?: string;
  status?: string;
  reason?: string;
  transfer_type?: string;
  clinical_notes?: string | null;
  requested_by?: string | null;
  requested_at?: string;
  accepted_by?: string | null;
  completed_at?: string | null;
};

type TransfersListResponse = { data: PatientTransferRow[] };

export function usePatientTransfers(patientId: string | undefined) {
  return useQuery<TransfersListResponse>({
    queryKey: ["patient-transfers", patientId],
    queryFn: () =>
      apiClient.get<TransfersListResponse>(
        `/internal/v1/transfers?patientId=${encodeURIComponent(patientId!)}`,
      ),
    enabled: Boolean(patientId),
    staleTime: 25_000,
  });
}

export function useRequestPatientTransfer() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: {
      patientId: string;
      reason?: string;
      clinicalNotes?: string;
      transferType?: string;
      requestedBy: string;
    }) =>
      apiClient.post<{ data: { id: string; status: string } }>("/internal/v1/transfers", {
        patientId: body.patientId,
        reason: body.reason ?? "CLINICAL",
        clinicalNotes: body.clinicalNotes ?? "",
        transferType: body.transferType ?? "INTERNAL",
        requestedBy: body.requestedBy,
      }),
    onSuccess: (_, v) => {
      queryClient.invalidateQueries({ queryKey: ["patient-transfers", v.patientId] });
    },
  });
}

export function useAcceptPatientTransfer() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload: { id: string; patientId: string; acceptedBy: string }) =>
      apiClient.post<{ status: string }>(`/internal/v1/transfers/${payload.id}/accept`, {
        acceptedBy: payload.acceptedBy,
      }),
    onSuccess: (_, v) => {
      queryClient.invalidateQueries({ queryKey: ["patient-transfers", v.patientId] });
    },
  });
}

/**
 * Experience UI — Workforce Intake Query Hooks
 *
 * Staged MoHCC staff bootstrap journey against the experience BFF at
 * /internal/v1/workforce-intake (upload → mapping/validation → match/dedupe
 * preview → approve → execute → completion tracker).
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

const BASE = "/internal/v1/workforce-intake";

export interface IntakeEnvelope<T = Record<string, unknown>> {
  type: string;
  attributes: {
    integrationStatus?: string;
    friendlyMessage?: string | null;
    data?: T;
    // ActionResponse fields (approve step)
    status?: string;
    friendlyTitle?: string;
    blockedReason?: string;
    payload?: Record<string, unknown>;
    [key: string]: unknown;
  };
}

export interface IntakeBatch {
  id: string;
  stage?: string;
  status?: string;
  rowCount?: number;
  validRowCount?: number;
  exceptionCount?: number;
  [key: string]: unknown;
}

export interface IntakeValidationRow {
  rowId: string;
  rowNumber: number;
  validationStatus: string;
  outcome: string;
  payload: Record<string, string>;
}

export interface IntakeValidationReport {
  totalRows: number;
  validRows: number;
  invalidRows: number;
  rows: IntakeValidationRow[];
  exceptions: Array<Record<string, unknown>>;
}

export interface IntakeMatchRow {
  rowId: string;
  rowNumber: number;
  payload: Record<string, string>;
  matchStatus: string;
  matchedHealthId?: string;
  duplicateOfRowId?: string;
}

export interface IntakeMatchSummary {
  matched: number;
  noMatch: number;
  ambiguous: number;
  duplicatesInBatch: number;
  invalid: number;
}

export interface IntakeStatusRow {
  rowId: string;
  rowNumber: number;
  payload: Record<string, string>;
  validationStatus: string;
  matchStatus: string;
  matchedHealthId?: string | null;
  providerPublicId?: string | null;
  vashandiProfileId?: string | null;
  assignmentId?: string | null;
  executionStatus: string;
  executionError?: string | null;
  invitationId?: string | null;
}

export interface IntakeStatus {
  batch: IntakeBatch;
  stage: string;
  executionTally: Record<string, number>;
  matchTally: Record<string, number>;
  rows: IntakeStatusRow[];
}

type Envelope = ApiResponse<IntakeEnvelope>;

export function useWorkforceIntakeTemplate() {
  return useQuery<Envelope>({
    queryKey: ["workforce-intake", "template"],
    queryFn: () => apiClient.get<Envelope>(`${BASE}/template`),
  });
}

export function useWorkforceIntakeStatus(batchId: string | null, pollWhileExecuting = false) {
  return useQuery<Envelope>({
    queryKey: ["workforce-intake", "status", batchId],
    queryFn: () => apiClient.get<Envelope>(`${BASE}/batches/${batchId}/status`),
    enabled: !!batchId,
    refetchInterval: pollWhileExecuting ? 4000 : false,
  });
}

export function useCreateIntakeBatch() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: { organisationId: string; fileName: string; csvContent: string }) =>
      apiClient.post<Envelope>(`${BASE}/batches`, body),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["workforce-intake"] }),
  });
}

export function useApplyIntakeMapping(batchId: string | null) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (columnMapping: Record<string, string>) =>
      apiClient.post<Envelope>(`${BASE}/batches/${batchId}/mapping`, { columnMapping }),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: ["workforce-intake", "status", batchId] }),
  });
}

export function useRunIntakeMatch(batchId: string | null) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => apiClient.post<Envelope>(`${BASE}/batches/${batchId}/match`, {}),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: ["workforce-intake", "status", batchId] }),
  });
}

export function useApproveIntakeBatch(batchId: string | null) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => apiClient.post<Envelope>(`${BASE}/batches/${batchId}/approve`, {}),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: ["workforce-intake", "status", batchId] }),
  });
}

export function useExecuteIntakeBatch(batchId: string | null) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => apiClient.post<Envelope>(`${BASE}/batches/${batchId}/execute`, {}),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: ["workforce-intake", "status", batchId] }),
  });
}

/** Unwraps the BFF envelope's attributes, tolerating either Lookup or Action shapes. */
export function intakeAttributes(response?: Envelope) {
  return response?.data?.attributes;
}

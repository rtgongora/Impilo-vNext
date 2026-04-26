/**
 * Patient-facing financials — TanStack Query hooks (Experience BFF → COSTA).
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

type UnknownRecord = Record<string, unknown>;

function unwrapData<T>(res: unknown): T | undefined {
  if (res == null) return undefined;
  if (typeof res !== "object") return res as T;
  const o = res as { data?: T };
  if ("data" in o) return o.data;
  return res as T;
}

function withQuery(path: string, params: Record<string, string | number | undefined | null>) {
  const search = new URLSearchParams();
  for (const [k, v] of Object.entries(params)) {
    if (v === undefined || v === null || v === "") continue;
    search.set(k, String(v));
  }
  const qs = search.toString();
  return `${path}${qs ? `?${qs}` : ""}`;
}

export function usePatientAccount(cpid?: string | null) {
  return useQuery({
    queryKey: ["patient-finance-account", cpid ?? null],
    queryFn: async () => {
      const res = await apiClient.get<ApiResponse<unknown> | unknown>(
        `/internal/v1/finance/patient-accounts/${encodeURIComponent(String(cpid))}`,
      );
      return unwrapData(res);
    },
    enabled: Boolean(cpid),
  });
}

export function usePatientTransactions(
  cpid?: string | null,
  page = 0,
  size = 20,
  dateFrom?: string | null,
  dateTo?: string | null,
) {
  return useQuery({
    queryKey: ["patient-finance-txns", cpid ?? null, page, size, dateFrom ?? null, dateTo ?? null],
    queryFn: async () => {
      const res = await apiClient.get<ApiResponse<unknown> | unknown>(
        withQuery(`/internal/v1/finance/patient-accounts/${encodeURIComponent(String(cpid))}/transactions`, {
          page,
          size,
          dateFrom: dateFrom ?? undefined,
          dateTo: dateTo ?? undefined,
        }),
      );
      return unwrapData(res);
    },
    enabled: Boolean(cpid),
  });
}

export function usePatientOutstanding(cpid?: string | null) {
  return useQuery({
    queryKey: ["patient-finance-outstanding", cpid ?? null],
    queryFn: async () => {
      const res = await apiClient.get<ApiResponse<unknown> | unknown>(
        `/internal/v1/finance/patient-accounts/${encodeURIComponent(String(cpid))}/outstanding`,
      );
      return unwrapData(res);
    },
    enabled: Boolean(cpid),
  });
}

export function useRecordPayment(cpid: string | null | undefined) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: UnknownRecord) =>
      apiClient.post<ApiResponse<unknown>>(
        `/internal/v1/finance/patient-accounts/${encodeURIComponent(String(cpid))}/payments`,
        body,
      ),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["patient-finance-account"] });
      void queryClient.invalidateQueries({ queryKey: ["patient-finance-txns"] });
      void queryClient.invalidateQueries({ queryKey: ["patient-finance-outstanding"] });
    },
  });
}

export function usePaymentPlans(cpid?: string | null) {
  return useQuery({
    queryKey: ["patient-finance-plans", cpid ?? null],
    queryFn: async () => {
      const res = await apiClient.get<ApiResponse<unknown> | unknown>(
        withQuery("/internal/v1/finance/payment-plans", { patient_cpid: cpid ?? undefined }),
      );
      return unwrapData(res);
    },
    enabled: Boolean(cpid),
  });
}

export function useCreatePaymentPlan() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: UnknownRecord) =>
      apiClient.post<ApiResponse<unknown>>("/internal/v1/finance/payment-plans", body),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["patient-finance-plans"] });
    },
  });
}

export function usePayInstallment() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ planId, body }: { planId: string; body?: UnknownRecord }) =>
      apiClient.put<ApiResponse<unknown>>(
        `/internal/v1/finance/payment-plans/${encodeURIComponent(planId)}/pay`,
        body ?? {},
      ),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["patient-finance-plans"] });
    },
  });
}

export function useFinancialDocuments(subjectRef?: string | null, docType?: string | null, page = 0, size = 20) {
  return useQuery({
    queryKey: ["patient-finance-docs", subjectRef ?? null, docType ?? null, page, size],
    queryFn: async () => {
      const res = await apiClient.get<ApiResponse<unknown> | unknown>(
        withQuery("/internal/v1/finance/documents", {
          subject_ref: subjectRef ?? undefined,
          subject_type: "PATIENT",
          doc_type: docType ?? undefined,
          page,
          size,
        }),
      );
      return unwrapData(res);
    },
    enabled: Boolean(subjectRef),
  });
}

export function useGenerateDocument() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: UnknownRecord) =>
      apiClient.post<ApiResponse<unknown>>("/internal/v1/finance/documents/generate", body),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["patient-finance-docs"] });
    },
  });
}

export function useFinancialDocument(docId?: string | null) {
  return useQuery({
    queryKey: ["patient-finance-doc", docId ?? null],
    queryFn: async () => {
      const res = await apiClient.get<ApiResponse<unknown> | unknown>(
        `/internal/v1/finance/documents/${encodeURIComponent(String(docId))}`,
      );
      return unwrapData(res);
    },
    enabled: Boolean(docId),
  });
}

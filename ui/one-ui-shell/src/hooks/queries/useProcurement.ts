/**
 * Experience UI — Procurement (procurement-service via BFF).
 *
 * Proxies to `/internal/v1/erp/procurement/**` (`ErpProcurementBffController`).
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";

export function useProcSuppliers() {
  return useQuery({
    queryKey: ["erp-proc", "suppliers"],
    queryFn: () => apiClient.get<unknown>("/internal/v1/erp/procurement/suppliers"),
  });
}

export function useProcRequisitions() {
  return useQuery({
    queryKey: ["erp-proc", "requisitions"],
    queryFn: () => apiClient.get<unknown>("/internal/v1/erp/procurement/requisitions"),
  });
}

export function useProcPurchaseOrders() {
  return useQuery({
    queryKey: ["erp-proc", "purchase-orders"],
    queryFn: () => apiClient.get<unknown>("/internal/v1/erp/procurement/purchase-orders"),
  });
}

export function useProcGrn() {
  return useQuery({
    queryKey: ["erp-proc", "grn"],
    queryFn: () => apiClient.get<unknown>("/internal/v1/erp/procurement/grn"),
  });
}

export function useProcInvoices() {
  return useQuery({
    queryKey: ["erp-proc", "invoices"],
    queryFn: () => apiClient.get<unknown>("/internal/v1/erp/procurement/invoices"),
  });
}

export function useProcRfqs() {
  return useQuery({
    queryKey: ["erp-proc", "rfqs"],
    queryFn: () => apiClient.get<unknown>("/internal/v1/erp/procurement/rfqs"),
  });
}

export function useProcRfqResponses(rfqId?: string | null) {
  return useQuery({
    queryKey: ["erp-proc", "rfq-responses", rfqId],
    queryFn: () =>
      apiClient.get<unknown>(
        `/internal/v1/erp/procurement/rfqs/${encodeURIComponent(rfqId!)}/responses`,
      ),
    enabled: !!rfqId,
  });
}

export function useProcPostGrn() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: Record<string, unknown>) =>
      apiClient.post<unknown>("/internal/v1/erp/procurement/grn", body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["erp-proc", "grn"] });
      qc.invalidateQueries({ queryKey: ["erp-proc", "purchase-orders"] });
    },
  });
}

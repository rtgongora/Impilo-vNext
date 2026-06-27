/**
 * Experience UI — HR & payroll (hr-payroll-service via BFF).
 *
 * Proxies to `/internal/v1/erp/hr/**` (`ErpHrBffController`).
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";

function q(params: Record<string, string | number | undefined | null>): string {
  const usp = new URLSearchParams();
  for (const [k, v] of Object.entries(params)) {
    if (v === undefined || v === null || v === "") continue;
    usp.set(k, String(v));
  }
  const s = usp.toString();
  return s ? `?${s}` : "";
}

export function useHrEmployees() {
  return useQuery({
    queryKey: ["erp-hr", "employees"],
    queryFn: () => apiClient.get<unknown>("/internal/v1/erp/hr/employees"),
  });
}

export function useHrCreateEmployee() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: Record<string, unknown>) =>
      apiClient.post<unknown>("/internal/v1/erp/hr/employees", body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["erp-hr", "employees"] });
    },
  });
}

export function useHrContracts(employeeId?: string | null) {
  return useQuery({
    queryKey: ["erp-hr", "contracts", employeeId],
    queryFn: () =>
      apiClient.get<unknown>(`/internal/v1/erp/hr/contracts${q({ employee_id: employeeId })}`),
    enabled: !!employeeId,
  });
}

export function useHrLeaveTypes() {
  return useQuery({
    queryKey: ["erp-hr", "leave-types"],
    queryFn: () => apiClient.get<unknown>("/internal/v1/erp/hr/leave/types"),
  });
}

// Leave + attendance are owned by Vashandi (workforce SoR). These read Vashandi via the BFF,
// keyed by the worker's provider id (the Employee.providerId ↔ WorkforceProfile bridge).
export function useHrLeaveRequests(providerWorkerId?: string | null) {
  return useQuery({
    queryKey: ["erp-hr", "leave-requests", providerWorkerId],
    queryFn: () =>
      apiClient.get<unknown>(`/internal/v1/erp/hr/leave/requests${q({ provider_worker_id: providerWorkerId })}`),
    enabled: !!providerWorkerId,
  });
}

export function useHrAttendance(providerWorkerId?: string | null) {
  return useQuery({
    queryKey: ["erp-hr", "attendance", providerWorkerId],
    queryFn: () =>
      apiClient.get<unknown>(`/internal/v1/erp/hr/attendance${q({ provider_worker_id: providerWorkerId })}`),
    enabled: !!providerWorkerId,
  });
}

export function useHrPayrollRuns() {
  return useQuery({
    queryKey: ["erp-hr", "payroll-runs"],
    queryFn: () => apiClient.get<unknown>("/internal/v1/erp/hr/payroll/runs"),
  });
}

export function useHrPayslips(runId?: string | null) {
  return useQuery({
    queryKey: ["erp-hr", "payslips", runId],
    queryFn: () =>
      apiClient.get<unknown>(`/internal/v1/erp/hr/payroll/payslips${q({ run_id: runId })}`),
    enabled: !!runId,
  });
}

export function useHrDeductionTypes() {
  return useQuery({
    queryKey: ["erp-hr", "deductions"],
    queryFn: () => apiClient.get<unknown>("/internal/v1/erp/hr/deductions"),
  });
}

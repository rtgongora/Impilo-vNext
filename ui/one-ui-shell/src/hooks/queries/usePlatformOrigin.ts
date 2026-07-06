/**
 * Platform Origin governance query hooks (Experience → BFF → workforce-governance-service).
 *
 * Surfaces the platform-origin admin console: country operations, the two-person
 * platform-action lifecycle (initiate → approve → execute), national appointments,
 * the pending-approvals inbox, and the who-approved projection.
 *
 * BFF base: /internal/v1/platform-origin. Every response is the governance
 * {data, meta} envelope injected by the BFF PlatformOriginController; hooks read
 * the `data` field. The network helpers are exported so they can be unit-tested
 * against a mocked api-client (path + two-person approve body assertions).
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";

export const PLATFORM_ORIGIN_BASE = "/internal/v1/platform-origin";
export const PLATFORM_ORIGIN_ADMIN_ROLE = "PLATFORM_ORIGIN_ADMINISTRATOR";

/** Governance envelope: BFF wraps upstream governance data as { data, meta }. */
export interface GovernanceEnvelope<T> {
  data: T;
  meta?: { request_id?: string; correlation_id?: string };
}

export interface CountryOperation {
  id?: string;
  tenantId?: string;
  isoCountryCode?: string;
  displayName?: string;
  status?: string;
  createdByPlatformAction?: string;
  createdAt?: string;
  [key: string]: unknown;
}

export interface NationalAppointment {
  id?: string;
  countryOperationId?: string;
  subjectUserId?: string;
  role?: string;
  appointmentSource?: string;
  appointedBy?: string;
  appointedAt?: string;
  expiresAt?: string;
  status?: string;
  revocationReason?: string;
  [key: string]: unknown;
}

/** PENDING PLATFORM_ACTION access request row (initiate result / inbox item). */
export interface PlatformActionRequest {
  accessRequestId?: string;
  id?: string;
  requestType?: string;
  status?: string;
  requesterId?: string;
  approvalsRequired?: unknown;
  [key: string]: unknown;
}

/** approve() output: two-person progress projection. */
export interface PlatformActionApprovalState {
  accessRequestId: string;
  status: string;
  approvalsRecorded: number;
  approvalsSatisfied: boolean;
}

/** A single recorded approver decision (who-approved projection, A-b). */
export interface PlatformActionApprovalRow {
  approverUserId?: string;
  approverRole?: string;
  decision?: string;
  note?: string;
  decidedAt?: string;
}

export interface CreateCountryOperationBody {
  tenantId: string;
  isoCountryCode: string;
  displayName: string;
  legalFramework?: Record<string, unknown>;
  sovereignOrganisationId?: string;
}

export interface ApproveActionVars {
  accessRequestId: string;
  approverUserId: string;
  note?: string;
  /** Defaults applied for the two-person platform-action rule. */
  approverRole?: string;
  decision?: "APPROVE" | "REJECT";
}

export interface InitiateAppointmentVars {
  countryOperationId: string;
  subjectUserId: string;
  role?: string;
  appointmentSource?: string;
  expiresAt?: string;
  scope?: Record<string, unknown>;
}

export interface RevokeAppointmentVars {
  countryOperationId: string;
  appointmentId: string;
  reason?: string;
}

// ── Network helpers (exported for unit tests) ──────────────────────────────

export function fetchCountryOperations() {
  return apiClient.get<GovernanceEnvelope<CountryOperation[]>>(
    `${PLATFORM_ORIGIN_BASE}/country-operations`,
  );
}

export function fetchCountryOperation(id: string) {
  return apiClient.get<GovernanceEnvelope<{ countryOperation: CountryOperation; bootstrap?: unknown }>>(
    `${PLATFORM_ORIGIN_BASE}/country-operations/${id}`,
  );
}

export function fetchAppointments(countryOperationId: string) {
  return apiClient.get<GovernanceEnvelope<NationalAppointment[]>>(
    `${PLATFORM_ORIGIN_BASE}/country-operations/${countryOperationId}/appointments`,
  );
}

export function fetchPendingActions(status = "PENDING") {
  return apiClient.get<GovernanceEnvelope<PlatformActionRequest[]>>(
    `${PLATFORM_ORIGIN_BASE}/actions?status=${encodeURIComponent(status)}`,
  );
}

export function fetchActionApprovals(accessRequestId: string) {
  return apiClient.get<GovernanceEnvelope<PlatformActionApprovalRow[]>>(
    `${PLATFORM_ORIGIN_BASE}/actions/${accessRequestId}/approvals`,
  );
}

export function createCountryOperation(body: CreateCountryOperationBody) {
  return apiClient.post<GovernanceEnvelope<PlatformActionRequest>>(
    `${PLATFORM_ORIGIN_BASE}/country-operations`,
    body,
  );
}

export function approveAction(vars: ApproveActionVars) {
  return apiClient.post<GovernanceEnvelope<PlatformActionApprovalState>>(
    `${PLATFORM_ORIGIN_BASE}/actions/${vars.accessRequestId}/approve`,
    {
      approverUserId: vars.approverUserId,
      approverRole: vars.approverRole ?? PLATFORM_ORIGIN_ADMIN_ROLE,
      decision: vars.decision ?? "APPROVE",
      note: vars.note,
    },
  );
}

export function executeAction(accessRequestId: string) {
  return apiClient.post<GovernanceEnvelope<{ accessRequestId: string; action: string; result: unknown }>>(
    `${PLATFORM_ORIGIN_BASE}/actions/${accessRequestId}/execute`,
  );
}

export function initiateAppointment(vars: InitiateAppointmentVars) {
  const { countryOperationId, ...body } = vars;
  return apiClient.post<GovernanceEnvelope<PlatformActionRequest>>(
    `${PLATFORM_ORIGIN_BASE}/country-operations/${countryOperationId}/appointments`,
    body,
  );
}

export function revokeAppointment(vars: RevokeAppointmentVars) {
  return apiClient.delete<GovernanceEnvelope<PlatformActionRequest>>(
    `${PLATFORM_ORIGIN_BASE}/country-operations/${vars.countryOperationId}/appointments/${vars.appointmentId}`,
    { reason: vars.reason },
  );
}

// ── Query hooks ────────────────────────────────────────────────────────────

export function useCountryOperations() {
  return useQuery({
    queryKey: ["platform-origin", "country-operations"],
    queryFn: fetchCountryOperations,
  });
}

export function useCountryOperation(id: string | undefined) {
  return useQuery({
    queryKey: ["platform-origin", "country-operation", id],
    queryFn: () => fetchCountryOperation(id as string),
    enabled: Boolean(id),
  });
}

export function useAppointments(countryOperationId: string | undefined) {
  return useQuery({
    queryKey: ["platform-origin", "appointments", countryOperationId],
    queryFn: () => fetchAppointments(countryOperationId as string),
    enabled: Boolean(countryOperationId),
  });
}

export function usePendingActions(status = "PENDING") {
  return useQuery({
    queryKey: ["platform-origin", "pending-actions", status],
    queryFn: () => fetchPendingActions(status),
  });
}

export function useActionApprovals(accessRequestId: string | undefined) {
  return useQuery({
    queryKey: ["platform-origin", "action-approvals", accessRequestId],
    queryFn: () => fetchActionApprovals(accessRequestId as string),
    enabled: Boolean(accessRequestId),
  });
}

// ── Mutation hooks ─────────────────────────────────────────────────────────

export function useCreateCountryOperation() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: createCountryOperation,
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ["platform-origin", "country-operations"] });
      void qc.invalidateQueries({ queryKey: ["platform-origin", "pending-actions"] });
    },
  });
}

export function useApproveAction() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: approveAction,
    onSuccess: (_res, vars) => {
      void qc.invalidateQueries({ queryKey: ["platform-origin", "pending-actions"] });
      void qc.invalidateQueries({ queryKey: ["platform-origin", "action-approvals", vars.accessRequestId] });
    },
  });
}

export function useExecuteAction() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (accessRequestId: string) => executeAction(accessRequestId),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ["platform-origin"] });
    },
  });
}

export function useInitiateAppointment() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: initiateAppointment,
    onSuccess: (_res, vars) => {
      void qc.invalidateQueries({ queryKey: ["platform-origin", "appointments", vars.countryOperationId] });
      void qc.invalidateQueries({ queryKey: ["platform-origin", "pending-actions"] });
    },
  });
}

export function useRevokeAppointment() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: revokeAppointment,
    onSuccess: (_res, vars) => {
      void qc.invalidateQueries({ queryKey: ["platform-origin", "appointments", vars.countryOperationId] });
      void qc.invalidateQueries({ queryKey: ["platform-origin", "pending-actions"] });
    },
  });
}

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

/**
 * Khuluma comms approval-queue hooks. Backed by the policy-protected experience-bff controller
 * `/internal/v1/comms/approval-queue`, which orchestrates the Notification (templates) and Campaigns
 * services. This is the governance gate every template/campaign passes before dispatch: an operator
 * approves, rejects (with a mandatory reason), dry-runs, or cancels a queued send. The BFF reports the
 * real upstream state (including per-source health) — no fabricated queue items.
 */

const BASE = "/internal/v1/comms/approval-queue";

export interface ApprovalQueueItem {
  id: string;
  key: string | null;
  name: string | null;
  type: string | null;
  channel: string | null;
  status: string;
  updated_at: string | null;
  age_hours: number;
  sla_breach: boolean;
  priority: string;
}

export interface ApprovalQueueCounts {
  templates: number;
  campaigns: number;
  total: number;
}

export interface ApprovalQueueData {
  templates: ApprovalQueueItem[];
  campaigns: ApprovalQueueItem[];
  counts: ApprovalQueueCounts;
  source_health: Record<string, unknown>;
}

export interface DryRunResult {
  dry_run_ok: boolean;
  issues: string[];
  estimated_reachable_audience: number;
  blocked_by_policy: number;
  [key: string]: unknown;
}

const QUEUE_KEY = ["comms-approval-queue"];

export function useCommsApprovalQueue() {
  return useQuery<ApiResponse<ApprovalQueueData>>({
    queryKey: QUEUE_KEY,
    queryFn: () => apiClient.get<ApiResponse<ApprovalQueueData>>(BASE),
  });
}

export function useApproveTemplate() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (templateId: string) =>
      apiClient.post<ApiResponse<unknown>>(`${BASE}/templates/${encodeURIComponent(templateId)}/approve`),
    onSuccess: () => qc.invalidateQueries({ queryKey: QUEUE_KEY }),
  });
}

export function useRejectTemplate() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (args: { templateId: string; reason: string }) =>
      apiClient.post<ApiResponse<unknown>>(
        `${BASE}/templates/${encodeURIComponent(args.templateId)}/reject-with-reason`,
        { decision_reason: args.reason },
      ),
    onSuccess: () => qc.invalidateQueries({ queryKey: QUEUE_KEY }),
  });
}

export function useApproveCampaign() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (campaignId: string | number) =>
      apiClient.post<ApiResponse<unknown>>(`${BASE}/campaigns/${campaignId}/approve`),
    onSuccess: () => qc.invalidateQueries({ queryKey: QUEUE_KEY }),
  });
}

export function useRejectCampaign() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (args: { campaignId: string | number; reason: string }) =>
      apiClient.post<ApiResponse<unknown>>(`${BASE}/campaigns/${args.campaignId}/reject-with-reason`, {
        decision_reason: args.reason,
      }),
    onSuccess: () => qc.invalidateQueries({ queryKey: QUEUE_KEY }),
  });
}

export function useCancelCampaign() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (campaignId: string | number) =>
      apiClient.post<ApiResponse<unknown>>(`${BASE}/campaigns/${campaignId}/cancel`),
    onSuccess: () => qc.invalidateQueries({ queryKey: QUEUE_KEY }),
  });
}

export function useTemplateDryRun() {
  return useMutation({
    mutationFn: (templateId: string) =>
      apiClient.get<ApiResponse<DryRunResult>>(
        `${BASE}/templates/${encodeURIComponent(templateId)}/dry-run`,
      ),
  });
}

export function useCampaignDryRun() {
  return useMutation({
    mutationFn: (campaignId: string | number) =>
      apiClient.get<ApiResponse<DryRunResult>>(`${BASE}/campaigns/${campaignId}/dry-run`),
  });
}

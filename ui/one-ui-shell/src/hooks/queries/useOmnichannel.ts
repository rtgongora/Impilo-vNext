import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";

export interface OmnichannelCallback {
  id: string;
  channel: string;
  caller_id: string;
  caller_name?: string | null;
  reason?: string | null;
  priority: string;
  status: string;
  assigned_to?: string | null;
  scheduled_at?: string | null;
  completed_at?: string | null;
  notes?: string | null;
  created_at?: string | null;
}

export interface OmnichannelChannelConfig {
  id: string;
  channel_type: string;
  name: string;
  config?: Record<string, unknown> | null;
  is_active: boolean;
  created_at?: string | null;
  updated_at?: string | null;
}

export interface OmnichannelSmsJourney {
  id: string;
  name: string;
  trigger_event: string;
  message_template: string;
  schedule_cron?: string | null;
  is_active: boolean;
  sent_count?: number | null;
  created_at?: string | null;
}

export interface OmnichannelUssdMenu {
  id: string;
  short_code: string;
  menu_tree?: Record<string, unknown> | null;
  is_active: boolean;
  created_at?: string | null;
}

export interface OmnichannelIvrFlow {
  id: string;
  name: string;
  phone_number?: string | null;
  flow_definition?: Record<string, unknown> | null;
  is_active: boolean;
  created_at?: string | null;
}

export interface OmnichannelDisclosureRule {
  id: string;
  channel_type: string;
  data_category: string;
  disclosure_level: string;
  is_active?: boolean;
  created_at?: string | null;
}

export interface OmnichannelDashboard {
  executive?: Record<string, unknown>;
  operations?: Record<string, unknown>;
  clinical?: Record<string, unknown>;
  communications?: Record<string, unknown>;
  governance?: {
    source_health?: Record<string, string>;
    partial_data?: boolean;
    trend_window?: string;
    last_refreshed_at?: string | null;
    [key: string]: unknown;
  };
  active_campaigns?: number;
  completed_campaigns?: number;
  active_sms_journeys?: number;
  configured_channels?: number;
  pending_callbacks?: number;
  escalated_callbacks?: number;
  ussd_flows?: number;
  ivr_flows?: number;
  disclosure_rules?: number;
  source_health?: Record<string, string>;
  last_refreshed_at?: string | null;
}

export interface OmnichannelCampaign {
  id?: string | number;
  name?: string;
  status?: string;
  channel?: string;
  campaignType?: string;
  [key: string]: unknown;
}

function asCampaignRows(raw: unknown): OmnichannelCampaign[] {
  if (Array.isArray(raw)) return raw as OmnichannelCampaign[];
  if (raw && typeof raw === "object") {
    const r = raw as Record<string, unknown>;
    if (Array.isArray(r.content)) return r.content as OmnichannelCampaign[];
    if (Array.isArray(r.items)) return r.items as OmnichannelCampaign[];
  }
  return [];
}

export function useOmnichannelCampaigns() {
  return useQuery({
    queryKey: ["omni-campaigns"],
    queryFn: async () => {
      const res = await apiClient.get<{ data: unknown }>("/internal/v1/omnichannel/campaigns");
      return asCampaignRows(res.data);
    },
  });
}

export function useCreateOmnichannelCampaign() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: Record<string, unknown>) =>
      apiClient.post<{ data: unknown }>("/internal/v1/omnichannel/campaigns", body),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ["omni-campaigns"] });
      void qc.invalidateQueries({ queryKey: ["omni-dashboard"] });
    },
  });
}

export function useOmnichannelDashboard() {
  return useQuery<{ data: OmnichannelDashboard }>({
    queryKey: ["omni-dashboard"],
    queryFn: () => apiClient.get("/internal/v1/omnichannel/dashboard"),
  });
}

export function useOmnichannelCallbacks() {
  return useQuery<{ data: OmnichannelCallback[] }>({
    queryKey: ["omni-callbacks"],
    queryFn: () => apiClient.get("/internal/v1/omnichannel/callbacks"),
  });
}

export function useOmnichannelChannels() {
  return useQuery<{ data: OmnichannelChannelConfig[] }>({
    queryKey: ["omni-channels"],
    queryFn: () => apiClient.get("/internal/v1/omnichannel/channels"),
  });
}

export function useOmnichannelSmsJourneys() {
  return useQuery<{ data: OmnichannelSmsJourney[] }>({
    queryKey: ["omni-sms"],
    queryFn: () => apiClient.get("/internal/v1/omnichannel/sms-journeys"),
  });
}

export function useOmnichannelUssdMenus() {
  return useQuery<{ data: OmnichannelUssdMenu[] }>({
    queryKey: ["omni-ussd"],
    queryFn: () => apiClient.get("/internal/v1/omnichannel/ussd-menus"),
  });
}

export function useOmnichannelIvrFlows() {
  return useQuery<{ data: OmnichannelIvrFlow[] }>({
    queryKey: ["omni-ivr"],
    queryFn: () => apiClient.get("/internal/v1/omnichannel/ivr-flows"),
  });
}

export function useOmnichannelDisclosureRules() {
  return useQuery<{ data: OmnichannelDisclosureRule[] }>({
    queryKey: ["omni-disclosure"],
    queryFn: () => apiClient.get("/internal/v1/omnichannel/disclosure-rules"),
  });
}

export function useCreateOmnichannelCallback() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: Record<string, string>) => apiClient.post("/internal/v1/omnichannel/callbacks", body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["omni-callbacks"] });
    },
  });
}

export function useCompleteOmnichannelCallback() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => apiClient.post(`/internal/v1/omnichannel/callbacks/${id}/complete`, {}),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["omni-callbacks"] });
    },
  });
}

export function useCreateSmsJourney() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: Record<string, string>) => apiClient.post("/internal/v1/omnichannel/sms-journeys", body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["omni-sms"] });
    },
  });
}

export function useCreateDisclosureRule() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: Record<string, string>) => apiClient.post("/internal/v1/omnichannel/disclosure-rules", body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["omni-disclosure"] });
    },
  });
}

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

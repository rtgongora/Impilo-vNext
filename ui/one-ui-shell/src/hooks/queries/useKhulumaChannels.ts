/**
 * Khuluma channels / communities / broadcast (R4/G13). Facility & programme announcement
 * channels: discover the channels for a scope, create one, join, and broadcast an announcement
 * (khuluma gates the OWNER/MODERATOR role server-side). Routes live under the khuluma BFF surface.
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";

const BASE = "/internal/v1/khuluma";

export interface ChannelView {
  conversationId: string;
  conversationType?: string;
  scopeType?: string;
  scopeRef?: string | null;
  title?: string | null;
  topic?: string | null;
  status?: string;
  [key: string]: unknown;
}

function rows<T>(res: unknown): T[] {
  const data = (res as { data?: unknown })?.data ?? res;
  return Array.isArray(data) ? (data as T[]) : [];
}

export function useDiscoverChannels(scopeType?: string, scopeRef?: string) {
  return useQuery<ChannelView[]>({
    queryKey: ["khuluma-channels", scopeType ?? null, scopeRef ?? null],
    queryFn: async () =>
      rows<ChannelView>(
        await apiClient.get(
          `${BASE}/channels/discover?scope_type=${encodeURIComponent(scopeType ?? "")}&scope_ref=${encodeURIComponent(scopeRef ?? "")}`,
        ),
      ),
    enabled: !!scopeType && !!scopeRef,
  });
}

export function useCreateChannel() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: Record<string, unknown>) => apiClient.post(`${BASE}/channels`, body),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["khuluma-channels"] }),
  });
}

export function useJoinChannel() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ conversationId, ...body }: { conversationId: string } & Record<string, unknown>) =>
      apiClient.post(`${BASE}/channels/${encodeURIComponent(conversationId)}/join`, body),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["khuluma-channels"] }),
  });
}

export function useBroadcastChannel() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ conversationId, body }: { conversationId: string; body: string }) =>
      apiClient.post(`${BASE}/channels/${encodeURIComponent(conversationId)}/broadcast`, { body }),
    onSuccess: (_r, vars) =>
      qc.invalidateQueries({ queryKey: ["khuluma-channel-messages", vars.conversationId] }),
  });
}

export function useChannelMessages(conversationId?: string) {
  return useQuery<Record<string, unknown>[]>({
    queryKey: ["khuluma-channel-messages", conversationId ?? null],
    queryFn: async () =>
      rows<Record<string, unknown>>(
        await apiClient.get(`${BASE}/conversations/${encodeURIComponent(conversationId ?? "")}/messages`),
      ),
    enabled: !!conversationId,
  });
}

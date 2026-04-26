import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";

export interface CommunicationChannel {
  id: string;
  name: string | null;
  channel_type: string;
  participants?: string[] | string | null;
  last_message_at?: string | null;
  created_at?: string | null;
}

export interface ClinicalMessage {
  id: string;
  channel_id: string;
  sender_id: string;
  sender_name?: string | null;
  content: string;
  message_type: string;
  is_read?: boolean;
  created_at?: string | null;
}

export interface ClinicalPageRecord {
  id: string;
  sender_id: string;
  sender_name?: string | null;
  recipient_id: string;
  recipient_name?: string | null;
  urgency: string;
  message: string;
  callback_number?: string | null;
  status: string;
  sent_at?: string | null;
  read_at?: string | null;
  responded_at?: string | null;
}

interface SendPagePayload {
  senderId: string;
  senderName: string;
  recipientId: string;
  recipientName: string;
  message: string;
  urgency: string;
  callbackNumber?: string;
}

interface SendMessagePayload {
  channelId: string;
  senderId: string;
  senderName: string;
  content: string;
  messageType?: string;
}

export function useMessageChannels() {
  return useQuery<{ data: CommunicationChannel[] }>({
    queryKey: ["message-channels"],
    queryFn: () => apiClient.get("/internal/v1/communication/messages/channels"),
  });
}

export function useChannelMessages(channelId: string | null) {
  return useQuery<{ data: ClinicalMessage[] }>({
    queryKey: ["channel-messages", channelId],
    queryFn: () => apiClient.get(`/internal/v1/communication/messages/channels/${channelId}/messages`),
    enabled: !!channelId,
  });
}

export function useClinicalPages(recipientId?: string | null) {
  return useQuery<{ data: ClinicalPageRecord[] }>({
    queryKey: ["clinical-pages", recipientId ?? "all"],
    queryFn: () =>
      apiClient.get(
        recipientId
          ? `/internal/v1/communication/pages?recipientId=${encodeURIComponent(recipientId)}`
          : "/internal/v1/communication/pages",
      ),
  });
}

export function useSendClinicalPage() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: SendPagePayload) => apiClient.post("/internal/v1/communication/pages", body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["clinical-pages"] });
    },
  });
}

export function useMarkClinicalPageRead() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (pageId: string) => apiClient.post(`/internal/v1/communication/pages/${pageId}/read`, {}),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["clinical-pages"] });
    },
  });
}

export function useRespondToClinicalPage() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (pageId: string) => apiClient.post(`/internal/v1/communication/pages/${pageId}/respond`, {}),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["clinical-pages"] });
    },
  });
}

export function useSendClinicalMessage() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: SendMessagePayload) => apiClient.post("/internal/v1/communication/messages", body),
    onSuccess: (_data, variables) => {
      queryClient.invalidateQueries({ queryKey: ["channel-messages", variables.channelId] });
      queryClient.invalidateQueries({ queryKey: ["message-channels"] });
    },
  });
}

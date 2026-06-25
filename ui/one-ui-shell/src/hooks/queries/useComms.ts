"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";

/**
 * Khuluma Comms Hub data layer — conversations, messages, presence, unread, and calls over the
 * experience-bff `/internal/v1/khuluma/**` surface. Field names are camelCase (the BFF relays the
 * khuluma-service JSON unchanged). Realtime updates arrive via {@link useKhulumaRealtime}, which
 * invalidates these query keys.
 */

const BASE = "/internal/v1/khuluma";

export interface ConversationSummary {
  conversationId: string;
  type: string;
  title: string | null;
  status: string;
  lastMessagePreview: string | null;
  lastMessageAt: string | null;
  role: string;
  muted: boolean;
  unreadCount: number;
}

export interface ConversationParticipant {
  participantId: string;
  actorId: string;
  actorType: string;
  displayName: string | null;
  role: string;
  active: boolean;
  muted: boolean;
}

export interface ConversationDetail {
  conversationId: string;
  type: string;
  title: string | null;
  topic: string | null;
  status: string;
  createdBy: string;
  lastMessagePreview: string | null;
  lastMessageAt: string | null;
  participants: ConversationParticipant[];
  links: Array<{ linkId: string; objectType: string; objectId: string; linkRole: string | null }>;
}

export interface Message {
  messageId: string;
  conversationId: string;
  senderId: string;
  senderType: string;
  senderDisplayName: string | null;
  contentType: string;
  body: string;
  status: string;
  clientMessageId: string | null;
  sentAt: string | null;
}

export interface Presence {
  actorId: string;
  status: string;
  statusMessage: string | null;
  lastSeenAt: string | null;
}

export interface CallResponse {
  callId: string;
  conversationId: string | null;
  callType: string;
  status: string;
  initiatedBy: string;
  roomName: string | null;
  roomUrl: string | null;
  provider: string | null;
  mediaAvailable: boolean;
  accessToken: string | null;
  mediaError: string | null;
  participants: Array<{ actorId: string; actorType: string; displayName: string | null; state: string }>;
}

export function commsKeys() {
  return {
    inbox: ["khuluma", "inbox"] as const,
    conversation: (id: string) => ["khuluma", "conversation", id] as const,
    messages: (id: string) => ["khuluma", "messages", id] as const,
    summary: ["khuluma", "summary"] as const,
    presence: (actorId: string) => ["khuluma", "presence", actorId] as const,
  };
}

export function useCommsInbox() {
  return useQuery<ConversationSummary[]>({
    queryKey: commsKeys().inbox,
    queryFn: () => apiClient.get(`${BASE}/conversations`),
    refetchInterval: 5000, // liveness without a realtime socket
  });
}

/**
 * Secure, BFF-authenticated poll for calls currently ringing the user. This is how the incoming-call
 * prompt appears without exposing the realtime gateway directly to the browser.
 */
export function useIncomingCalls(enabled: boolean) {
  return useQuery<CallResponse[]>({
    queryKey: ["khuluma", "incoming-calls"],
    queryFn: () => apiClient.get(`${BASE}/calls/incoming`),
    refetchInterval: 4000,
    enabled,
  });
}

export function useConversation(conversationId: string | null) {
  return useQuery<ConversationDetail>({
    queryKey: commsKeys().conversation(conversationId ?? "none"),
    queryFn: () => apiClient.get(`${BASE}/conversations/${conversationId}`),
    enabled: !!conversationId,
  });
}

export function useMessages(conversationId: string | null) {
  return useQuery<Message[]>({
    queryKey: commsKeys().messages(conversationId ?? "none"),
    queryFn: () => apiClient.get(`${BASE}/conversations/${conversationId}/messages`),
    enabled: !!conversationId,
    refetchInterval: conversationId ? 4000 : false, // live message delivery without a socket
  });
}

export function useCommsSummary() {
  return useQuery<{ messages: { unreadCount: number; conversations: number } }>({
    queryKey: commsKeys().summary,
    queryFn: () => apiClient.get(`${BASE}/summary`),
    refetchInterval: 60_000,
  });
}

export function useSendMessage(conversationId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: { body: string; contentType?: string; clientMessageId?: string }) =>
      apiClient.post<Message>(`${BASE}/conversations/${conversationId}/messages`, body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: commsKeys().messages(conversationId) });
      qc.invalidateQueries({ queryKey: commsKeys().inbox });
    },
  });
}

export function useMarkRead(conversationId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (messageId: string) =>
      apiClient.post(`${BASE}/conversations/${conversationId}/messages/read`, { messageId }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: commsKeys().inbox });
      qc.invalidateQueries({ queryKey: commsKeys().summary });
    },
  });
}

export function useCreateConversation() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: {
      type: string;
      title?: string;
      participants: Array<{ actorId: string; actorType?: string; displayName?: string; role?: string }>;
    }) => apiClient.post<ConversationDetail>(`${BASE}/conversations`, body),
    onSuccess: () => qc.invalidateQueries({ queryKey: commsKeys().inbox }),
  });
}

export function useUpdatePresence() {
  return useMutation({
    mutationFn: (body: { status: string; statusMessage?: string }) =>
      apiClient.put<Presence>(`${BASE}/presence`, body),
  });
}

export function useStartCall() {
  return useMutation({
    mutationFn: (body: {
      conversationId?: string;
      callType: string;
      displayName?: string;
      callees: Array<{ actorId: string; actorType?: string; displayName?: string }>;
    }) => apiClient.post<CallResponse>(`${BASE}/calls`, body),
  });
}

export function useCallActions() {
  return {
    accept: (callId: string) => apiClient.post<CallResponse>(`${BASE}/calls/${callId}/accept`, {}),
    decline: (callId: string) => apiClient.post<CallResponse>(`${BASE}/calls/${callId}/decline`, {}),
    end: (callId: string) => apiClient.post<CallResponse>(`${BASE}/calls/${callId}/end`, { reason: "HANGUP" }),
  };
}

export interface MeetingResponse {
  conversationId: string;
  eventId: string | null;
  mediaAvailable: boolean;
  roomUrl: string | null;
  accessToken: string | null;
  provider: string | null;
  error: string | null;
}

export function useMeetingActions() {
  return {
    create: (body: {
      title: string;
      participants: Array<{ actorId: string; actorType?: string; displayName?: string }>;
    }) => apiClient.post<MeetingResponse>(`${BASE}/meetings`, body),
    join: (conversationId: string) =>
      apiClient.post<MeetingResponse>(`${BASE}/conversations/${conversationId}/meeting/join`, {}),
    end: (conversationId: string) =>
      apiClient.post<unknown>(`${BASE}/conversations/${conversationId}/meeting/end`, {}),
    // Compose with Impilo Live: attach (idempotently) a Khuluma discussion to an existing live event.
    fromEvent: (
      eventId: string,
      participants: Array<{ actorId: string; actorType?: string; displayName?: string }> = [],
      title?: string,
    ) => apiClient.post<MeetingResponse>(`${BASE}/meetings/from-event`, { eventId, title, participants }),
    eventConversation: (eventId: string) =>
      apiClient.get<MeetingResponse>(`${BASE}/events/${eventId}/conversation`),
  };
}

/** Adapt a meeting media join into the CallResponse shape the call modal consumes. */
export function meetingToCall(meeting: MeetingResponse): CallResponse {
  return {
    callId: meeting.eventId ?? meeting.conversationId,
    conversationId: meeting.conversationId,
    callType: "VIDEO",
    status: "ACTIVE",
    initiatedBy: "",
    roomName: null,
    roomUrl: meeting.roomUrl,
    provider: meeting.provider,
    mediaAvailable: meeting.mediaAvailable,
    accessToken: meeting.accessToken,
    mediaError: meeting.error,
    participants: [],
  };
}

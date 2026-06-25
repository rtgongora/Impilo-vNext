/**
 * Khuluma Comms Hub service — the native unified comms layer for the citizen app.
 *
 * Backend: experience-bff (/internal/v1/mobile/khuluma/*) → khuluma-service. The BFF relays the
 * khuluma-service JSON unchanged (bare arrays/objects, camelCase); apiClient resolves to
 * { data: <body> }.
 */

import { apiClient } from "@impilo/mobile-api-client";

const V1 = "/internal/v1/mobile/khuluma";

export interface CommsConversationSummary {
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

export interface CommsParticipant {
  actorId: string;
  actorType: string;
  displayName: string | null;
  role: string;
  active: boolean;
}

export interface CommsConversationDetail {
  conversationId: string;
  type: string;
  title: string | null;
  topic: string | null;
  status: string;
  participants: CommsParticipant[];
}

export interface CommsMessage {
  messageId: string;
  conversationId: string;
  senderId: string;
  senderType: string;
  senderDisplayName: string | null;
  contentType: string;
  body: string;
  status: string;
  sentAt: string | null;
}

export interface CommsCall {
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
}

export async function fetchInbox(): Promise<CommsConversationSummary[]> {
  const response = await apiClient.get<CommsConversationSummary[]>(`${V1}/inbox`);
  return response.data ?? [];
}

export async function fetchConversation(id: string): Promise<CommsConversationDetail> {
  const response = await apiClient.get<CommsConversationDetail>(
    `${V1}/conversations/${encodeURIComponent(id)}`,
  );
  return response.data;
}

export async function fetchMessages(conversationId: string): Promise<CommsMessage[]> {
  const response = await apiClient.get<CommsMessage[]>(
    `${V1}/conversations/${encodeURIComponent(conversationId)}/messages`,
  );
  return response.data ?? [];
}

export async function sendMessage(conversationId: string, body: string): Promise<CommsMessage> {
  const response = await apiClient.post<CommsMessage>(
    `${V1}/conversations/${encodeURIComponent(conversationId)}/messages`,
    { body, contentType: "TEXT", clientMessageId: cryptoRandomId() },
  );
  return response.data;
}

export async function markConversationRead(conversationId: string, messageId: string): Promise<void> {
  await apiClient.post(`${V1}/conversations/${encodeURIComponent(conversationId)}/messages/read`, { messageId });
}

export async function fetchSummary(): Promise<{ messages: { unreadCount: number; conversations: number } }> {
  const response = await apiClient.get<{ messages: { unreadCount: number; conversations: number } }>(`${V1}/summary`);
  return response.data;
}

export async function fetchNotifications(): Promise<unknown[]> {
  const response = await apiClient.get<unknown[]>(`${V1}/notifications`);
  return response.data ?? [];
}

export async function updatePresence(status: string): Promise<void> {
  await apiClient.put(`${V1}/presence`, { status });
}

export async function startCall(
  conversationId: string,
  callType: "AUDIO" | "VIDEO",
  callees: Array<{ actorId: string; actorType?: string; displayName?: string }>,
): Promise<CommsCall> {
  const response = await apiClient.post<CommsCall>(`${V1}/calls`, { conversationId, callType, callees });
  return response.data;
}

/** Secure poll for calls currently ringing the user (the incoming-call sheet source). */
export async function fetchIncomingCalls(): Promise<CommsCall[]> {
  const response = await apiClient.get<CommsCall[]>(`${V1}/calls/incoming`);
  return response.data ?? [];
}

export async function acceptCall(callId: string): Promise<CommsCall> {
  const response = await apiClient.post<CommsCall>(`${V1}/calls/${encodeURIComponent(callId)}/accept`, {});
  return response.data;
}

export async function declineCall(callId: string): Promise<void> {
  await apiClient.post(`${V1}/calls/${encodeURIComponent(callId)}/decline`, {});
}

export async function endCall(callId: string): Promise<void> {
  await apiClient.post(`${V1}/calls/${encodeURIComponent(callId)}/end`, { reason: "HANGUP" });
}

export interface CommsMeeting {
  conversationId: string;
  eventId: string | null;
  mediaAvailable: boolean;
  roomUrl: string | null;
  accessToken: string | null;
  provider: string | null;
  error: string | null;
}

export async function createMeeting(
  title: string,
  participants: Array<{ actorId: string; actorType?: string; displayName?: string }>,
): Promise<CommsMeeting> {
  const response = await apiClient.post<CommsMeeting>(`${V1}/meetings`, { title, participants });
  return response.data;
}

export async function joinMeeting(conversationId: string): Promise<CommsMeeting> {
  const response = await apiClient.post<CommsMeeting>(
    `${V1}/conversations/${encodeURIComponent(conversationId)}/meeting/join`,
    {},
  );
  return response.data;
}

export async function endMeeting(conversationId: string): Promise<void> {
  await apiClient.post(`${V1}/conversations/${encodeURIComponent(conversationId)}/meeting/end`, {});
}

/** Adapt a meeting media join into the CommsCall shape the call surface renders. */
export function meetingToCall(meeting: CommsMeeting): CommsCall {
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
  };
}

function cryptoRandomId(): string {
  if (typeof globalThis.crypto?.randomUUID === "function") {
    return globalThis.crypto.randomUUID();
  }
  return `cmid-${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

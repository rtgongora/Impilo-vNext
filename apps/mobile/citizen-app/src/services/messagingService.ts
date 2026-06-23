/**
 * Messaging Service — Citizen-provider and citizen-support messaging.
 *
 * Backend: experience-bff (/internal/v1/mobile/citizen/messaging/*)
 */

import { apiClient } from "@impilo/mobile-api-client";
import type { Conversation, Message, SendMessageRequest } from "@impilo/mobile-messaging";

const V1 = "/internal/v1/mobile/citizen/messaging";

// ─── Development seed data ────────────────────────────────────────────────
function seedMsg(id: string, convId: string, senderId: string, senderName: string, body: string, sentAt: string): Message {
  return { id, conversationId: convId, senderId, senderName, body, contentType: "TEXT", sentAt, readBy: [], status: "READ" };
}

const SEED_CONVERSATIONS: Conversation[] = [
  {
    id: "conv-001",
    type: "DIRECT",
    subject: "Follow-up: Blood Pressure Management",
    participants: [{ actorId: "prov-001", actorType: "PROVIDER", displayName: "Dr. Chikwanda", role: "MEMBER" }],
    lastMessage: seedMsg("msg-003", "conv-001", "prov-001", "Dr. Chikwanda", "Please continue the medication and check in with me in two weeks.", "2026-06-12T09:15:00Z"),
    unreadCount: 1,
    createdAt: "2026-06-10T08:00:00Z",
    updatedAt: "2026-06-12T09:15:00Z",
  },
  {
    id: "conv-002",
    type: "DIRECT",
    subject: "Lab Results Review",
    participants: [{ actorId: "prov-002", actorType: "PROVIDER", displayName: "Dr. Moyo", role: "MEMBER" }],
    lastMessage: seedMsg("msg-007", "conv-002", "citizen-001", "You", "Thank you, I will pick up the referral letter tomorrow.", "2026-06-11T14:30:00Z"),
    unreadCount: 0,
    createdAt: "2026-06-09T10:00:00Z",
    updatedAt: "2026-06-11T14:30:00Z",
  },
  {
    id: "conv-003",
    type: "SUPPORT",
    subject: "Help with appointment booking",
    participants: [{ actorId: "support-001", actorType: "SUPPORT", displayName: "Impilo Support", role: "MEMBER" }],
    lastMessage: seedMsg("msg-010", "conv-003", "support-001", "Impilo Support", "Your appointment has been confirmed. You will receive a reminder 24 hours before.", "2026-06-10T16:00:00Z"),
    unreadCount: 0,
    createdAt: "2026-06-08T11:00:00Z",
    updatedAt: "2026-06-10T16:00:00Z",
  },
  {
    id: "conv-004",
    type: "DIRECT",
    subject: "Pre-operative Instructions",
    participants: [{ actorId: "prov-003", actorType: "PROVIDER", displayName: "Dr. Sibanda", role: "MEMBER" }],
    lastMessage: seedMsg("msg-014", "conv-004", "prov-003", "Dr. Sibanda", "Fast from midnight the night before. Bring your ID and insurance card.", "2026-06-08T11:45:00Z"),
    unreadCount: 2,
    createdAt: "2026-06-07T09:00:00Z",
    updatedAt: "2026-06-08T11:45:00Z",
  },
];

interface PagedResult<T> {
  data: T[];
  meta: { page: { number: number; size: number; total_elements: number; total_pages: number } };
}

export async function fetchConversations(params: {
  type?: string;
  page?: number;
  size?: number;
} = {}): Promise<{ items: Conversation[]; totalElements: number; hasNext: boolean }> {
  const query: string[] = [];
  if (params.type) query.push(`type=${params.type}`);
  if (params.page !== undefined) query.push(`page=${params.page}`);
  if (params.size !== undefined) query.push(`size=${params.size}`);
  const qs = query.length > 0 ? `?${query.join("&")}` : "";

  try {
    const response = await apiClient.get<PagedResult<Conversation>>(`${V1}/conversations${qs}`);
    const result = response.data;
    return {
      items: result.data,
      totalElements: result.meta.page.total_elements,
      hasNext: result.meta.page.number < result.meta.page.total_pages - 1,
    };
  } catch {
    const items = params.type
      ? SEED_CONVERSATIONS.filter((c) => c.type === params.type)
      : SEED_CONVERSATIONS;
    return { items, totalElements: items.length, hasNext: false };
  }
}

export async function fetchConversation(id: string): Promise<Conversation> {
  const response = await apiClient.get<{ data: Conversation }>(
    `${V1}/conversations/${encodeURIComponent(id)}`
  );
  return response.data.data;
}

export async function createConversation(params: {
  recipientId: string;
  subject?: string;
  type: "DIRECT" | "SUPPORT";
  initialMessage: string;
}): Promise<Conversation> {
  const response = await apiClient.post<{ data: Conversation }>(`${V1}/conversations`, params);
  return response.data.data;
}

export async function fetchMessages(
  conversationId: string,
  params: { page?: number; size?: number } = {}
): Promise<{ items: Message[]; totalElements: number; hasNext: boolean }> {
  const query: string[] = [];
  if (params.page !== undefined) query.push(`page=${params.page}`);
  if (params.size !== undefined) query.push(`size=${params.size}`);
  const qs = query.length > 0 ? `?${query.join("&")}` : "";

  const response = await apiClient.get<PagedResult<Message>>(
    `${V1}/conversations/${encodeURIComponent(conversationId)}/messages${qs}`
  );
  const result = response.data;
  return {
    items: result.data,
    totalElements: result.meta.page.total_elements,
    hasNext: result.meta.page.number < result.meta.page.total_pages - 1,
  };
}

export async function sendMessage(
  conversationId: string,
  body: string,
  replyTo?: string
): Promise<Message> {
  const response = await apiClient.post<{ data: Message }>(
    `${V1}/conversations/${encodeURIComponent(conversationId)}/messages`,
    { body, replyTo }
  );
  return response.data.data;
}

export async function markConversationRead(conversationId: string): Promise<void> {
  await apiClient.post(`${V1}/conversations/${encodeURIComponent(conversationId)}/read`);
}

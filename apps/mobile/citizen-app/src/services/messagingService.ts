/**
 * Messaging Service — Citizen-provider and citizen-support messaging.
 *
 * Backend: experience-bff (/internal/v1/mobile/citizen/messaging/*)
 */

import { apiClient } from "@impilo/mobile-api-client";
import type { Conversation, Message, SendMessageRequest } from "@impilo/mobile-messaging";

const V1 = "/internal/v1/mobile/citizen/messaging";

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

  const response = await apiClient.get<PagedResult<Conversation>>(`${V1}/conversations${qs}`);
  const result = response.data;
  return {
    items: result.data,
    totalElements: result.meta.page.total_elements,
    hasNext: result.meta.page.number < result.meta.page.total_pages - 1,
  };
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

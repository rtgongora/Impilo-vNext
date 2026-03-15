/**
 * Conversation Service — Secure messaging between participants.
 *
 * Backend: channels-service (/internal/v1/channels/messages/*)
 */

import { apiClient } from "@impilo/mobile-api-client";
import type { PagedResponse } from "@impilo/mobile-trust";
import type { Conversation, Message, SendMessageRequest } from "./types";

const V1 = "/internal/v1/channels/messages";

/**
 * Fetch all conversations for the current user.
 */
export async function fetchConversations(
  params: { page?: number; size?: number } = {}
): Promise<PagedResponse<Conversation>> {
  const query: string[] = [];
  if (params.page !== undefined) query.push(`page=${params.page}`);
  if (params.size !== undefined) query.push(`size=${params.size}`);
  const qs = query.length > 0 ? `?${query.join("&")}` : "";
  const response = await apiClient.get<PagedResponse<Conversation>>(`${V1}/conversations${qs}`);
  return response.data;
}

/**
 * Fetch a single conversation by ID.
 */
export async function fetchConversation(conversationId: string): Promise<Conversation> {
  const response = await apiClient.get<Conversation>(
    `${V1}/conversations/${encodeURIComponent(conversationId)}`
  );
  return response.data;
}

/**
 * Create a new conversation.
 */
export async function createConversation(params: {
  participantIds: string[];
  subject?: string;
  type?: "DIRECT" | "GROUP";
  initialMessage?: string;
}): Promise<Conversation> {
  const response = await apiClient.post<Conversation>(`${V1}/conversations`, params);
  return response.data;
}

/**
 * Fetch messages in a conversation with pagination.
 */
export async function fetchMessages(
  conversationId: string,
  params: { page?: number; size?: number; before?: string } = {}
): Promise<PagedResponse<Message>> {
  const query: string[] = [];
  if (params.page !== undefined) query.push(`page=${params.page}`);
  if (params.size !== undefined) query.push(`size=${params.size}`);
  if (params.before) query.push(`before=${encodeURIComponent(params.before)}`);
  const qs = query.length > 0 ? `?${query.join("&")}` : "";
  const response = await apiClient.get<PagedResponse<Message>>(
    `${V1}/conversations/${encodeURIComponent(conversationId)}/messages${qs}`
  );
  return response.data;
}

/**
 * Send a message in a conversation.
 */
export async function sendMessage(request: SendMessageRequest): Promise<Message> {
  const response = await apiClient.post<Message>(
    `${V1}/conversations/${encodeURIComponent(request.conversationId)}/messages`,
    {
      body: request.body,
      contentType: request.contentType ?? "TEXT",
      replyTo: request.replyTo,
      attachmentIds: request.attachmentIds,
    }
  );
  return response.data;
}

/**
 * Mark all messages in a conversation as read up to a given message ID.
 */
export async function markConversationRead(
  conversationId: string,
  upToMessageId: string
): Promise<void> {
  await apiClient.post(
    `${V1}/conversations/${encodeURIComponent(conversationId)}/read`,
    { upToMessageId }
  );
}

/**
 * Conversation Service — Secure messaging between participants.
 *
 * Backend: experience-bff (/internal/v1/mobile/provider/messaging/*)
 */

import { apiClient } from "@impilo/mobile-api-client";
import type { PagedResponse } from "@impilo/mobile-trust";
import type { Conversation, Message, SendMessageRequest } from "./types";

const V1 = "/internal/v1/mobile/provider/messaging";

type AnyRecord = Record<string, unknown>;

interface Envelope<T> {
  data: T;
}

export interface CommunicationDashboard {
  active_announcements: number;
  open_clinical_pages: number;
  active_threads: number;
  sent_today: number;
  failed_today: number;
  telemedicine_reminders_sent?: number;
  telemedicine_join_success_rate?: number;
  telemedicine_no_show_nudges_sent?: number;
  telemedicine_fallback_events?: number;
  telemedicine_escalation_volumes?: number;
  source_health?: Record<string, string>;
  last_refreshed_at?: string | null;
}

function asNumber(value: unknown, fallback = 0): number {
  return typeof value === "number" ? value : fallback;
}

function normalizeCommunicationDashboard(raw: unknown): CommunicationDashboard {
  const record = asRecord(raw) ?? {};
  const operations = asRecord(record.operations) ?? {};
  const communications = asRecord(record.communications) ?? {};
  const clinical = asRecord(record.clinical) ?? {};
  const governance = asRecord(record.governance) ?? {};
  const telemedicine =
    (asRecord(operations.telemedicine) as AnyRecord | null) ??
    (asRecord(communications.telemedicine_metrics) as AnyRecord | null) ??
    {};
  return {
    active_announcements: asNumber(record.active_announcements ?? communications.active_announcements),
    open_clinical_pages: asNumber(record.open_clinical_pages ?? clinical.open_clinical_pages),
    active_threads: asNumber(record.active_threads ?? operations.active_threads),
    sent_today: asNumber(record.sent_today ?? communications.sent_today ?? operations.messages_sent_today),
    failed_today: asNumber(record.failed_today ?? communications.failed_today ?? operations.delivery_failures_today),
    telemedicine_reminders_sent: asNumber(telemedicine.telemedicine_reminders_sent),
    telemedicine_join_success_rate: asNumber(
      telemedicine.join_success_rate ?? telemedicine.telemedicine_join_success_rate
    ),
    telemedicine_no_show_nudges_sent: asNumber(telemedicine.no_show_nudges_sent),
    telemedicine_fallback_events: asNumber(telemedicine.video_audio_fallback_events),
    telemedicine_escalation_volumes: asNumber(telemedicine.escalation_volumes),
    source_health:
      (asRecord(record.source_health) as Record<string, string> | null) ??
      (asRecord(governance.source_health) as Record<string, string> | null) ??
      undefined,
    last_refreshed_at:
      String(record.last_refreshed_at ?? governance.last_refreshed_at ?? "") || undefined,
  };
}

function asRecord(value: unknown): AnyRecord | null {
  return typeof value === "object" && value !== null ? (value as AnyRecord) : null;
}

function toConversation(raw: unknown): Conversation {
  const record = asRecord(raw) ?? {};
  const attributes = asRecord(record.attributes) ?? record;
  const participants = Array.isArray(attributes.participants)
    ? attributes.participants
    : Array.isArray(attributes.participant_ids)
      ? (attributes.participant_ids as string[]).map((id) => ({
          actorId: id,
          actorType: "PROVIDER",
          displayName: id,
          role: "MEMBER" as const,
        }))
      : [];

  const subject = String(attributes.subject ?? attributes.title ?? "");
  const createdAt = String(attributes.created_at ?? attributes.createdAt ?? new Date().toISOString());
  const updatedAt = String(attributes.updated_at ?? attributes.updatedAt ?? createdAt);
  const unreadCount = Number(attributes.unreadCount ?? attributes.unread_count ?? 0);
  const typeRaw = String(attributes.conversation_type ?? attributes.sessionType ?? attributes.type ?? "DIRECT");
  const normalizedType =
    typeRaw === "GROUP" || typeRaw === "SUPPORT" || typeRaw === "SYSTEM" ? typeRaw : "DIRECT";

  return {
    id: String(record.id ?? attributes.id ?? ""),
    type: normalizedType,
    participants: participants as Conversation["participants"],
    subject: subject || undefined,
    title: subject || undefined,
    unreadCount,
    createdAt,
    updatedAt,
    telemedicineLinks: (asRecord(attributes.telemedicine_links) as Conversation["telemedicineLinks"]) ?? undefined,
  };
}

function toMessage(raw: unknown): Message {
  const record = asRecord(raw) ?? {};
  const attributes = asRecord(record.attributes) ?? record;
  const sentAt = String(attributes.sent_at ?? attributes.sentAt ?? attributes.created_at ?? new Date().toISOString());
  const body = String(attributes.content ?? attributes.body ?? "");
  const contentTypeRaw = String(attributes.message_type ?? attributes.contentType ?? "TEXT");
  const normalizedType =
    contentTypeRaw === "RICH_TEXT" ||
    contentTypeRaw === "IMAGE" ||
    contentTypeRaw === "DOCUMENT" ||
    contentTypeRaw === "SYSTEM"
      ? contentTypeRaw
      : "TEXT";

  return {
    id: String(record.id ?? attributes.id ?? ""),
    conversationId: String(attributes.conversation_id ?? attributes.conversationId ?? attributes.sessionId ?? ""),
    senderId: String(attributes.sender_id ?? attributes.senderId ?? attributes.senderRef ?? "unknown"),
    senderName: String(attributes.sender_name ?? attributes.senderName ?? attributes.sender_id ?? "Unknown"),
    body,
    content: body,
    contentType: normalizedType,
    sentAt,
    readBy: Array.isArray(attributes.readBy) ? (attributes.readBy as string[]) : [],
    status: "SENT",
    telemedicineLinks: (asRecord(attributes.telemedicine_links) as Message["telemedicineLinks"]) ?? undefined,
  };
}

function toPaged<T>(items: T[]): PagedResponse<T> {
  return {
    items,
    page: 0,
    size: items.length,
    totalElements: items.length,
    totalPages: 1,
    hasNext: false,
  };
}

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
  const response = await apiClient.get<Envelope<unknown> | PagedResponse<Conversation>>(
    `${V1}/conversations${qs}`
  );
  const payload = response.data;
  if (Array.isArray((payload as Envelope<unknown>).data)) {
    const items = ((payload as Envelope<unknown>).data as unknown[]).map(toConversation);
    return toPaged(items);
  }
  if (Array.isArray((payload as unknown as { items?: Conversation[] }).items)) {
    return payload as PagedResponse<Conversation>;
  }
  return toPaged([]);
}

/**
 * Fetch a single conversation by ID.
 */
export async function fetchConversation(conversationId: string): Promise<Conversation> {
  const response = await apiClient.get<Envelope<unknown> | Conversation>(
    `${V1}/conversations/${encodeURIComponent(conversationId)}`
  );
  const payload = response.data as Envelope<unknown> | Conversation;
  const maybeEnvelope = payload as Envelope<unknown>;
  return maybeEnvelope.data ? toConversation(maybeEnvelope.data) : toConversation(payload);
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
  const response = await apiClient.post<Envelope<unknown> | Conversation>(`${V1}/conversations`, {
    subject: params.subject ?? "Provider conversation",
    participant_ids: params.participantIds,
    conversation_type: params.type ?? "DIRECT",
  });
  const payload = response.data as Envelope<unknown> | Conversation;
  const maybeEnvelope = payload as Envelope<unknown>;
  return maybeEnvelope.data ? toConversation(maybeEnvelope.data) : toConversation(payload);
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
  const response = await apiClient.get<Envelope<unknown> | PagedResponse<Message>>(
    `${V1}/conversations/${encodeURIComponent(conversationId)}/messages${qs}`
  );
  const payload = response.data;
  if (Array.isArray((payload as Envelope<unknown>).data)) {
    const items = ((payload as Envelope<unknown>).data as unknown[]).map(toMessage);
    return toPaged(items);
  }
  if (Array.isArray((payload as unknown as { items?: Message[] }).items)) {
    return payload as PagedResponse<Message>;
  }
  return toPaged([]);
}

/**
 * Send a message in a conversation.
 */
export async function sendMessage(request: SendMessageRequest): Promise<Message> {
  const response = await apiClient.post<Envelope<unknown> | Message>(
    `${V1}/conversations/${encodeURIComponent(request.conversationId)}/messages`,
    {
      sender_id: "provider-app",
      content: request.body,
      message_type: request.contentType ?? "TEXT",
    }
  );
  const payload = response.data as Envelope<unknown> | Message;
  const maybeEnvelope = payload as Envelope<unknown>;
  return maybeEnvelope.data ? toMessage(maybeEnvelope.data) : toMessage(payload);
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
    { participant_id: upToMessageId }
  );
}

/**
 * Fetch communication dashboard KPIs for mobile comms surfaces.
 */
export async function fetchCommunicationDashboard(): Promise<CommunicationDashboard> {
  const response = await apiClient.get<Envelope<unknown>>("/internal/v1/communication/dashboard");
  return normalizeCommunicationDashboard(response.data.data);
}

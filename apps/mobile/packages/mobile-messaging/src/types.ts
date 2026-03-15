/**
 * Messaging Types — Shared types for notifications, conversations, and channels.
 */

export interface Notification {
  id: string;
  type: NotificationType;
  title: string;
  body: string;
  category: NotificationCategory;
  priority: NotificationPriority;
  read: boolean;
  createdAt: string;
  expiresAt?: string;
  deepLink?: string;
  metadata?: Record<string, unknown>;
  actionRequired?: boolean;
  groupKey?: string;
}

export type NotificationType =
  | "PUSH"
  | "IN_APP"
  | "SILENT";

export type NotificationCategory =
  | "TASK_ASSIGNED"
  | "RESULT_READY"
  | "MESSAGE_RECEIVED"
  | "APPOINTMENT_REMINDER"
  | "RX_READY"
  | "ESCALATION"
  | "SYSTEM"
  | "SYNC_COMPLETE";

export type NotificationPriority = "HIGH" | "NORMAL" | "LOW";

export interface NotificationPreference {
  category: NotificationCategory;
  pushEnabled: boolean;
  inAppEnabled: boolean;
}

export interface Conversation {
  id: string;
  type: ConversationType;
  participants: Participant[];
  subject?: string;
  lastMessage?: Message;
  unreadCount: number;
  createdAt: string;
  updatedAt: string;
  metadata?: Record<string, unknown>;
}

export type ConversationType = "DIRECT" | "GROUP" | "SUPPORT" | "SYSTEM";

export interface Participant {
  actorId: string;
  actorType: string;
  displayName: string;
  role: "OWNER" | "MEMBER" | "OBSERVER";
}

export interface Message {
  id: string;
  conversationId: string;
  senderId: string;
  senderName: string;
  body: string;
  contentType: MessageContentType;
  sentAt: string;
  readBy: string[];
  attachments?: Attachment[];
  replyTo?: string;
  status: MessageStatus;
}

export type MessageContentType = "TEXT" | "RICH_TEXT" | "IMAGE" | "DOCUMENT" | "SYSTEM";

export type MessageStatus = "SENDING" | "SENT" | "DELIVERED" | "READ" | "FAILED";

export interface Attachment {
  id: string;
  filename: string;
  mimeType: string;
  sizeBytes: number;
  url: string;
  thumbnailUrl?: string;
}

export interface SendMessageRequest {
  conversationId: string;
  body: string;
  contentType?: MessageContentType;
  replyTo?: string;
  attachmentIds?: string[];
}

export interface ChannelEvent {
  type: string;
  topic: string;
  payload: unknown;
  timestamp: string;
  correlationId: string;
}

export interface PushRegistration {
  deviceId: string;
  platform: "ios" | "android";
  pushToken: string;
  appVersion: string;
}

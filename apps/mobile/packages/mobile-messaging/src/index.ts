/**
 * @impilo/mobile-messaging — Messaging & Notification SDK
 *
 * Push notification registration, in-app notification feed,
 * secure messaging (conversations/threads), and real-time event channels.
 * All 4 mobile apps MUST use this package for messaging.
 */

// Types
export type {
  Notification,
  NotificationType,
  NotificationCategory,
  NotificationPriority,
  NotificationPreference,
  Conversation,
  ConversationType,
  Participant,
  Message,
  MessageContentType,
  MessageStatus,
  Attachment,
  SendMessageRequest,
  ChannelEvent,
  PushRegistration,
} from "./types";

// Services
export {
  registerDevice,
  unregisterDevice,
  fetchNotifications,
  markRead,
  markAllRead,
  getUnreadCount,
  getPreferences,
  updatePreference,
} from "./notificationService";

export {
  fetchConversations,
  fetchConversation,
  createConversation,
  fetchMessages,
  sendMessage,
  markConversationRead,
} from "./conversationService";

// Real-time channels
export { RealtimeChannel } from "./channelClient";
export type { ChannelStatus, ChannelOptions } from "./channelClient";

// React hooks
export {
  useNotifications,
  usePushRegistration,
  useChannel,
  useConversations,
  useMessages,
} from "./hooks";

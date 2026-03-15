/**
 * React Hooks for Messaging — Reactive access to notifications, conversations, and channels.
 */

import { useState, useEffect, useCallback, useRef } from "react";
import type { PagedResponse } from "@impilo/mobile-trust";
import type { Notification, Conversation, Message, ChannelEvent, NotificationPreference } from "./types";
import * as notificationService from "./notificationService";
import * as conversationService from "./conversationService";
import { RealtimeChannel } from "./channelClient";
import type { ChannelStatus } from "./channelClient";

/**
 * Hook for the notification inbox.
 */
export function useNotifications(params: { pageSize?: number; unreadOnly?: boolean } = {}) {
  const [notifications, setNotifications] = useState<Notification[]>([]);
  const [unreadCount, setUnreadCount] = useState(0);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);
  const [page, setPage] = useState(0);
  const [hasMore, setHasMore] = useState(true);
  const pageSize = params.pageSize ?? 20;

  const load = useCallback(async (pageNum: number, replace: boolean) => {
    setIsLoading(true);
    setError(null);
    try {
      const result = await notificationService.fetchNotifications({
        page: pageNum,
        size: pageSize,
        unreadOnly: params.unreadOnly,
      });
      setNotifications((prev) => replace ? result.items : [...prev, ...result.items]);
      setHasMore(result.hasNext);
      setPage(pageNum);

      const count = await notificationService.getUnreadCount();
      setUnreadCount(count);
    } catch (err) {
      setError(err instanceof Error ? err : new Error(String(err)));
    } finally {
      setIsLoading(false);
    }
  }, [pageSize, params.unreadOnly]);

  useEffect(() => {
    load(0, true);
  }, [load]);

  const loadMore = useCallback(() => {
    if (!isLoading && hasMore) {
      load(page + 1, false);
    }
  }, [isLoading, hasMore, page, load]);

  const refresh = useCallback(() => load(0, true), [load]);

  const markRead = useCallback(async (id: string) => {
    await notificationService.markRead(id);
    setNotifications((prev) =>
      prev.map((n) => (n.id === id ? { ...n, read: true } : n))
    );
    setUnreadCount((prev) => Math.max(0, prev - 1));
  }, []);

  const markAllRead = useCallback(async () => {
    await notificationService.markAllRead();
    setNotifications((prev) => prev.map((n) => ({ ...n, read: true })));
    setUnreadCount(0);
  }, []);

  return { notifications, unreadCount, isLoading, error, hasMore, loadMore, refresh, markRead, markAllRead };
}

/**
 * Hook for push notification registration.
 */
export function usePushRegistration() {
  const [isRegistered, setIsRegistered] = useState(false);

  const register = useCallback(async (params: {
    deviceId: string;
    platform: "ios" | "android";
    pushToken: string;
    appVersion: string;
  }) => {
    await notificationService.registerDevice(params);
    setIsRegistered(true);
  }, []);

  const unregister = useCallback(async (deviceId: string) => {
    await notificationService.unregisterDevice(deviceId);
    setIsRegistered(false);
  }, []);

  return { isRegistered, register, unregister };
}

/**
 * Hook for a real-time event channel.
 */
export function useChannel(topic: string) {
  const [messages, setMessages] = useState<ChannelEvent[]>([]);
  const [status, setStatus] = useState<ChannelStatus>("DISCONNECTED");
  const channelRef = useRef<RealtimeChannel | null>(null);

  useEffect(() => {
    const channel = new RealtimeChannel({
      topic,
      onEvent: (event) => setMessages((prev) => [...prev, event]),
      onStatusChange: setStatus,
    });
    channelRef.current = channel;
    channel.connect();

    return () => {
      channel.disconnect();
      channelRef.current = null;
    };
  }, [topic]);

  const isConnected = status === "CONNECTED";

  return { messages, status, isConnected };
}

/**
 * Hook for conversations.
 */
export function useConversations(params: { pageSize?: number } = {}) {
  const [conversations, setConversations] = useState<Conversation[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);
  const pageSize = params.pageSize ?? 20;

  const load = useCallback(async () => {
    setIsLoading(true);
    setError(null);
    try {
      const result = await conversationService.fetchConversations({ page: 0, size: pageSize });
      setConversations(result.items);
    } catch (err) {
      setError(err instanceof Error ? err : new Error(String(err)));
    } finally {
      setIsLoading(false);
    }
  }, [pageSize]);

  useEffect(() => {
    load();
  }, [load]);

  return { conversations, isLoading, error, refresh: load };
}

/**
 * Hook for messages within a conversation.
 */
export function useMessages(conversationId: string, params: { pageSize?: number } = {}) {
  const [messages, setMessages] = useState<Message[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);
  const [hasMore, setHasMore] = useState(true);
  const pageSize = params.pageSize ?? 30;

  const load = useCallback(async (beforeId?: string) => {
    setIsLoading(true);
    setError(null);
    try {
      const result = await conversationService.fetchMessages(conversationId, {
        size: pageSize,
        before: beforeId,
      });
      setMessages((prev) => beforeId ? [...result.items, ...prev] : result.items);
      setHasMore(result.hasNext);
    } catch (err) {
      setError(err instanceof Error ? err : new Error(String(err)));
    } finally {
      setIsLoading(false);
    }
  }, [conversationId, pageSize]);

  useEffect(() => {
    load();
  }, [load]);

  const loadOlder = useCallback(() => {
    if (!isLoading && hasMore && messages.length > 0) {
      load(messages[0].id);
    }
  }, [isLoading, hasMore, messages, load]);

  const send = useCallback(async (body: string, replyTo?: string) => {
    const msg = await conversationService.sendMessage({
      conversationId,
      body,
      replyTo,
    });
    setMessages((prev) => [...prev, msg]);
    return msg;
  }, [conversationId]);

  return { messages, isLoading, error, hasMore, loadOlder, send, refresh: () => load() };
}

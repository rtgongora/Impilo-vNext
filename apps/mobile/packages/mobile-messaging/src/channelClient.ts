/**
 * Channel Client — Real-time event channel via Server-Sent Events (SSE).
 *
 * Provides live updates for task assignments, messages, results, etc.
 * Reconnects automatically on connection loss with exponential backoff.
 *
 * Backend: channels-service (/internal/v1/channels/stream/*)
 */

import { authStore } from "@impilo/mobile-auth";
import { getApiClientConfig } from "@impilo/mobile-api-client";
import type { ChannelEvent } from "./types";

export type ChannelStatus = "CONNECTING" | "CONNECTED" | "RECONNECTING" | "DISCONNECTED" | "ERROR";

export interface ChannelOptions {
  /** Topic to subscribe to (e.g., "tasks", "messages", "results"). */
  topic: string;
  /** Callback for received events. */
  onEvent: (event: ChannelEvent) => void;
  /** Callback for status changes. */
  onStatusChange?: (status: ChannelStatus) => void;
  /** Maximum reconnection attempts before giving up. */
  maxReconnectAttempts?: number;
}

const RECONNECT_BASE_DELAY_MS = 1_000;
const MAX_RECONNECT_DELAY_MS = 30_000;

export class RealtimeChannel {
  private readonly options: ChannelOptions;
  private eventSource: EventSource | null = null;
  private reconnectAttempts = 0;
  private reconnectTimer: ReturnType<typeof setTimeout> | null = null;
  private status: ChannelStatus = "DISCONNECTED";
  private disposed = false;

  constructor(options: ChannelOptions) {
    this.options = options;
  }

  /**
   * Connect to the real-time channel.
   */
  async connect(): Promise<void> {
    if (this.disposed) return;

    this.setStatus("CONNECTING");

    const config = getApiClientConfig();
    const state = authStore.getState();
    if (!state.session) {
      this.setStatus("ERROR");
      return;
    }

    const token = await state.getValidToken();
    const url = new URL(
      `/internal/v1/channels/stream/${encodeURIComponent(this.options.topic)}`,
      config.baseUrl
    );
    url.searchParams.set("token", token);
    url.searchParams.set("tenant", state.session.tenantId);

    this.eventSource = new EventSource(url.toString());

    this.eventSource.onopen = () => {
      this.reconnectAttempts = 0;
      this.setStatus("CONNECTED");
    };

    this.eventSource.onmessage = (event) => {
      try {
        const channelEvent: ChannelEvent = JSON.parse(event.data);
        this.options.onEvent(channelEvent);
      } catch {
        // Ignore malformed events
      }
    };

    this.eventSource.onerror = () => {
      this.eventSource?.close();
      this.eventSource = null;

      if (this.disposed) return;

      const maxAttempts = this.options.maxReconnectAttempts ?? 10;
      if (this.reconnectAttempts >= maxAttempts) {
        this.setStatus("ERROR");
        return;
      }

      this.setStatus("RECONNECTING");
      this.scheduleReconnect();
    };
  }

  /**
   * Disconnect from the channel.
   */
  disconnect(): void {
    this.disposed = true;
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer);
      this.reconnectTimer = null;
    }
    if (this.eventSource) {
      this.eventSource.close();
      this.eventSource = null;
    }
    this.setStatus("DISCONNECTED");
  }

  /**
   * Get current connection status.
   */
  getStatus(): ChannelStatus {
    return this.status;
  }

  private scheduleReconnect(): void {
    const delay = Math.min(
      RECONNECT_BASE_DELAY_MS * Math.pow(2, this.reconnectAttempts),
      MAX_RECONNECT_DELAY_MS
    );
    this.reconnectAttempts++;

    this.reconnectTimer = setTimeout(() => {
      this.reconnectTimer = null;
      this.connect();
    }, delay);
  }

  private setStatus(status: ChannelStatus): void {
    this.status = status;
    this.options.onStatusChange?.(status);
  }
}

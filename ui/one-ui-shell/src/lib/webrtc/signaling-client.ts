import { getBffWsOrigin } from "@/lib/bff-origin";
import type { RtcSignalMessage } from "@/lib/webrtc/types";

export type SignalingStatus = "connecting" | "connected" | "disconnected" | "error";

export class RtcSignalingClient {
  private ws: WebSocket | null = null;
  private reconnectTimer: ReturnType<typeof setTimeout> | null = null;
  private disposed = false;

  constructor(
    private readonly roomId: string,
    private readonly peerId: string,
    private readonly onMessage: (msg: RtcSignalMessage) => void,
    private readonly onStatus: (status: SignalingStatus) => void,
  ) {}

  connect() {
    if (this.disposed || this.ws?.readyState === WebSocket.OPEN) return;

    this.onStatus("connecting");
    const url = `${getBffWsOrigin()}/ws/telemedicine/rtc/${encodeURIComponent(this.roomId)}?from=${encodeURIComponent(this.peerId)}`;
    const ws = new WebSocket(url);
    this.ws = ws;

    ws.onopen = () => {
      this.onStatus("connected");
      ws.send(JSON.stringify({ type: "join", from: this.peerId }));
      this.startHeartbeat();
    };

    ws.onmessage = (event) => {
      try {
        const msg = JSON.parse(String(event.data)) as RtcSignalMessage;
        this.onMessage(msg);
      } catch {
        // ignore malformed frames
      }
    };

    ws.onclose = () => {
      this.stopHeartbeat();
      if (!this.disposed) {
        this.onStatus("disconnected");
        this.scheduleReconnect();
      }
    };

    ws.onerror = () => {
      this.onStatus("error");
    };
  }

  send(type: string, payload?: unknown) {
    if (this.ws?.readyState !== WebSocket.OPEN) return;
    this.ws.send(JSON.stringify({ type, from: this.peerId, payload }));
  }

  close() {
    this.disposed = true;
    this.stopHeartbeat();
    if (this.reconnectTimer) clearTimeout(this.reconnectTimer);
    this.ws?.close();
    this.ws = null;
  }

  private heartbeatTimer: ReturnType<typeof setInterval> | null = null;

  private startHeartbeat() {
    this.stopHeartbeat();
    this.heartbeatTimer = setInterval(() => this.send("ping"), 25000);
  }

  private stopHeartbeat() {
    if (this.heartbeatTimer) {
      clearInterval(this.heartbeatTimer);
      this.heartbeatTimer = null;
    }
  }

  private scheduleReconnect() {
    if (this.reconnectTimer || this.disposed) return;
    this.reconnectTimer = setTimeout(() => {
      this.reconnectTimer = null;
      this.connect();
    }, 2000);
  }
}

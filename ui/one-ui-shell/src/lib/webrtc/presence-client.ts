import { getBffWsOrigin } from "@/lib/bff-origin";
import type { IncomingCallInvite } from "@/lib/webrtc/types";

export type PresenceMessage =
  | { type: "presence-ready"; userId?: string }
  | { type: "registered"; patientId?: string; cpid?: string }
  | IncomingCallInvite
  | { type: "pong" };

export class TelemedicinePresenceClient {
  private ws: WebSocket | null = null;
  private reconnectTimer: ReturnType<typeof setTimeout> | null = null;
  private heartbeatTimer: ReturnType<typeof setInterval> | null = null;
  private disposed = false;

  constructor(
    private readonly userId: string,
    private readonly onMessage: (msg: PresenceMessage) => void,
  ) {}

  connect(patientId?: string, cpid?: string) {
    if (this.disposed || this.ws?.readyState === WebSocket.OPEN) return;

    const url = `${getBffWsOrigin()}/ws/telemedicine/presence?from=${encodeURIComponent(this.userId)}`;
    const ws = new WebSocket(url);
    this.ws = ws;

    ws.onopen = () => {
      if (patientId || cpid) {
        ws.send(JSON.stringify({ type: "register", payload: { patientId, cpid } }));
      }
      this.startHeartbeat();
    };

    ws.onmessage = (event) => {
      try {
        const msg = JSON.parse(String(event.data)) as PresenceMessage;
        if (msg.type === "presence-ready" && (patientId || cpid)) {
          ws.send(JSON.stringify({ type: "register", payload: { patientId, cpid } }));
        }
        this.onMessage(msg);
      } catch {
        // ignore malformed frames
      }
    };

    ws.onclose = () => {
      this.stopHeartbeat();
      if (!this.disposed) {
        this.scheduleReconnect(patientId, cpid);
      }
    };
  }

  close() {
    this.disposed = true;
    this.stopHeartbeat();
    if (this.reconnectTimer) clearTimeout(this.reconnectTimer);
    this.ws?.close();
    this.ws = null;
  }

  private scheduleReconnect(patientId?: string, cpid?: string) {
    if (this.reconnectTimer || this.disposed) return;
    this.reconnectTimer = setTimeout(() => {
      this.reconnectTimer = null;
      this.connect(patientId, cpid);
    }, 2500);
  }

  private startHeartbeat() {
    this.stopHeartbeat();
    this.heartbeatTimer = setInterval(() => {
      if (this.ws?.readyState === WebSocket.OPEN) {
        this.ws.send(JSON.stringify({ type: "ping" }));
      }
    }, 25000);
  }

  private stopHeartbeat() {
    if (this.heartbeatTimer) {
      clearInterval(this.heartbeatTimer);
      this.heartbeatTimer = null;
    }
  }
}

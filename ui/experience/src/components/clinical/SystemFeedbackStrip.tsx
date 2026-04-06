"use client";

/**
 * SystemFeedbackStrip — Persistent strip showing system-level feedback
 * (network status, CDS state, errors, success messages).
 */

import { useState, useEffect, useCallback } from "react";
import { AlertCircle, CheckCircle2, Info, X, RefreshCw } from "lucide-react";

interface SystemMessage {
  id: string;
  type: "error" | "warning" | "success" | "info";
  message: string;
  timestamp: Date;
  dismissible: boolean;
  action?: { label: string; onClick: () => void };
}

const typeConfig = {
  error: { bg: "bg-red-50", border: "border-red-200", text: "text-red-700", icon: AlertCircle },
  warning: { bg: "bg-amber-50", border: "border-amber-200", text: "text-amber-700", icon: AlertCircle },
  success: { bg: "bg-emerald-50", border: "border-emerald-200", text: "text-emerald-700", icon: CheckCircle2 },
  info: { bg: "bg-blue-50", border: "border-blue-200", text: "text-blue-700", icon: Info },
};

export function SystemFeedbackStrip() {
  const [messages, setMessages] = useState<SystemMessage[]>([]);

  const dismiss = useCallback((id: string) => {
    setMessages((prev) => prev.filter((m) => m.id !== id));
  }, []);

  const addMessage = useCallback((msg: SystemMessage) => {
    setMessages((prev) => {
      if (prev.find((m) => m.id === msg.id)) return prev;
      return [...prev, msg];
    });
    if (msg.type === "success" || msg.type === "info") {
      setTimeout(() => dismiss(msg.id), 6000);
    }
  }, [dismiss]);

  useEffect(() => {
    const handleOnline = () => {
      setMessages((prev) => prev.filter((m) => m.id !== "offline"));
      addMessage({ id: "online-" + Date.now(), type: "success", message: "Connection restored", timestamp: new Date(), dismissible: true });
    };
    const handleOffline = () => {
      addMessage({ id: "offline", type: "error", message: "Network connection lost — changes may not be saved", timestamp: new Date(), dismissible: false, action: { label: "Retry", onClick: () => window.location.reload() } });
    };
    const handleSystemFeedback = (e: CustomEvent<SystemMessage>) => addMessage(e.detail);

    window.addEventListener("online", handleOnline);
    window.addEventListener("offline", handleOffline);
    window.addEventListener("system-feedback", handleSystemFeedback as EventListener);
    if (typeof navigator !== "undefined" && !navigator.onLine) handleOffline();

    return () => {
      window.removeEventListener("online", handleOnline);
      window.removeEventListener("offline", handleOffline);
      window.removeEventListener("system-feedback", handleSystemFeedback as EventListener);
    };
  }, [addMessage]);

  if (messages.length === 0) return null;

  return (
    <div className="space-y-0">
      {messages.map((msg) => {
        const config = typeConfig[msg.type];
        const Icon = config.icon;
        return (
          <div key={msg.id} className={`flex items-center gap-2 px-3 py-1.5 text-xs border-b ${config.bg} ${config.border}`}>
            <Icon className={`h-3.5 w-3.5 shrink-0 ${config.text}`} />
            <span className={`flex-1 ${config.text}`}>{msg.message}</span>
            {msg.action && (
              <button onClick={msg.action.onClick} className={`inline-flex items-center gap-1 h-5 px-2 text-[10px] font-medium rounded ${config.text} hover:opacity-80`}>
                <RefreshCw className="h-3 w-3" /> {msg.action.label}
              </button>
            )}
            {msg.dismissible && (
              <button onClick={() => dismiss(msg.id)} className="h-5 w-5 flex items-center justify-center rounded hover:bg-black/5">
                <X className="h-3 w-3" />
              </button>
            )}
          </div>
        );
      })}
    </div>
  );
}

export function dispatchSystemFeedback(msg: Omit<SystemMessage, "timestamp">) {
  window.dispatchEvent(new CustomEvent("system-feedback", { detail: { ...msg, timestamp: new Date() } }));
}

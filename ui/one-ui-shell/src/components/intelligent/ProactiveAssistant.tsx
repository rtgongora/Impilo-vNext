"use client";

/**
 * Proactive Health Assistant — Health OS Experience Doctrine
 *
 * An intelligent overlay that surfaces timely, contextual information
 * based on who the user is, what they're doing, and what the system
 * knows about their patients, schedule, and obligations.
 *
 * Capabilities:
 * - Clinical alerts (drug interactions, critical results, overdue screenings)
 * - Operational nudges (queue bottlenecks, shift handoff reminders, stock alerts)
 * - Wellness prompts (medication adherence, appointment reminders, goal progress)
 * - Guided workflows (step-by-step for complex processes)
 * - Contextual education (relevant clinical guidelines, formulary info)
 *
 * The assistant listens, understands, searches, responds, guides,
 * reminds, alerts, and acts within governed boundaries.
 */

import { useState, useEffect, useCallback, useMemo } from "react";
import { usePathname } from "next/navigation";
import {
  AlertTriangle, CheckCircle2, Info, Heart, Calendar,
  Stethoscope, X, ChevronRight, Sparkles,
  MessageCircle, Lightbulb, Activity,
} from "lucide-react";
import { useWorkModeStore } from "@/hooks/useWorkModeStore";
import { useFacilityStore } from "@/hooks/useFacilityStore";
import { useLayoutPrefsStore } from "@/hooks/useLayoutPrefsStore";
import { useShiftStore } from "@/hooks/useShiftStore";
import {
  shouldSuppressNonUrgent,
  useAssistantUiStore,
} from "@/hooks/useAssistantUiStore";
import { isFocusedWorkspaceRoute } from "@/lib/shell/workspace-context";
import { apiClient } from "@/lib/api-client";

// ── Types ────────────────────────────────────────────────────────────

interface AssistantNotification {
  id: string;
  type: "CLINICAL_ALERT" | "OPERATIONAL" | "WELLNESS" | "GUIDANCE" | "REMINDER" | "INSIGHT";
  severity: "CRITICAL" | "HIGH" | "MEDIUM" | "LOW" | "INFO";
  title: string;
  body: string;
  action?: { label: string; href: string };
  dismissible: boolean;
  timestamp: string;
  source: string;
  context?: Record<string, unknown>;
}

interface AssistantState {
  notifications: AssistantNotification[];
  unreadCount: number;
}

// ── Severity styling ─────────────────────────────────────────────────

const SEVERITY_STYLES: Record<string, { bg: string; border: string; icon: typeof AlertTriangle; iconColor: string }> = {
  CRITICAL: { bg: "bg-danger-soft", border: "border-danger/28", icon: AlertTriangle, iconColor: "text-red-600" },
  HIGH: { bg: "bg-warning-soft", border: "border-warning/35", icon: AlertTriangle, iconColor: "text-amber-600" },
  MEDIUM: { bg: "bg-primary-soft", border: "border-primary/25", icon: Info, iconColor: "text-primary" },
  LOW: { bg: "bg-background", border: "border-border", icon: Info, iconColor: "text-muted-foreground" },
  INFO: { bg: "bg-success-soft", border: "border-success/25", icon: Lightbulb, iconColor: "text-primary" },
};

const TYPE_ICONS: Record<string, typeof Stethoscope> = {
  CLINICAL_ALERT: Stethoscope,
  OPERATIONAL: Activity,
  WELLNESS: Heart,
  GUIDANCE: Lightbulb,
  REMINDER: Calendar,
  INSIGHT: Sparkles,
};

// ── Component ────────────────────────────────────────────────────────

export function ProactiveAssistant() {
  const [state, setState] = useState<AssistantState>({
    notifications: [],
    unreadCount: 0,
  });
  const [chatInput, setChatInput] = useState("");

  // Panel/chat/conversation live in a store so minimising the assistant or
  // navigating never loses the conversation (workflow-state hard gate).
  const isOpen = useAssistantUiStore((s) => s.panelOpen);
  const setPanelOpen = useAssistantUiStore((s) => s.setPanelOpen);
  const togglePanel = useAssistantUiStore((s) => s.togglePanel);
  const chatOpen = useAssistantUiStore((s) => s.chatOpen);
  const setChatOpen = useAssistantUiStore((s) => s.setChatOpen);
  const chatMessages = useAssistantUiStore((s) => s.chatMessages);
  const appendChatMessage = useAssistantUiStore((s) => s.appendChatMessage);
  const lastTypingAt = useAssistantUiStore((s) => s.lastTypingAt);
  const markTyping = useAssistantUiStore((s) => s.markTyping);

  const pathname = usePathname();
  const focusMode = useLayoutPrefsStore((s) => s.focusMode);
  // Focused work (opened application/record or focus mode): Nompilo shrinks
  // to an unobtrusive control and never expands over the workspace on its own.
  const focusedWork = focusMode || (pathname ? isFocusedWorkspaceRoute(pathname) : false);

  // Track active field entry anywhere on the page — non-urgent prompts are
  // suppressed while the user is typing (interruption rule).
  useEffect(() => {
    function onKeyDown(e: KeyboardEvent) {
      const target = e.target as HTMLElement | null;
      if (!target) return;
      if (target.tagName === "INPUT" || target.tagName === "TEXTAREA" || target.isContentEditable) {
        markTyping(Date.now());
      }
    }
    window.addEventListener("keydown", onKeyDown, true);
    return () => window.removeEventListener("keydown", onKeyDown, true);
  }, [markTyping]);
  const suppressNonUrgent = shouldSuppressNonUrgent(lastTypingAt, Date.now());

  const workMode = useWorkModeStore();
  const facility = useFacilityStore();
  const shift = useShiftStore();

  // Fetch contextual notifications based on current user context
  const fetchNotifications = useCallback(async () => {
    try {
      const params = new URLSearchParams();
      if (workMode.mode) params.set("work_mode", workMode.mode);
      if (facility.facility?.id) params.set("facility_id", facility.facility.id);
      if (shift.shift?.id) params.set("shift_id", shift.shift.id);

      const response = await apiClient.get<{ data: AssistantNotification[] }>(
        `/internal/v1/assistant/notifications?${params.toString()}`
      );
      const notifications = response?.data ?? [];
      setState(prev => ({
        ...prev,
        notifications,
        unreadCount: notifications.filter(n => n.severity === "CRITICAL" || n.severity === "HIGH").length,
      }));
    } catch {
      // Silent fail — assistant is non-blocking
    }
  }, [workMode.mode, facility.facility?.id, shift.shift?.id]);

  // Poll for notifications every 30 seconds
  useEffect(() => {
    fetchNotifications();
    const interval = setInterval(fetchNotifications, 30000);
    return () => clearInterval(interval);
  }, [fetchNotifications]);

  const dismiss = useCallback((id: string) => {
    setState(prev => ({
      ...prev,
      notifications: prev.notifications.filter(n => n.id !== id),
      unreadCount: Math.max(0, prev.unreadCount - 1),
    }));
  }, []);

  const handleChat = useCallback(async () => {
    if (!chatInput.trim()) return;
    const userMessage = chatInput.trim();
    setChatInput("");
    appendChatMessage({ role: "user", text: userMessage });

    try {
      const response = await apiClient.post<{ data: { reply: string } }>(
        "/internal/v1/assistant/chat",
        { message: userMessage, context: { work_mode: workMode.mode, facility_id: facility.facility?.id } }
      );
      appendChatMessage({ role: "assistant", text: response?.data?.reply ?? "I'm here to help. Could you rephrase that?" });
    } catch {
      appendChatMessage({ role: "assistant", text: "I'm having trouble connecting. Please try again." });
    }
  }, [appendChatMessage, chatInput, workMode.mode, facility.facility?.id]);

  const criticalNotifications = useMemo(
    () => state.notifications.filter(n => n.severity === "CRITICAL"),
    [state.notifications]
  );

  return (
    <>
      {/* Critical alert banner — always visible at top */}
      {criticalNotifications.length > 0 && (
        <div className="fixed top-0 left-0 right-0 z-50 bg-red-600 text-white px-4 py-2 flex items-center gap-3">
          <AlertTriangle className="h-5 w-5 flex-shrink-0 animate-pulse" />
          <span className="text-sm font-medium flex-1">
            {criticalNotifications[0].title}: {criticalNotifications[0].body}
          </span>
          {criticalNotifications[0].action && (
            <a href={criticalNotifications[0].action.href} className="text-sm underline font-bold">
              {criticalNotifications[0].action.label}
            </a>
          )}
          <button onClick={() => dismiss(criticalNotifications[0].id)} className="p-1">
            <X className="h-4 w-4" />
          </button>
        </div>
      )}

      {/* Floating assistant control — minimised during focused work so it never
          covers Save/Continue/clinical actions; conversation is retained. */}
      <button
        onClick={() => togglePanel()}
        aria-label={isOpen ? "Minimize Nompilo assistant" : "Open Nompilo assistant"}
        aria-expanded={isOpen}
        data-testid="proactive-assistant-launcher"
        data-minimized={focusedWork && !isOpen ? "true" : "false"}
        className={`fixed z-40 flex items-center justify-center rounded-full bg-gradient-to-br from-violet-600 to-indigo-700 text-white shadow-lg transition-all hover:shadow-xl ${
          focusedWork && !isOpen ? "right-2 h-9 w-9 opacity-75 hover:opacity-100 focus-visible:opacity-100" : "right-6 h-14 w-14"
        }`}
        style={{
          bottom: focusedWork && !isOpen
            ? "calc(var(--shell-taskbar-height, 0px) + 5rem)"
            : "calc(var(--shell-taskbar-height, 0px) + 1.5rem)",
        }}
      >
        <Sparkles className={focusedWork && !isOpen ? "h-4 w-4" : "h-6 w-6"} />
        {state.unreadCount > 0 && (
          <span
            className={`absolute -top-1 -right-1 h-5 w-5 rounded-full bg-red-500 text-[10px] font-bold flex items-center justify-center ${
              suppressNonUrgent ? "" : "animate-pulse"
            }`}
          >
            {state.unreadCount}
          </span>
        )}
      </button>

      {/* Notification panel — opens only on deliberate user action */}
      {isOpen && (
        <div
          className="fixed right-6 z-40 w-96 max-h-[70vh] bg-card rounded-2xl shadow-2xl border border-border overflow-hidden flex flex-col"
          style={{ bottom: "calc(var(--shell-taskbar-height, 0px) + 6.5rem)" }}
        >
          {/* Header */}
          <div className="px-4 py-3 bg-gradient-to-r from-violet-600 to-indigo-700 text-white flex items-center justify-between">
            <div className="flex items-center gap-2">
              <Sparkles className="h-5 w-5" />
              <span className="font-semibold">Health Assistant</span>
            </div>
            <div className="flex items-center gap-2">
              <button
                onClick={() => setChatOpen(!chatOpen)}
                aria-label={chatOpen ? "Show notifications" : "Open chat"}
                className="p-1.5 rounded-lg bg-card/20 hover:bg-card/30 transition"
              >
                <MessageCircle className="h-4 w-4" />
              </button>
              <button
                onClick={() => setPanelOpen(false)}
                aria-label="Minimize assistant (conversation is kept)"
                className="p-1.5 rounded-lg bg-card/20 hover:bg-card/30 transition"
              >
                <X className="h-4 w-4" />
              </button>
            </div>
          </div>

          {/* Chat mode */}
          {chatOpen ? (
            <div className="flex flex-col flex-1 min-h-[300px]">
              <div className="flex-1 overflow-y-auto p-3 space-y-2">
                {chatMessages.length === 0 && (
                  <p className="text-sm text-muted-foreground text-center mt-8">
                    Ask me anything about your patients, schedule, guidelines, or the platform.
                  </p>
                )}
                {chatMessages.map((msg, i) => (
                  <div key={i} className={`flex ${msg.role === "user" ? "justify-end" : "justify-start"}`}>
                    <div className={`max-w-[80%] rounded-xl px-3 py-2 text-sm ${
                      msg.role === "user" ? "bg-violet-600 text-white" : "bg-neutral-100 text-foreground"
                    }`}>
                      {msg.text}
                    </div>
                  </div>
                ))}
              </div>
              <div className="p-3 border-t flex gap-2">
                <input
                  value={chatInput}
                  onChange={e => setChatInput(e.target.value)}
                  onKeyDown={e => e.key === "Enter" && handleChat()}
                  placeholder="Ask the assistant..."
                  className="flex-1 rounded-lg border border-border px-3 py-2 text-sm focus:border-violet-500 focus:ring-1 focus:ring-violet-500 outline-none"
                />
                <button
                  onClick={handleChat}
                  className="px-3 py-2 rounded-lg bg-violet-600 text-white text-sm font-medium hover:bg-violet-700"
                >
                  Send
                </button>
              </div>
            </div>
          ) : (
            /* Notification list */
            <div className="flex-1 overflow-y-auto divide-y divide-gray-100">
              {state.notifications.length === 0 ? (
                <div className="p-8 text-center">
                  <CheckCircle2 className="h-10 w-10 text-emerald-400 mx-auto mb-2" />
                  <p className="text-sm text-muted-foreground">All clear — no alerts right now</p>
                </div>
              ) : (
                state.notifications.map(notification => {
                  const style = SEVERITY_STYLES[notification.severity] ?? SEVERITY_STYLES.INFO;
                  const TypeIcon = TYPE_ICONS[notification.type] ?? Info;

                  return (
                    <div key={notification.id} className={`p-3 ${style.bg} border-l-4 ${style.border} flex gap-3`}>
                      <div className="flex-shrink-0 mt-0.5">
                        <TypeIcon className={`h-5 w-5 ${style.iconColor}`} />
                      </div>
                      <div className="flex-1 min-w-0">
                        <p className="text-sm font-medium text-foreground">{notification.title}</p>
                        <p className="text-xs text-muted-foreground mt-0.5">{notification.body}</p>
                        <div className="flex items-center gap-2 mt-1.5">
                          <span className="text-[10px] text-muted-foreground">{notification.source}</span>
                          {notification.action && (
                            <a href={notification.action.href} className="text-xs text-violet-600 font-medium flex items-center gap-0.5">
                              {notification.action.label} <ChevronRight className="h-3 w-3" />
                            </a>
                          )}
                        </div>
                      </div>
                      {notification.dismissible && (
                        <button onClick={() => dismiss(notification.id)} className="flex-shrink-0 p-1">
                          <X className="h-3.5 w-3.5 text-muted-foreground" />
                        </button>
                      )}
                    </div>
                  );
                })
              )}
            </div>
          )}
        </div>
      )}
    </>
  );
}

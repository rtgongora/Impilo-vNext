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
import {
  Bell, AlertTriangle, CheckCircle2, Info, Heart, Pill, Calendar,
  TrendingUp, Shield, Stethoscope, X, ChevronRight, Sparkles,
  MessageCircle, Lightbulb, Clock, Activity,
} from "lucide-react";
import { useAuthStore } from "@/hooks/useAuthStore";
import { useWorkModeStore } from "@/hooks/useWorkModeStore";
import { useFacilityStore } from "@/hooks/useFacilityStore";
import { useShiftStore } from "@/hooks/useShiftStore";
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
  isOpen: boolean;
  unreadCount: number;
}

// ── Severity styling ─────────────────────────────────────────────────

const SEVERITY_STYLES: Record<string, { bg: string; border: string; icon: typeof AlertTriangle; iconColor: string }> = {
  CRITICAL: { bg: "bg-red-50", border: "border-red-200", icon: AlertTriangle, iconColor: "text-red-600" },
  HIGH: { bg: "bg-amber-50", border: "border-amber-200", icon: AlertTriangle, iconColor: "text-amber-600" },
  MEDIUM: { bg: "bg-impilo-50", border: "border-impilo-200", icon: Info, iconColor: "text-impilo-500" },
  LOW: { bg: "bg-gray-50", border: "border-gray-200", icon: Info, iconColor: "text-gray-500" },
  INFO: { bg: "bg-emerald-50", border: "border-emerald-200", icon: Lightbulb, iconColor: "text-emerald-600" },
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
    isOpen: false,
    unreadCount: 0,
  });
  const [chatOpen, setChatOpen] = useState(false);
  const [chatInput, setChatInput] = useState("");
  const [chatMessages, setChatMessages] = useState<Array<{ role: "user" | "assistant"; text: string }>>([]);

  const auth = useAuthStore();
  const workMode = useWorkModeStore();
  const facility = useFacilityStore();
  const shift = useShiftStore();

  // Fetch contextual notifications based on current user context
  const fetchNotifications = useCallback(async () => {
    try {
      const params = new URLSearchParams();
      if (workMode.mode) params.set("work_mode", workMode.mode);
      if (facility.facilityId) params.set("facility_id", facility.facilityId);
      if (shift.shiftId) params.set("shift_id", shift.shiftId);

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
  }, [workMode.mode, facility.facilityId, shift.shiftId]);

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
    setChatMessages(prev => [...prev, { role: "user", text: userMessage }]);

    try {
      const response = await apiClient.post<{ data: { reply: string } }>(
        "/internal/v1/assistant/chat",
        { message: userMessage, context: { work_mode: workMode.mode, facility_id: facility.facilityId } }
      );
      setChatMessages(prev => [...prev, { role: "assistant", text: response?.data?.reply ?? "I'm here to help. Could you rephrase that?" }]);
    } catch {
      setChatMessages(prev => [...prev, { role: "assistant", text: "I'm having trouble connecting. Please try again." }]);
    }
  }, [chatInput, workMode.mode, facility.facilityId]);

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

      {/* Floating assistant button */}
      <button
        onClick={() => setState(prev => ({ ...prev, isOpen: !prev.isOpen }))}
        className="fixed bottom-6 right-6 z-40 h-14 w-14 rounded-full bg-gradient-to-br from-violet-600 to-indigo-700 text-white shadow-lg hover:shadow-xl transition-all flex items-center justify-center"
      >
        <Sparkles className="h-6 w-6" />
        {state.unreadCount > 0 && (
          <span className="absolute -top-1 -right-1 h-5 w-5 rounded-full bg-red-500 text-[10px] font-bold flex items-center justify-center">
            {state.unreadCount}
          </span>
        )}
      </button>

      {/* Notification panel */}
      {state.isOpen && (
        <div className="fixed bottom-24 right-6 z-40 w-96 max-h-[70vh] bg-white rounded-2xl shadow-2xl border border-gray-200 overflow-hidden flex flex-col">
          {/* Header */}
          <div className="px-4 py-3 bg-gradient-to-r from-violet-600 to-indigo-700 text-white flex items-center justify-between">
            <div className="flex items-center gap-2">
              <Sparkles className="h-5 w-5" />
              <span className="font-semibold">Health Assistant</span>
            </div>
            <div className="flex items-center gap-2">
              <button
                onClick={() => setChatOpen(!chatOpen)}
                className="p-1.5 rounded-lg bg-white/20 hover:bg-white/30 transition"
              >
                <MessageCircle className="h-4 w-4" />
              </button>
              <button
                onClick={() => setState(prev => ({ ...prev, isOpen: false }))}
                className="p-1.5 rounded-lg bg-white/20 hover:bg-white/30 transition"
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
                  <p className="text-sm text-gray-400 text-center mt-8">
                    Ask me anything about your patients, schedule, guidelines, or the platform.
                  </p>
                )}
                {chatMessages.map((msg, i) => (
                  <div key={i} className={`flex ${msg.role === "user" ? "justify-end" : "justify-start"}`}>
                    <div className={`max-w-[80%] rounded-xl px-3 py-2 text-sm ${
                      msg.role === "user" ? "bg-violet-600 text-white" : "bg-gray-100 text-gray-800"
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
                  className="flex-1 rounded-lg border border-gray-300 px-3 py-2 text-sm focus:border-violet-500 focus:ring-1 focus:ring-violet-500 outline-none"
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
                  <p className="text-sm text-gray-500">All clear — no alerts right now</p>
                </div>
              ) : (
                state.notifications.map(notification => {
                  const style = SEVERITY_STYLES[notification.severity] ?? SEVERITY_STYLES.INFO;
                  const TypeIcon = TYPE_ICONS[notification.type] ?? Info;
                  const SeverityIcon = style.icon;

                  return (
                    <div key={notification.id} className={`p-3 ${style.bg} border-l-4 ${style.border} flex gap-3`}>
                      <div className="flex-shrink-0 mt-0.5">
                        <TypeIcon className={`h-5 w-5 ${style.iconColor}`} />
                      </div>
                      <div className="flex-1 min-w-0">
                        <p className="text-sm font-medium text-gray-900">{notification.title}</p>
                        <p className="text-xs text-gray-600 mt-0.5">{notification.body}</p>
                        <div className="flex items-center gap-2 mt-1.5">
                          <span className="text-[10px] text-gray-400">{notification.source}</span>
                          {notification.action && (
                            <a href={notification.action.href} className="text-xs text-violet-600 font-medium flex items-center gap-0.5">
                              {notification.action.label} <ChevronRight className="h-3 w-3" />
                            </a>
                          )}
                        </div>
                      </div>
                      {notification.dismissible && (
                        <button onClick={() => dismiss(notification.id)} className="flex-shrink-0 p-1">
                          <X className="h-3.5 w-3.5 text-gray-400" />
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

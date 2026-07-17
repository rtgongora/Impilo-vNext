"use client";

/**
 * Proactive Health Assistant — Health OS Experience Doctrine
 *
 * The docked, context-aware Nompilo surface. It is genuinely context-aware because its proactive
 * notices come from the REAL Nompilo context engine (useNompiloContext → BFF
 * /internal/v1/nompilo/context) — the same source NompiloContextualGuidance uses — which resolves
 * route + userType + trust + locked-state from real service signals. The legacy
 * /assistant/notifications feed is kept only as a SUPPLEMENTARY stream (clearly labelled), never
 * as the primary voice.
 *
 * Conversation runs through the shared useNompiloChat hook (POST /internal/v1/assistant/chat) and
 * renders the backend's reply + genuine next-step actions. When the backend is degraded or
 * unreachable, Nompilo says so and offers safe actions — it NEVER fabricates an answer or a
 * success. When a citizen action fails, the failure signal (privacy-safe: route + error code +
 * correlation id) is surfaced as a "that didn't work — here's what you can do" recovery notice.
 *
 * Presentation: a docked compact panel on desktop; a bottom-sheet focused conversation on mobile.
 * It minimises during active data entry / focused work but stays easily retrievable, and the
 * conversation is retained across minimise + navigation (workflow-state hard gate).
 */

import { useState, useEffect, useCallback, useMemo } from "react";
import { usePathname } from "next/navigation";
import Link from "next/link";
import {
  AlertTriangle, CheckCircle2, Info, Heart, Calendar,
  Stethoscope, X, ChevronRight, Sparkles,
  MessageCircle, Lightbulb, Activity, Lock, ListChecks, GraduationCap,
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
import { useNompiloContext, type NompiloGuidanceItem, type NompiloSeverity } from "@/hooks/queries/useNompilo";
import { useNompiloChat } from "@/hooks/useNompiloChat";
import { useNompiloFailureStore, isFailureFresh } from "@/lib/nompilo-failure";

// ── Types ────────────────────────────────────────────────────────────

type NoticeSeverity = "CRITICAL" | "HIGH" | "MEDIUM" | "LOW" | "INFO";

interface AssistantNotification {
  id: string;
  type: "CLINICAL_ALERT" | "OPERATIONAL" | "WELLNESS" | "GUIDANCE" | "REMINDER" | "INSIGHT";
  severity: NoticeSeverity;
  title: string;
  body: string;
  action?: { label: string; href: string };
  dismissible: boolean;
  timestamp: string;
  source: string;
  context?: Record<string, unknown>;
}

/** Unified proactive notice — the shape the panel renders, from any source. */
interface Notice {
  id: string;
  severity: NoticeSeverity;
  iconKey: string;
  title: string;
  body: string;
  action?: { label: string; href: string };
  /** When set, an "Ask Nompilo" button opens the conversation pre-seeded with this query. */
  askQuery?: string;
  source: string;
  supplementary?: boolean;
  dismissible: boolean;
  /** Dismissing this notice also clears the underlying failure signal. */
  clearsFailure?: boolean;
}

// ── Severity styling ─────────────────────────────────────────────────

const SEVERITY_STYLES: Record<string, { bg: string; border: string; icon: typeof AlertTriangle; iconColor: string }> = {
  CRITICAL: { bg: "bg-danger-soft", border: "border-danger/28", icon: AlertTriangle, iconColor: "text-red-600" },
  HIGH: { bg: "bg-warning-soft", border: "border-warning/35", icon: AlertTriangle, iconColor: "text-amber-600" },
  MEDIUM: { bg: "bg-primary-soft", border: "border-primary/25", icon: Info, iconColor: "text-primary" },
  LOW: { bg: "bg-background", border: "border-border", icon: Info, iconColor: "text-muted-foreground" },
  INFO: { bg: "bg-success-soft", border: "border-success/25", icon: Lightbulb, iconColor: "text-primary" },
};

const ICONS: Record<string, typeof Stethoscope> = {
  CLINICAL_ALERT: Stethoscope,
  OPERATIONAL: Activity,
  WELLNESS: Heart,
  GUIDANCE: Lightbulb,
  REMINDER: Calendar,
  INSIGHT: Sparkles,
  LOCKED_STATE: Lock,
  CHECKLIST_ITEM: ListChecks,
  PROTOCOL: GraduationCap,
  ORIENTATION: Info,
  FAILURE: AlertTriangle,
};

// ── Context → notice mapping ─────────────────────────────────────────

const GUIDANCE_SEVERITY: Record<NompiloSeverity, NoticeSeverity> = {
  CRITICAL: "CRITICAL",
  WARN: "HIGH",
  RECOMMEND: "MEDIUM",
  INFO: "INFO",
};

function guidanceToNotice(item: NompiloGuidanceItem): Notice {
  return {
    id: `guidance:${item.key}`,
    severity: GUIDANCE_SEVERITY[item.severity] ?? "INFO",
    iconKey: item.type,
    title: item.title,
    body: item.body,
    action: item.ctaRoute ? { label: item.ctaLabel ?? "Open", href: item.ctaRoute } : undefined,
    source: item.ownerService ?? "Nompilo",
    dismissible: true,
  };
}

// ── Component ────────────────────────────────────────────────────────

export function ProactiveAssistant() {
  const [supplemental, setSupplemental] = useState<AssistantNotification[]>([]);
  const [dismissed, setDismissed] = useState<Set<string>>(new Set());
  const [chatInput, setChatInput] = useState("");

  // Panel/chat/conversation live in a store so minimising the assistant or
  // navigating never loses the conversation (workflow-state hard gate).
  const isOpen = useAssistantUiStore((s) => s.panelOpen);
  const setPanelOpen = useAssistantUiStore((s) => s.setPanelOpen);
  const togglePanel = useAssistantUiStore((s) => s.togglePanel);
  const chatOpen = useAssistantUiStore((s) => s.chatOpen);
  const setChatOpen = useAssistantUiStore((s) => s.setChatOpen);
  const chatMessages = useAssistantUiStore((s) => s.chatMessages);
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

  // PRIMARY: the real Nompilo context engine — resolves route + userType + trust + locked-state.
  const { data: contextData } = useNompiloContext(pathname ?? "");
  const guidance = useMemo(() => contextData?.data?.guidance ?? [], [contextData]);
  const contextDegraded = contextData?.data?.degraded ?? false;

  // Failure recovery signal (privacy-safe: route + code + correlation id only).
  const lastFailure = useNompiloFailureStore((s) => s.lastFailure);
  const clearFailure = useNompiloFailureStore((s) => s.clear);

  const { send, pending } = useNompiloChat();

  // SUPPLEMENTARY: legacy notifications feed — kept, but clearly secondary to the context engine.
  const fetchNotifications = useCallback(async () => {
    try {
      const params = new URLSearchParams();
      if (workMode.mode) params.set("work_mode", workMode.mode);
      if (facility.facility?.id) params.set("facility_id", facility.facility.id);
      if (shift.shift?.id) params.set("shift_id", shift.shift.id);

      const response = await apiClient.get<{ data: AssistantNotification[] }>(
        `/internal/v1/assistant/notifications?${params.toString()}`
      );
      setSupplemental(response?.data ?? []);
    } catch {
      // Silent fail — assistant is non-blocking; the context engine is the primary voice.
    }
  }, [workMode.mode, facility.facility?.id, shift.shift?.id]);

  useEffect(() => {
    fetchNotifications();
    const interval = setInterval(fetchNotifications, 30000);
    return () => clearInterval(interval);
  }, [fetchNotifications]);

  // Assemble the unified notice list: context guidance (primary) + failure recovery +
  // supplementary notifications, minus locally dismissed items.
  const notices = useMemo<Notice[]>(() => {
    const out: Notice[] = [];

    if (isFailureFresh(lastFailure) && lastFailure) {
      out.push({
        id: `failure:${lastFailure.at}`,
        severity: "HIGH",
        iconKey: "FAILURE",
        title: "That didn't work",
        body:
          `Your last action couldn't be completed (${lastFailure.code}). ` +
          "You can try again, ask me what to do next, or report the problem — a person will follow up." +
          (lastFailure.correlationId ? ` Reference: ${lastFailure.correlationId}.` : ""),
        action: { label: "Report a problem", href: "/welcome/report" },
        askQuery: "I hit an error on this page. What can I do to recover?",
        source: "Nompilo recovery",
        dismissible: true,
        clearsFailure: true,
      });
    }

    for (const item of guidance) out.push(guidanceToNotice(item));

    for (const n of supplemental) {
      out.push({
        id: `supp:${n.id}`,
        severity: n.severity,
        iconKey: n.type,
        title: n.title,
        body: n.body,
        action: n.action,
        source: n.source,
        supplementary: true,
        dismissible: n.dismissible,
      });
    }

    return out.filter((n) => !dismissed.has(n.id));
  }, [guidance, supplemental, lastFailure, dismissed]);

  const dismiss = useCallback(
    (notice: Notice) => {
      setDismissed((prev) => new Set(prev).add(notice.id));
      if (notice.clearsFailure) clearFailure();
    },
    [clearFailure],
  );

  const unreadCount = useMemo(
    () => notices.filter((n) => n.severity === "CRITICAL" || n.severity === "HIGH").length,
    [notices],
  );

  const criticalNotices = useMemo(() => notices.filter((n) => n.severity === "CRITICAL"), [notices]);

  const submitChat = useCallback(() => {
    const q = chatInput;
    setChatInput("");
    void send(q);
  }, [chatInput, send]);

  const askInChat = useCallback(
    (query: string) => {
      setChatOpen(true);
      void send(query);
    },
    [send, setChatOpen],
  );

  return (
    <>
      {/* Critical alert banner — always visible at top */}
      {criticalNotices.length > 0 && (
        <div className="fixed top-0 left-0 right-0 z-50 bg-red-600 text-white px-4 py-2 flex items-center gap-3">
          <AlertTriangle className="h-5 w-5 flex-shrink-0 animate-pulse" />
          <span className="text-sm font-medium flex-1">
            {criticalNotices[0].title}: {criticalNotices[0].body}
          </span>
          {criticalNotices[0].action && (
            <Link href={criticalNotices[0].action.href} className="text-sm underline font-bold">
              {criticalNotices[0].action.label}
            </Link>
          )}
          <button onClick={() => dismiss(criticalNotices[0])} className="p-1" aria-label="Dismiss alert">
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
        {unreadCount > 0 && (
          <span
            className={`absolute -top-1 -right-1 h-5 w-5 rounded-full bg-red-500 text-[10px] font-bold flex items-center justify-center ${
              suppressNonUrgent ? "" : "animate-pulse"
            }`}
          >
            {unreadCount}
          </span>
        )}
      </button>

      {/* Panel — docked compact card on desktop; bottom-sheet on mobile. Opens only on
          deliberate user action. */}
      {isOpen && (
        <div
          className="fixed z-40 flex flex-col overflow-hidden border border-border bg-card shadow-2xl
                     inset-x-0 bottom-0 max-h-[80vh] rounded-t-2xl
                     sm:inset-x-auto sm:right-6 sm:bottom-[calc(var(--shell-taskbar-height,0px)+6.5rem)] sm:w-96 sm:max-h-[70vh] sm:rounded-2xl"
        >
          {/* Header */}
          <div className="px-4 py-3 bg-gradient-to-r from-violet-600 to-indigo-700 text-white flex items-center justify-between">
            <div className="flex items-center gap-2">
              <Sparkles className="h-5 w-5" />
              <span className="font-semibold">Health Assistant</span>
              {contextDegraded && (
                <span className="text-[10px] rounded-full bg-white/20 px-1.5 py-0.5">limited mode</span>
              )}
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
                    Ask me anything about your care, payments, records, or the platform.
                  </p>
                )}
                {chatMessages.map((msg, i) => (
                  <div key={i} className={`flex ${msg.role === "user" ? "justify-end" : "justify-start"}`}>
                    <div
                      className={`max-w-[85%] rounded-xl px-3 py-2 text-sm ${
                        msg.role === "user"
                          ? "bg-violet-600 text-white"
                          : msg.degraded
                          ? "bg-warning-soft text-foreground border border-warning/35"
                          : "bg-neutral-100 text-foreground"
                      }`}
                    >
                      {msg.role === "assistant" && msg.degraded && (
                        <span className="mb-1 flex items-center gap-1 text-[10px] font-medium uppercase tracking-wide text-amber-700">
                          <AlertTriangle className="h-3 w-3" /> Limited mode — not a complete answer
                        </span>
                      )}
                      <p className="whitespace-pre-wrap">{msg.text}</p>
                      {msg.disclaimer && (
                        <p className="mt-1 text-[11px] italic text-muted-foreground">{msg.disclaimer}</p>
                      )}
                      {msg.actions && msg.actions.length > 0 && (
                        <div className="mt-2 flex flex-wrap gap-1.5">
                          {msg.actions.map((a, ai) => (
                            <Link
                              key={`${a.href}:${ai}`}
                              href={a.href}
                              className="inline-flex items-center gap-0.5 rounded-md border border-border bg-card px-2 py-1 text-[11px] font-medium text-violet-700 hover:bg-neutral-50"
                            >
                              {a.label}
                              <ChevronRight className="h-3 w-3" />
                            </Link>
                          ))}
                        </div>
                      )}
                      {msg.sources && msg.sources.length > 0 && (
                        <p className="mt-1 text-[10px] text-muted-foreground">
                          Sources: {msg.sources.map((s) => s.title ?? s.label ?? s.href).filter(Boolean).join(", ")}
                        </p>
                      )}
                    </div>
                  </div>
                ))}
                {pending && (
                  <div className="flex justify-start">
                    <div className="rounded-xl bg-neutral-100 px-3 py-2 text-sm text-muted-foreground">Nompilo is thinking…</div>
                  </div>
                )}
              </div>
              <div className="p-3 border-t flex gap-2">
                <input
                  value={chatInput}
                  onChange={e => setChatInput(e.target.value)}
                  onKeyDown={e => e.key === "Enter" && submitChat()}
                  placeholder="Ask the assistant..."
                  className="flex-1 rounded-lg border border-border px-3 py-2 text-sm focus:border-violet-500 focus:ring-1 focus:ring-violet-500 outline-none"
                />
                <button
                  onClick={submitChat}
                  disabled={pending || !chatInput.trim()}
                  className="px-3 py-2 rounded-lg bg-violet-600 text-white text-sm font-medium hover:bg-violet-700 disabled:opacity-50"
                >
                  Send
                </button>
              </div>
            </div>
          ) : (
            /* Notice list — primary voice is the context engine */
            <div className="flex-1 overflow-y-auto divide-y divide-gray-100">
              {notices.length === 0 ? (
                <div className="p-8 text-center">
                  <CheckCircle2 className="h-10 w-10 text-emerald-400 mx-auto mb-2" />
                  <p className="text-sm text-muted-foreground">All clear — nothing needs your attention right now</p>
                </div>
              ) : (
                notices.map(notice => {
                  const style = SEVERITY_STYLES[notice.severity] ?? SEVERITY_STYLES.INFO;
                  const TypeIcon = ICONS[notice.iconKey] ?? Info;

                  return (
                    <div key={notice.id} className={`p-3 ${style.bg} border-l-4 ${style.border} flex gap-3`}>
                      <div className="flex-shrink-0 mt-0.5">
                        <TypeIcon className={`h-5 w-5 ${style.iconColor}`} />
                      </div>
                      <div className="flex-1 min-w-0">
                        <p className="text-sm font-medium text-foreground">{notice.title}</p>
                        <p className="text-xs text-muted-foreground mt-0.5">{notice.body}</p>
                        <div className="flex flex-wrap items-center gap-2 mt-1.5">
                          <span className="text-[10px] text-muted-foreground">
                            {notice.source}
                            {notice.supplementary ? " · supplementary" : ""}
                          </span>
                          {notice.action && (
                            <Link href={notice.action.href} className="text-xs text-violet-600 font-medium flex items-center gap-0.5">
                              {notice.action.label} <ChevronRight className="h-3 w-3" />
                            </Link>
                          )}
                          {notice.askQuery && (
                            <button
                              type="button"
                              onClick={() => askInChat(notice.askQuery as string)}
                              className="text-xs text-violet-600 font-medium flex items-center gap-0.5"
                            >
                              Ask Nompilo <MessageCircle className="h-3 w-3" />
                            </button>
                          )}
                        </div>
                      </div>
                      {notice.dismissible && (
                        <button onClick={() => dismiss(notice)} className="flex-shrink-0 p-1" aria-label={`Dismiss ${notice.title}`}>
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

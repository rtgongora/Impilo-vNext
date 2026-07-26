"use client";

/**
 * Ask — Conversational Health Assistant (Health OS §2a)
 *
 * "The experience layer is intelligent: it listens, understands, searches,
 *  responds, guides, reminds, alerts, and acts within governed boundaries."
 *
 * Chat-based interaction surface for health, wellness, lifestyle, diet, sleep,
 * service discovery, and general guidance. Context-aware, consent-aware, and
 * proportionate to the user's role and assurance level.
 */

import { useState, useRef, useEffect } from "react";
import { usePathname, useSearchParams } from "next/navigation";
import { MessageSquare, Send, Loader2, Sparkles, ShieldCheck, BookMarked } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { apiClient } from "@/lib/api-client";
import { randomUUID } from "@/lib/uuid";
import {
  useAskEdlizClinical,
  useAskGuidance,
  useClinicalPathways,
  useStartClinicalPathwaySession,
} from "@/hooks/queries/useGuidance";
import { buildNompiloRouteContext } from "@/lib/nompilo-route-context";

interface Message {
  id: string;
  role: "user" | "assistant";
  content: string;
  timestamp: string;
  clinicalMeta?: { traceId?: string; supportMode?: string };
}

export default function AskPage() {
  const searchParams = useSearchParams();
  const pathname = usePathname() ?? "/ask";
  const routeContext = searchParams.get("from") ?? pathname;
  const nompiloContext = buildNompiloRouteContext(routeContext);
  const [messages, setMessages] = useState<Message[]>([
    {
      id: "welcome",
      role: "assistant",
      content: "Hello! I can help you with health, wellness, lifestyle, diet, sleep, service discovery, and general guidance. What would you like to know?",
      timestamp: new Date().toISOString(),
    },
  ]);
  const [input, setInput] = useState("");
  const [sending, setSending] = useState(false);
  const [consentChecked, setConsentChecked] = useState(false);
  const [consentGranted, setConsentGranted] = useState(false);
  const [mode, setMode] = useState<"general" | "edliz">("general");
  const askEdliz = useAskEdlizClinical();
  const askGuidance = useAskGuidance();
  const { data: pathwaysRes, isError: pathwaysUnavailable } = useClinicalPathways();
  const startPathway = useStartClinicalPathwaySession();
  const [pathwaySessionId, setPathwaySessionId] = useState<string | null>(null);
  const endRef = useRef<HTMLDivElement>(null);

  const pathways = (pathwaysRes?.data ?? []) as Array<{
    id?: string;
    pathway_name?: string;
    condition_code?: string;
    step_count?: number;
  }>;

  // Health OS §16a: consent-aware — check if user has opted into personalized guidance.
  // Left as-is by the empty-200 honesty sweep: this catch is fail-closed (a failed consent read
  // drops to non-personalized), and the banner below states that mode explicitly, so the
  // degradation is named rather than hidden. The two Ask paths likewise already report their own
  // unavailability in-band, as assistant messages.
  useEffect(() => {
    apiClient
      .get<{ data: { guidanceConsent: boolean } }>("/internal/v1/guidance/consent-status")
      .then((res) => {
        setConsentGranted(res?.data?.guidanceConsent ?? true);
        setConsentChecked(true);
      })
      .catch(() => {
        // Default to non-personalized mode if consent check fails
        setConsentGranted(false);
        setConsentChecked(true);
      });
  }, []);

  useEffect(() => {
    endRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages]);

  function handleSend(e: React.FormEvent) {
    e.preventDefault();
    const text = input.trim();
    if (!text || sending) return;

    const userMsg: Message = {
      id: randomUUID(),
      role: "user",
      content: text,
      timestamp: new Date().toISOString(),
    };
    setMessages((prev) => [...prev, userMsg]);
    setInput("");
    setSending(true);

    if (mode === "edliz") {
      askEdliz
        .mutateAsync({
          question: text,
          citizen_mode: !consentGranted,
          role: consentGranted ? "PROVIDER" : "CITIZEN",
        })
        .then((res) => {
          const d = res?.data;
          const citations =
            Array.isArray(d?.source_citations) && d.source_citations.length > 0
              ? `\n\nSources: ${d.source_citations
                  .map((c: Record<string, unknown>) => (c.section_title as string) || (c.excerpt as string)?.slice?.(0, 80))
                  .filter(Boolean)
                  .join(" · ")}`
              : "";
          const warnings =
            Array.isArray(d?.warnings) && d.warnings.length > 0 ? `\n\nWarnings: ${d.warnings.join(" ")}` : "";
          const assistantMsg: Message = {
            id: randomUUID(),
            role: "assistant",
            content: (d?.answer_summary as string) || "No indexed answer was available for that question." + citations + warnings,
            timestamp: new Date().toISOString(),
            clinicalMeta: { traceId: d?.trace_id as string | undefined, supportMode: d?.support_mode as string | undefined },
          };
          setMessages((prev) => [...prev, assistantMsg]);
        })
        .catch(() => {
          setMessages((prev) => [
            ...prev,
            {
              id: randomUUID(),
              role: "assistant",
              content: "The clinical knowledge service is unavailable. Try general guidance or retry later.",
              timestamp: new Date().toISOString(),
            },
          ]);
        })
        .finally(() => setSending(false));
      return;
    }

    askGuidance
      .mutateAsync({
        question: text,
        personalized: consentGranted,
        context: { ...nompiloContext },
      })
      .then((res) => {
        const payload = res?.data as { response?: string } | undefined;
        const assistantMsg: Message = {
          id: randomUUID(),
          role: "assistant",
          content: payload?.response ?? "I received your question. The guidance service is processing your request.",
          timestamp: new Date().toISOString(),
        };
        setMessages((prev) => [...prev, assistantMsg]);
      })
      .catch(() => {
        const errorMsg: Message = {
          id: randomUUID(),
          role: "assistant",
          content: "I was unable to process your question at this time. The guidance service may be temporarily unavailable.",
          timestamp: new Date().toISOString(),
        };
        setMessages((prev) => [...prev, errorMsg]);
      })
      .finally(() => setSending(false));
  }

  return (
    <AppLayout>
      <PageShell title="Ask" subtitle="General guidance or governed Ask EDLIZ (national clinical knowledge)" serviceSlug="nompilo">
        <div className="flex flex-col h-[calc(100vh-220px)] max-w-2xl mx-auto">
          <p className="mb-2 rounded-lg border border-violet-200 bg-violet-50 px-3 py-2 text-xs text-violet-900" data-testid="nompilo-ask-route-context">
            Nompilo context: <code className="font-mono">{nompiloContext.routePath}</code> · {nompiloContext.surface} journey
          </p>
          <div className="flex gap-2 mb-3">
            <button
              type="button"
              onClick={() => setMode("general")}
              className={`text-xs px-3 py-1.5 rounded-full border ${mode === "general" ? "bg-primary text-white border-impilo-500" : "bg-card text-foreground border-border"}`}
            >
              General guidance
            </button>
            <button
              type="button"
              onClick={() => setMode("edliz")}
              className={`text-xs px-3 py-1.5 rounded-full border inline-flex items-center gap-1 ${mode === "edliz" ? "bg-emerald-700 text-white border-emerald-700" : "bg-card text-foreground border-border"}`}
            >
              <BookMarked className="h-3 w-3" /> Ask EDLIZ
            </button>
          </div>
          {/* Health OS §16a: consent-aware guidance banner */}
          {consentChecked && !consentGranted && (
            <div className="mb-3 rounded-lg border border-warning/35 bg-warning-soft p-3 text-xs text-warning-foreground flex items-start gap-2">
              <ShieldCheck className="h-4 w-4 mt-0.5 shrink-0" />
              <div>
                <strong>Non-personalized mode.</strong> Responses are general and not tailored to your health profile.
                To receive personalized guidance, enable guidance consent in your <a href="/settings/notifications" className="underline">preferences</a>.
              </div>
            </div>
          )}
          {/* The pathway chips vanish entirely on a failed read, which in EDLIZ mode reads as
              "no guided pathways are published". Low stakes — the user can still ask the question
              directly — but say it rather than disappear. */}
          {mode === "edliz" && pathwaysUnavailable && (
            <div className="mb-3 rounded-lg border border-warning/35 bg-warning-soft p-3 text-xs text-warning-foreground">
              Guided pathways could not be loaded. This is not a record that none are published.
            </div>
          )}
          {mode === "edliz" && !pathwaysUnavailable && pathways.length > 0 && (
            <div className="mb-3 rounded-lg border border-emerald-100 bg-card p-3 text-xs text-foreground">
              <p className="font-medium text-primary-hover mb-2">Guided pathways (national demo)</p>
              <div className="flex flex-wrap gap-2">
                {pathways
                  .filter((p) => (p.step_count ?? 0) > 0)
                  .map((p) => (
                    <button
                      key={p.id}
                      type="button"
                      disabled={startPathway.isPending}
                      onClick={() => {
                        if (!p.id) return;
                        startPathway.mutate(
                          { pathway_id: p.id },
                          {
                            onSuccess: (res) => {
                              const inner = res?.data as { session_id?: string } | undefined;
                              const sid = inner?.session_id;
                              if (sid) setPathwaySessionId(sid);
                            },
                          }
                        );
                      }}
                      className="rounded-full border border-success/25 px-2 py-1 hover:bg-success-soft disabled:opacity-50"
                    >
                      {p.pathway_name ?? p.condition_code}
                    </button>
                  ))}
              </div>
              {pathwaySessionId && (
                <p className="mt-2 text-[10px] text-muted-foreground">
                  Session started: <span className="font-mono">{pathwaySessionId}</span> — advance via{" "}
                  <code className="bg-neutral-100 px-1 rounded">POST /clinical/pathways/sessions/…/advance</code>
                </p>
              )}
            </div>
          )}
          {/* Messages */}
          <div className="flex-1 overflow-y-auto space-y-4 pb-4">
            {messages.map((msg) => (
              <div key={msg.id} className={`flex ${msg.role === "user" ? "justify-end" : "justify-start"}`}>
                <div className={`max-w-[80%] rounded-2xl px-4 py-3 text-sm ${
                  msg.role === "user"
                    ? "bg-primary text-white rounded-br-md"
                    : "bg-neutral-100 text-foreground rounded-bl-md"
                }`}>
                  {msg.role === "assistant" && (
                    <div className="flex items-center gap-1 mb-1 text-xs text-muted-foreground">
                      <Sparkles className="h-3 w-3" />{" "}
                      {msg.clinicalMeta?.traceId ? "EDLIZ clinical knowledge" : "Health OS Guidance"}
                    </div>
                  )}
                  <p className="whitespace-pre-wrap">{msg.content}</p>
                  {msg.clinicalMeta?.traceId && (
                    <p className="mt-2 text-[10px] text-muted-foreground">Trace: {msg.clinicalMeta.traceId}</p>
                  )}
                </div>
              </div>
            ))}
            {sending && (
              <div className="flex justify-start">
                <div className="bg-neutral-100 rounded-2xl rounded-bl-md px-4 py-3 text-sm text-muted-foreground">
                  <Loader2 className="h-4 w-4 animate-spin inline mr-1" /> Thinking...
                </div>
              </div>
            )}
            <div ref={endRef} />
          </div>

          {/* Input */}
          <form onSubmit={handleSend} className="flex gap-2 border-t border-border pt-4">
            <input
              value={input}
              onChange={(e) => setInput(e.target.value)}
              placeholder="Ask about health, wellness, diet, sleep, services..."
              className="flex-1 rounded-full border border-border px-4 py-2.5 text-sm focus:outline-none focus:border-impilo-400 focus:ring-1 focus:ring-impilo-200"
              disabled={sending}
            />
            <button
              type="submit"
              disabled={sending || !input.trim()}
              className="rounded-full bg-primary p-2.5 text-white hover:bg-primary-hover disabled:opacity-50 transition-colors"
            >
              <Send className="h-4 w-4" />
            </button>
          </form>
        </div>
      </PageShell>
    </AppLayout>
  );
}

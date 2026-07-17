"use client";

/**
 * Nompilo Global Command Bar — the always-present "ask / act" surface.
 *
 * Context is resolved by the REAL Nompilo context engine (useNompiloContext → BFF
 * /internal/v1/nompilo/context), the same source NompiloContextualGuidance uses. Suggestion
 * chips are the live guidance set: items with a real owner route are genuine next-step links;
 * the rest become one-tap asks routed into the conversational assistant. The fast client-side
 * route classification is kept ONLY as an instant fallback while the engine loads (or when it
 * returns nothing) — Nompilo never blanks out, but it also never invents guidance.
 *
 * "Ask" opens the docked conversational assistant (shared useAssistantUiStore + useNompiloChat)
 * with the typed query, instead of a dead link.
 */

import { useMemo, useState } from "react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { Mic, Search, Sparkles } from "lucide-react";
import { DictationButton } from "@/components/ui/DictationButton";
import { useShellStore } from "@/hooks/useShellStore";
import { useAssistantUiStore } from "@/hooks/useAssistantUiStore";
import { useNompiloChat } from "@/hooks/useNompiloChat";
import { useNompiloContext } from "@/hooks/queries/useNompilo";
import { classifyRouteJourney } from "@/lib/ui-route-journey-map";

interface CommandSuggestion {
  label: string;
  /** Real destination when the guidance item owns a route; otherwise it becomes an ask. */
  route?: string | null;
}

/** Instant client-side fallback while the context engine loads or returns nothing. */
function fallbackSuggestions(journey: ReturnType<typeof classifyRouteJourney>): CommandSuggestion[] {
  if (journey === "PERSON")
    return [
      { label: "Find care near me" },
      { label: "Explain this payment step" },
      { label: "Book follow-up" },
      { label: "Find a Health OS app" },
    ];
  if (journey === "PROVIDER")
    return [
      { label: "Show my queue blockers" },
      { label: "Summarize this client context" },
      { label: "Open Fundo refresher" },
      { label: "What apps are installed here?" },
    ];
  if (journey === "PLATFORM")
    return [
      { label: "Show sync exceptions" },
      { label: "Find payment gate failures" },
      { label: "Draft operations briefing" },
      { label: "Show pending marketplace approvals" },
    ];
  return [
    { label: "Search services" },
    { label: "Open support" },
    { label: "Help me navigate" },
    { label: "Browse Health OS apps" },
  ];
}

export function NompiloGlobalCommandBar() {
  const pathname = usePathname();
  const focusSearchPalette = useShellStore((s) => s.focusSearchPalette);
  const setPanelOpen = useAssistantUiStore((s) => s.setPanelOpen);
  const setChatOpen = useAssistantUiStore((s) => s.setChatOpen);
  const { send } = useNompiloChat();
  const [draft, setDraft] = useState("");

  const journey = classifyRouteJourney(pathname ?? "");
  const { data } = useNompiloContext(pathname ?? "");
  const guidance = data?.data?.guidance ?? [];
  const degraded = data?.data?.degraded ?? false;

  // Real suggestions from the context engine; fast client classification only as a fallback
  // while the engine is still loading or has produced nothing to say.
  const suggestions = useMemo<CommandSuggestion[]>(() => {
    if (guidance.length > 0) {
      return guidance
        .slice(0, 6)
        .map((item) => ({ label: item.ctaLabel?.trim() || item.title, route: item.ctaRoute }));
    }
    // Instant fallback while the engine loads or when it has nothing route-specific to say.
    return fallbackSuggestions(journey);
  }, [guidance, journey]);

  const openChatWith = (query: string) => {
    setPanelOpen(true);
    setChatOpen(true);
    if (query.trim()) {
      void send(query);
      setDraft("");
    }
  };

  return (
    <div className="rounded-[2rem] border border-[color:var(--nompilo)]/20 bg-gradient-to-r from-[color:var(--nompilo-soft)] via-card to-[color:var(--surface-warm)] p-3 shadow-impilo-nompilo">
      <div className="flex items-center gap-2">
        <Sparkles className="h-4 w-4 text-[color:var(--nompilo)]" />
        <span className="text-xs font-semibold uppercase tracking-wide text-[color:var(--nompilo)]">Nompilo Command Layer</span>
        <div className="ml-1 hidden items-center gap-1 sm:inline-flex">
          <span className="h-1.5 w-1.5 rounded-full bg-[color:var(--impilo-green)]" />
          <span className="h-1.5 w-1.5 rounded-full bg-[color:var(--impilo-yellow)]" />
          <span className="h-1.5 w-1.5 rounded-full bg-[color:var(--impilo-red)]" />
          <span className="h-1.5 w-1.5 rounded-full bg-[color:var(--impilo-charcoal)]" />
        </div>
        {degraded ? (
          <span className="ml-auto text-[10px] text-muted-foreground">Some guidance is unavailable right now</span>
        ) : null}
      </div>
      <div className="mt-2 flex items-center gap-2">
        <button
          type="button"
          onClick={() => focusSearchPalette()}
          className="inline-flex h-10 w-10 items-center justify-center rounded-full border border-[color:var(--nompilo)]/20 bg-card text-[color:var(--nompilo)] hover:bg-[color:var(--nompilo-soft)]"
          aria-label="Open Nompilo command palette"
          title="Open Nompilo command palette"
        >
          <Search className="h-4 w-4" />
        </button>
        <input
          value={draft}
          onChange={(event) => setDraft(event.target.value)}
          onKeyDown={(event) => {
            if (event.key === "Enter") openChatWith(draft);
          }}
          placeholder="Ask Nompilo... Search services, providers, records, reports, learning, support"
          className="impilo-pill-input h-10 flex-1 border-[color:var(--nompilo)]/20 text-[color:var(--text-primary)]"
        />
        <DictationButton value={draft} onValueChange={setDraft} className="h-10 rounded-full border border-[color:var(--nompilo)]/20 bg-card px-3 text-[color:var(--nompilo)]" />
        <button
          type="button"
          onClick={() => openChatWith(draft)}
          className="inline-flex items-center gap-1 rounded-full bg-[color:var(--nompilo)] px-3.5 py-2 text-xs font-semibold text-warning-foreground shadow-sm hover:opacity-95"
          aria-label="Ask Nompilo"
        >
          <Mic className="h-3.5 w-3.5" />
          Ask
        </button>
      </div>
      <p className="mt-1 text-[10px] text-muted-foreground">
        Context: {journey.replace("_", " ")} journey
        {pathname ? ` · ${pathname}` : ""}
      </p>
      <div className="mt-2 flex flex-wrap gap-2">
        {suggestions.map((suggestion) =>
          suggestion.route ? (
            <Link
              key={`${suggestion.label}:${suggestion.route}`}
              href={suggestion.route}
              className="rounded-full border border-[color:var(--nompilo)]/18 bg-card px-2.5 py-1 text-[11px] text-[color:var(--nompilo)] hover:bg-[color:var(--nompilo-soft)]"
            >
              {suggestion.label}
            </Link>
          ) : (
            <button
              key={suggestion.label}
              type="button"
              onClick={() => openChatWith(suggestion.label)}
              className="rounded-full border border-[color:var(--nompilo)]/18 bg-card px-2.5 py-1 text-[11px] text-[color:var(--nompilo)] hover:bg-[color:var(--nompilo-soft)]"
            >
              {suggestion.label}
            </button>
          ),
        )}
      </div>
    </div>
  );
}

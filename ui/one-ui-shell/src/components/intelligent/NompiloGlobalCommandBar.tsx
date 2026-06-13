"use client";

import { useMemo, useState } from "react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { Mic, Search, Sparkles } from "lucide-react";
import { DictationButton } from "@/components/ui/DictationButton";
import { useShellStore } from "@/hooks/useShellStore";
import { classifyRouteJourney } from "@/lib/ui-route-journey-map";

function suggestionsForJourney(journey: ReturnType<typeof classifyRouteJourney>): string[] {
  if (journey === "PERSON") return ["Find care near me", "Explain this payment step", "Book follow-up", "Find a Health OS app"];
  if (journey === "PROVIDER") return ["Show my queue blockers", "Summarize this client context", "Open Fundo refresher", "What apps are installed here?"];
  if (journey === "PLATFORM") return ["Show sync exceptions", "Find payment gate failures", "Draft operations briefing", "Show pending marketplace approvals"];
  return ["Search services", "Open support", "Help me navigate", "Browse Health OS apps"];
}

export function NompiloGlobalCommandBar() {
  const pathname = usePathname();
  const focusSearchPalette = useShellStore((s) => s.focusSearchPalette);
  const [draft, setDraft] = useState("");
  const journey = classifyRouteJourney(pathname);
  const suggestions = useMemo(() => suggestionsForJourney(journey), [journey]);

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
          onFocus={() => focusSearchPalette()}
          placeholder="Ask Nompilo... Search services, providers, records, reports, learning, support"
          className="impilo-pill-input h-10 flex-1 border-[color:var(--nompilo)]/20 text-[color:var(--text-primary)]"
        />
        <DictationButton value={draft} onValueChange={setDraft} className="h-10 rounded-full border border-[color:var(--nompilo)]/20 bg-card px-3 text-[color:var(--nompilo)]" />
        <Link
          href={pathname ? `/ask?from=${encodeURIComponent(pathname)}` : "/ask"}
          className="inline-flex items-center gap-1 rounded-full bg-[color:var(--nompilo)] px-3.5 py-2 text-xs font-semibold text-warning-foreground shadow-sm hover:opacity-95"
        >
          <Mic className="h-3.5 w-3.5" />
          Ask
        </Link>
      </div>
      <p className="mt-1 text-[10px] text-muted-foreground">
        Context: {journey.replace("_", " ")} journey
        {pathname ? ` · ${pathname}` : ""}
      </p>
      <div className="mt-2 flex flex-wrap gap-2">
        {suggestions.map((suggestion) => (
          <button
            key={suggestion}
            type="button"
            onClick={() => {
              setDraft(suggestion);
              focusSearchPalette();
            }}
            className="rounded-full border border-[color:var(--nompilo)]/18 bg-card px-2.5 py-1 text-[11px] text-[color:var(--nompilo)] hover:bg-[color:var(--nompilo-soft)]"
          >
            {suggestion}
          </button>
        ))}
      </div>
    </div>
  );
}


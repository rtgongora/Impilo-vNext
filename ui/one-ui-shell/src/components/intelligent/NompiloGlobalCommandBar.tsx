"use client";

import { useMemo, useState } from "react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { Mic, Search, Sparkles } from "lucide-react";
import { DictationButton } from "@/components/ui/DictationButton";
import { useShellStore } from "@/hooks/useShellStore";
import { classifyRouteJourney } from "@/lib/ui-route-journey-map";

function suggestionsForJourney(journey: ReturnType<typeof classifyRouteJourney>): string[] {
  if (journey === "PERSON") return ["Find care near me", "Explain this payment step", "Book follow-up"];
  if (journey === "PROVIDER") return ["Show my queue blockers", "Summarize this client context", "Open Fundo refresher"];
  if (journey === "PLATFORM") return ["Show sync exceptions", "Find payment gate failures", "Draft operations briefing"];
  return ["Search services", "Open support", "Help me navigate"];
}

export function NompiloGlobalCommandBar() {
  const pathname = usePathname();
  const focusSearchPalette = useShellStore((s) => s.focusSearchPalette);
  const [draft, setDraft] = useState("");
  const journey = classifyRouteJourney(pathname);
  const suggestions = useMemo(() => suggestionsForJourney(journey), [journey]);

  return (
    <div className="rounded-2xl border border-violet-200 bg-gradient-to-r from-violet-50 to-indigo-50 p-3">
      <div className="flex items-center gap-2">
        <Sparkles className="h-4 w-4 text-violet-600" />
        <span className="text-xs font-semibold uppercase tracking-wide text-violet-700">Nompilo Command Layer</span>
      </div>
      <div className="mt-2 flex items-center gap-2">
        <button
          type="button"
          onClick={() => focusSearchPalette()}
          className="inline-flex h-9 w-9 items-center justify-center rounded-lg border border-violet-200 bg-white text-violet-700 hover:bg-violet-100"
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
          className="h-9 flex-1 rounded-lg border border-violet-200 bg-white px-3 text-sm text-slate-700 outline-none ring-violet-300 focus:ring-2"
        />
        <DictationButton value={draft} onValueChange={setDraft} className="border border-violet-200" />
        <Link
          href="/ask"
          className="inline-flex items-center gap-1 rounded-lg bg-violet-600 px-3 py-2 text-xs font-semibold text-white hover:bg-violet-700"
        >
          <Mic className="h-3.5 w-3.5" />
          Ask
        </Link>
      </div>
      <div className="mt-2 flex flex-wrap gap-2">
        {suggestions.map((suggestion) => (
          <button
            key={suggestion}
            type="button"
            onClick={() => {
              setDraft(suggestion);
              focusSearchPalette();
            }}
            className="rounded-full border border-violet-200 bg-white px-2.5 py-1 text-[11px] text-violet-700 hover:bg-violet-100"
          >
            {suggestion}
          </button>
        ))}
      </div>
    </div>
  );
}


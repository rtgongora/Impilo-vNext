"use client";

/**
 * Nompilo Adaptive Guidance — Stateful, non-blocking 6-stage lifecycle
 * for identity and journey guidance.
 *
 * Lifecycle: Arrival -> Reading Period -> Gentle Fade -> Re-expansion -> Context Change -> User Control.
 */

import { useEffect, useRef, useState } from "react";
import { Bot, ChevronDown, ChevronUp, Sparkles, X } from "lucide-react";

interface NompiloAdaptiveGuidanceProps {
  contextMessage: string;
  suggestions?: string[];
  id?: string;
}

export function NompiloAdaptiveGuidance({
  contextMessage,
  suggestions = [],
  id = "auth-guidance",
}: NompiloAdaptiveGuidanceProps) {
  const [expanded, setExpanded] = useState(true);
  const [dismissed, setDismissed] = useState(false);
  const [hovered, setHovered] = useState(false);
  const timerRef = useRef<NodeJS.Timeout | null>(null);

  // Stage 2 -> 3: Reading period auto-collapse into pill after 8s if not hovered/focused.
  useEffect(() => {
    if (!expanded || dismissed || hovered) return;
    timerRef.current = setTimeout(() => {
      setExpanded(false);
    }, 8000);
    return () => {
      if (timerRef.current) clearTimeout(timerRef.current);
    };
  }, [expanded, dismissed, hovered]);

  if (dismissed) return null;

  if (!expanded) {
    return (
      <button
        type="button"
        data-testid="nompilo-guidance-pill"
        onClick={() => setExpanded(true)}
        className="mt-3 inline-flex items-center gap-2 rounded-full border border-violet-300 bg-violet-50/90 px-3.5 py-1.5 text-xs font-semibold text-violet-900 shadow-sm hover:bg-violet-100 focus:outline-none focus-visible:ring-2 focus-visible:ring-violet-600"
      >
        <Sparkles className="h-3.5 w-3.5 text-violet-600" aria-hidden />
        <span>Why do I need to sign in?</span>
        <ChevronDown className="h-3.5 w-3.5 text-violet-500" aria-hidden />
      </button>
    );
  }

  return (
    <div
      data-testid="nompilo-guidance-card"
      onMouseEnter={() => setHovered(true)}
      onMouseLeave={() => setHovered(false)}
      className="mt-4 rounded-2xl border border-violet-200 bg-violet-50/80 p-4 shadow-sm"
    >
      <div className="flex items-start justify-between gap-3">
        <div className="flex items-center gap-2">
          <span className="grid h-7 w-7 place-items-center rounded-lg bg-violet-600 text-white">
            <Bot className="h-4 w-4" aria-hidden />
          </span>
          <p className="text-xs font-bold text-violet-900">Nompilo Guidance</p>
        </div>

        <div className="flex items-center gap-1">
          <button
            type="button"
            aria-label="Minimise guidance"
            onClick={() => setExpanded(false)}
            className="rounded-md p-1 text-violet-600 hover:bg-violet-200/60"
          >
            <ChevronUp className="h-3.5 w-3.5" />
          </button>
          <button
            type="button"
            aria-label="Dismiss guidance"
            onClick={() => setDismissed(true)}
            className="rounded-md p-1 text-violet-600 hover:bg-violet-200/60"
          >
            <X className="h-3.5 w-3.5" />
          </button>
        </div>
      </div>

      <p className="mt-2.5 text-xs font-medium leading-relaxed text-violet-950">
        {contextMessage}
      </p>

      {suggestions.length > 0 && (
        <ul className="mt-2.5 space-y-1 border-t border-violet-200/60 pt-2 text-[11px] text-violet-800">
          {suggestions.map((s) => (
            <li key={s} className="flex items-start gap-1.5">
              <span className="mt-1 h-1 w-1 rounded-full bg-violet-500" />
              <span>{s}</span>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

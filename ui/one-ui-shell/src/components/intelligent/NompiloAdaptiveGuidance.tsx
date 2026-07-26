"use client";

/**
 * Nompilo Adaptive Guidance — stateful, non-blocking guidance that accompanies a
 * journey instead of interrupting it.
 *
 * Lifecycle: Arrival -> Reading Period -> Gentle Fade -> Re-expansion -> Context Change -> User Control.
 *
 * Modes (doctrine §11 — Nompilo guides the active journey, it does not sit in a footer):
 *  - "inline"    default. Collapses to an expandable pill after the reading period.
 *  - "advisory"  never auto-collapses. For guidance a person must not miss (safety,
 *                what a service can and cannot do). Still dismissible by the user.
 *  - "companion" sticks alongside the task on wide screens, flowing inline on mobile
 *                so it never covers content on a phone.
 *
 * It never covers input fields, primary actions or emergency controls: it renders in
 * flow, and the pill it collapses to is a sibling of the content, not an overlay.
 */

import { useEffect, useRef, useState } from "react";
import { Bot, ChevronDown, ChevronUp, Sparkles, X } from "lucide-react";

export type NompiloGuidanceMode = "inline" | "advisory" | "companion";

interface NompiloAdaptiveGuidanceProps {
  contextMessage: string;
  suggestions?: string[];
  id?: string;
  mode?: NompiloGuidanceMode;
  /** Short label for the collapsed pill. Defaults to a journey-neutral prompt. */
  collapsedLabel?: string;
}

export function NompiloAdaptiveGuidance({
  contextMessage,
  suggestions = [],
  id = "auth-guidance",
  mode = "inline",
  collapsedLabel,
}: NompiloAdaptiveGuidanceProps) {
  const [expanded, setExpanded] = useState(true);
  const [dismissed, setDismissed] = useState(false);
  const [hovered, setHovered] = useState(false);
  const [focused, setFocused] = useState(false);
  const [reducedMotion, setReducedMotion] = useState(false);
  const timerRef = useRef<NodeJS.Timeout | null>(null);

  useEffect(() => {
    if (typeof window === "undefined" || !window.matchMedia) return;
    setReducedMotion(window.matchMedia("(prefers-reduced-motion: reduce)").matches);
  }, []);

  // Stage 2 -> 3: reading-period auto-collapse into pill after 8s, but NEVER while the user is
  // hovering, keyboard-focused inside it, or has requested reduced motion (a11y doctrine).
  // Advisory guidance never auto-collapses — it is the mode used for things a person
  // should not miss, so only an explicit minimise/dismiss closes it.
  useEffect(() => {
    if (mode === "advisory") return;
    if (!expanded || dismissed || hovered || focused || reducedMotion) return;
    timerRef.current = setTimeout(() => {
      setExpanded(false);
    }, 8000);
    return () => {
      if (timerRef.current) clearTimeout(timerRef.current);
    };
  }, [expanded, dismissed, hovered, focused, reducedMotion, mode]);

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
        <span>{collapsedLabel ?? "Why do I need to sign in?"}</span>
        <ChevronDown className="h-3.5 w-3.5 text-violet-500" aria-hidden />
      </button>
    );
  }

  return (
    <div
      data-testid="nompilo-guidance-card"
      onMouseEnter={() => setHovered(true)}
      onMouseLeave={() => setHovered(false)}
      onFocusCapture={() => setFocused(true)}
      onBlurCapture={(e) => {
        if (!e.currentTarget.contains(e.relatedTarget as Node)) setFocused(false);
      }}
      className={`mt-4 rounded-2xl border border-violet-200 bg-violet-50/80 p-4 shadow-sm${
        mode === "companion" ? " lg:sticky lg:top-24" : ""
      }`}
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

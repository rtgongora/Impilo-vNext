"use client";

import { useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { Ear, Keyboard, Languages, Volume2 } from "lucide-react";
import { announce } from "@/lib/accessibility";
import { useShellStore } from "@/hooks/useShellStore";

const STORAGE_KEY = "impilo:a11y-toolbar";

interface ToolbarState {
  simpleLanguage: boolean;
  highContrast: boolean;
  largeText: boolean;
  lowBandwidth: boolean;
}

function applyFlags(state: ToolbarState) {
  const root = document.documentElement;
  root.classList.toggle("impilo-high-contrast", state.highContrast);
  root.classList.toggle("impilo-large-text", state.largeText);
  root.classList.toggle("impilo-low-bandwidth", state.lowBandwidth);
}

export function AccessibilityToolbar() {
  const focusSearchPalette = useShellStore((s) => s.focusSearchPalette);
  const [state, setState] = useState<ToolbarState>({
    simpleLanguage: false,
    highContrast: false,
    largeText: false,
    lowBandwidth: false,
  });

  useEffect(() => {
    try {
      const stored = sessionStorage.getItem(STORAGE_KEY);
      if (!stored) return;
      const parsed = JSON.parse(stored) as ToolbarState;
      setState(parsed);
      applyFlags(parsed);
    } catch {
      // Ignore invalid stored data.
    }
  }, []);

  useEffect(() => {
    applyFlags(state);
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify(state));
  }, [state]);

  const toggles = useMemo(
    () => [
      {
        key: "simpleLanguage" as const,
        label: "Simple Language",
      },
      {
        key: "highContrast" as const,
        label: "High Contrast",
      },
      {
        key: "largeText" as const,
        label: "Large Text",
      },
      {
        key: "lowBandwidth" as const,
        label: "Low Bandwidth",
      },
    ],
    [],
  );

  return (
    <div className="flex flex-wrap items-center gap-2 rounded-2xl border border-[color:var(--border-soft)] bg-[color:var(--surface)] px-3 py-2 shadow-impilo-card">
      <span className="inline-flex items-center gap-1 text-[11px] font-semibold uppercase tracking-wide text-[color:var(--text-muted)]">
        <Ear className="h-3.5 w-3.5" />
        Accessibility
      </span>
      {toggles.map((toggle) => (
        <button
          key={toggle.key}
          type="button"
          onClick={() =>
            setState((previous) => {
              const next = { ...previous, [toggle.key]: !previous[toggle.key] };
              announce(`${toggle.label} ${next[toggle.key] ? "enabled" : "disabled"}`);
              return next;
            })
          }
          className={[
            "rounded-full border px-2.5 py-1 text-[11px] transition",
            state[toggle.key]
              ? "border-[color:var(--primary-muted)] bg-[color:var(--primary-soft)] text-[color:var(--primary-hover)]"
              : "border-[color:var(--border-soft)] bg-[color:var(--surface-soft)] text-[color:var(--text-secondary)] hover:bg-white",
          ].join(" ")}
        >
          {toggle.label}
        </button>
      ))}
      <button
        type="button"
        onClick={() => {
          const text = "Nompilo accessibility assistance is available from the command bar and settings.";
          window.speechSynthesis?.cancel();
          window.speechSynthesis?.speak(new SpeechSynthesisUtterance(text));
        }}
        className="inline-flex items-center gap-1 rounded-full border border-[color:var(--border-soft)] bg-[color:var(--surface-soft)] px-2.5 py-1 text-[11px] text-[color:var(--text-secondary)] hover:bg-white"
      >
        <Volume2 className="h-3.5 w-3.5" />
        Read Aloud
      </button>
      <button
        type="button"
        onClick={() => focusSearchPalette()}
        className="inline-flex items-center gap-1 rounded-full border border-[color:var(--border-soft)] bg-[color:var(--surface-soft)] px-2.5 py-1 text-[11px] text-[color:var(--text-secondary)] hover:bg-white"
      >
        <Keyboard className="h-3.5 w-3.5" />
        Keyboard Hints
      </button>
      <Link
        href="/caregiving"
        className="inline-flex items-center gap-1 rounded-full border border-[color:var(--border-soft)] bg-[color:var(--surface-soft)] px-2.5 py-1 text-[11px] text-[color:var(--text-secondary)] hover:bg-white"
      >
        <Languages className="h-3.5 w-3.5" />
        Caregiver Assist
      </Link>
    </div>
  );
}


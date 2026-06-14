"use client";

import Link from "next/link";
import { Ear, Keyboard, Languages, Volume2 } from "lucide-react";
import { useAccessibilityPreferences } from "@/hooks/useAccessibilityPreferences";
import { useShellStore } from "@/hooks/useShellStore";

export function ShellAccessibilityMenu() {
  const focusSearchPalette = useShellStore((s) => s.focusSearchPalette);
  const { state, toggles, toggle } = useAccessibilityPreferences();

  return (
    <details className="relative shrink-0">
      <summary
        className="flex h-11 cursor-pointer list-none items-center gap-1 rounded-lg border border-transparent px-2 marker:hidden hover:bg-primary-soft dark:hover:bg-primary-soft [&::-webkit-details-marker]:hidden"
        aria-label="Accessibility options"
      >
        <Ear className="h-5 w-5 text-muted-foreground dark:text-muted-foreground" />
        <span className="hidden text-xs font-medium text-foreground lg:inline dark:text-foreground">Accessibility</span>
      </summary>
      <div className="absolute bottom-full right-0 z-[10001] mb-1 w-[min(92vw,280px)] rounded-xl border border-border bg-card py-2 text-sm shadow-xl dark:border-border dark:bg-card">
        <p className="px-3 pb-1 text-[10px] font-semibold uppercase tracking-wide text-muted-foreground">Display &amp; assist</p>
        {toggles.map((item) => (
          <button
            key={item.key}
            type="button"
            className={`block w-full px-3 py-2 text-left text-xs hover:bg-background dark:hover:bg-primary-soft ${
              state[item.key] ? "font-semibold text-primary-hover" : "text-foreground"
            }`}
            onClick={() => {
              toggle(item.key);
              (document.activeElement as HTMLElement | null)?.blur?.();
            }}
          >
            {item.label}
            {state[item.key] ? " · on" : ""}
          </button>
        ))}
        <div className="my-1 border-t border-border dark:border-border" />
        <button
          type="button"
          className="flex w-full items-center gap-2 px-3 py-2 text-left text-xs hover:bg-background dark:hover:bg-primary-soft"
          onClick={() => {
            const text = "Nompilo accessibility assistance is available from the command bar and settings.";
            window.speechSynthesis?.cancel();
            window.speechSynthesis?.speak(new SpeechSynthesisUtterance(text));
            (document.activeElement as HTMLElement | null)?.blur?.();
          }}
        >
          <Volume2 className="h-3.5 w-3.5 shrink-0" />
          Read aloud
        </button>
        <button
          type="button"
          className="flex w-full items-center gap-2 px-3 py-2 text-left text-xs hover:bg-background dark:hover:bg-primary-soft"
          onClick={() => {
            focusSearchPalette();
            (document.activeElement as HTMLElement | null)?.blur?.();
          }}
        >
          <Keyboard className="h-3.5 w-3.5 shrink-0" />
          Keyboard hints
        </button>
        <Link
          href="/caregiving"
          className="flex items-center gap-2 px-3 py-2 text-xs hover:bg-background dark:hover:bg-primary-soft"
          onClick={() => (document.activeElement as HTMLElement | null)?.blur?.()}
        >
          <Languages className="h-3.5 w-3.5 shrink-0" />
          Caregiver assist
        </Link>
      </div>
    </details>
  );
}

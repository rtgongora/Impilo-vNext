"use client";

import { useAccessibilityPreferences } from "@/hooks/useAccessibilityPreferences";

/**
 * Accessibility controls for the public L0 surface (G-CZO-08 / Persona E).
 *
 * The authenticated shell already exposes these toggles (ShellAccessibilityMenu); a guest on the
 * public landing previously could not. This reuses the same {@link useAccessibilityPreferences}
 * hook — high-contrast, large-text, low-bandwidth, simple-language — so a disabled citizen can
 * adjust the experience before signing in or requesting a Health ID, with no insecure workaround.
 * The hook persists in sessionStorage and toggles global CSS classes, so the choice carries into
 * the authenticated experience too.
 */
export function PublicAccessibilityMenu() {
  const { state, toggles, toggle } = useAccessibilityPreferences();

  return (
    <details className="relative shrink-0">
      <summary
        className="flex h-9 cursor-pointer list-none items-center gap-1.5 rounded-md px-3 text-sm font-medium text-slate-700 marker:hidden hover:bg-slate-100 [&::-webkit-details-marker]:hidden"
        aria-label="Accessibility options"
      >
        <span aria-hidden>♿</span>
        <span className="hidden sm:inline">Accessibility</span>
      </summary>
      <div className="absolute right-0 z-50 mt-1 w-[min(92vw,260px)] rounded-xl border border-slate-200 bg-white py-2 text-sm shadow-xl">
        <p className="px-3 pb-1 text-[10px] font-semibold uppercase tracking-wide text-slate-400">
          Display &amp; language
        </p>
        {toggles.map((item) => (
          <button
            key={item.key}
            type="button"
            aria-pressed={state[item.key]}
            className={`block w-full px-3 py-2 text-left text-sm hover:bg-slate-50 ${
              state[item.key] ? "font-semibold text-emerald-700" : "text-slate-700"
            }`}
            onClick={() => toggle(item.key)}
          >
            {item.label}
            {state[item.key] ? " · on" : ""}
          </button>
        ))}
      </div>
    </details>
  );
}

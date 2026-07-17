"use client";

import { Languages } from "lucide-react";
import { useI18n } from "@/lib/i18n/useI18n";
import type { Locale } from "@/lib/i18n";

/**
 * Accessible language switcher for the citizen front door and the authenticated
 * shell chrome. Uses a native <select> so it is keyboard-operable and announced by
 * screen readers out of the box; options show each language's own native name
 * (English / chiShona / isiNdebele) from {@link LOCALES}.
 *
 * i18n honesty (C8): the underlying {@link useI18n} hook is CLIENT-side and starts
 * at 'en' during SSR + the first client render, then applies the stored preference
 * after mount — so this control is hydration-safe and localises client components
 * only. Server-rendered static copy stays English (documented in the C8 report).
 *
 * The `className` is merged onto the <select> so each mount can match its chrome
 * (light public header vs. token-based authenticated strip).
 */
/**
 * Two mount contexts have distinct chrome:
 *  - "public"  — light slate header on the citizen front door.
 *  - "chrome"  — the authenticated shell strip, which is token-driven and may be
 *                light or dark; use CSS variables so it blends in either theme.
 * The full base set is swapped (not appended) to avoid unpredictable Tailwind
 * utility-conflict resolution between the two colour schemes.
 */
const SELECT_BASE =
  "h-9 cursor-pointer appearance-none rounded-md border py-1.5 pl-8 pr-7 text-sm font-medium focus:outline-none focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-600";

const SELECT_VARIANT: Record<"public" | "chrome", string> = {
  public: "border-slate-300 bg-white text-slate-700 hover:bg-slate-100",
  chrome:
    "border-[color:var(--border-soft)] bg-[color:var(--surface)] text-[color:var(--text-primary)] hover:bg-[color:var(--surface-soft)]",
};

export function LanguageSwitcher({
  className = "",
  variant = "public",
}: {
  /** Wrapper class (positioning). */
  className?: string;
  /** Chrome context — selects a coherent colour scheme. */
  variant?: "public" | "chrome";
}) {
  const { locale, setLocale, locales, t } = useI18n();

  return (
    <span className={`relative inline-flex items-center ${className}`}>
      <Languages
        aria-hidden="true"
        className="pointer-events-none absolute left-2 h-4 w-4 opacity-70"
      />
      <select
        aria-label={t("public.language.choose")}
        value={locale}
        onChange={(event) => setLocale(event.target.value as Locale)}
        className={`${SELECT_BASE} ${SELECT_VARIANT[variant]}`}
      >
        {(Object.keys(locales) as Locale[]).map((code) => (
          <option key={code} value={code}>
            {locales[code].nativeName}
          </option>
        ))}
      </select>
      {/* Decorative chevron; the native control still owns keyboard + SR behaviour. */}
      <svg
        aria-hidden="true"
        viewBox="0 0 20 20"
        className="pointer-events-none absolute right-2 h-4 w-4 opacity-60"
        fill="none"
        stroke="currentColor"
        strokeWidth="1.6"
      >
        <path d="M6 8l4 4 4-4" strokeLinecap="round" strokeLinejoin="round" />
      </svg>
    </span>
  );
}

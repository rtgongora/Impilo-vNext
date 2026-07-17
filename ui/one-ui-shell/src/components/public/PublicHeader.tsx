"use client";

import Link from "next/link";
import { PublicAccessibilityMenu } from "./PublicAccessibilityMenu";
import { LanguageSwitcher } from "@/components/i18n/LanguageSwitcher";
import { useI18n } from "@/lib/i18n/useI18n";

/**
 * Client chrome island for the public L0 header (C8 i18n). Extracted from the
 * server-rendered {@link PublicShell} so the interactive controls — accessibility
 * menu, language switcher, and the sign-in / create-account actions — can consume
 * the client-side i18n catalog without forcing the whole shell (or the server page
 * children) into the client bundle. Renders English on SSR + first paint, then the
 * stored locale after mount (hydration-safe).
 */
export function PublicHeader() {
  const { t } = useI18n();

  return (
    <header className="border-b border-slate-200 bg-white">
      <div className="mx-auto flex max-w-5xl items-center justify-between gap-4 px-4 py-3">
        <Link
          href="/welcome"
          className="flex items-center gap-2 font-semibold text-slate-900"
        >
          <span className="grid h-8 w-8 place-items-center rounded-lg bg-emerald-600 text-white">
            i
          </span>
          <span className="text-lg">Impilo</span>
          <span className="hidden text-sm font-normal text-slate-500 sm:inline">
            {t("public.chrome.healthOs")}
          </span>
        </Link>
        <nav className="flex items-center gap-2 text-sm">
          <PublicAccessibilityMenu />
          <LanguageSwitcher />
          <Link
            href="/auth/login"
            className="rounded-md px-3 py-1.5 font-medium text-slate-700 hover:bg-slate-100 focus:outline-none focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-600"
          >
            {t("public.chrome.signIn")}
          </Link>
          <Link
            href="/auth/register/contact"
            className="rounded-md bg-emerald-600 px-3 py-1.5 font-medium text-white hover:bg-emerald-700 focus:outline-none focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-700"
          >
            {t("public.chrome.createAccount")}
          </Link>
        </nav>
      </div>
    </header>
  );
}

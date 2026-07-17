"use client";

import Link from "next/link";
import { useI18n } from "@/lib/i18n/useI18n";

/**
 * Client chrome island for the public L0 footer (C8 i18n). Extracted from the
 * server-rendered {@link PublicShell} so the footer links localise via the
 * client-side catalog. Hydration-safe (English on SSR + first paint).
 */
export function PublicFooter() {
  const { t } = useI18n();

  return (
    <footer className="border-t border-slate-200 bg-white">
      <div className="mx-auto flex max-w-5xl flex-col gap-2 px-4 py-6 text-sm text-slate-500 sm:flex-row sm:items-center sm:justify-between">
        <p>{t("public.footer.copyright")}</p>
        <nav className="flex flex-wrap gap-4">
          <Link
            href="/welcome/find-care"
            className="rounded hover:text-slate-900 focus:outline-none focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-600"
          >
            {t("public.footer.findCare")}
          </Link>
          <Link
            href="/welcome/emergency"
            className="rounded hover:text-slate-900 focus:outline-none focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-600"
          >
            {t("public.footer.emergency")}
          </Link>
          <Link
            href="/privacy"
            className="rounded hover:text-slate-900 focus:outline-none focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-600"
          >
            {t("public.footer.privacy")}
          </Link>
          <Link
            href="/terms"
            className="rounded hover:text-slate-900 focus:outline-none focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-600"
          >
            {t("public.footer.terms")}
          </Link>
          <Link
            href="/welcome/accessibility"
            className="rounded hover:text-slate-900 focus:outline-none focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-600"
          >
            {t("public.footer.accessibilityLanguage")}
          </Link>
        </nav>
      </div>
    </footer>
  );
}

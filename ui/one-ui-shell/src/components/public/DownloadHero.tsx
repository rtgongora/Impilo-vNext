"use client";

import { useI18n } from "@/lib/i18n/useI18n";

/**
 * Client hero island for the public download / get-app page (C8 i18n). The page is a
 * server component (exports metadata); this heading + intro localise client-side and
 * fall back to English for locales without a translation.
 */
export function DownloadHero() {
  const { t } = useI18n();

  return (
    <section className="mt-3 rounded-2xl border border-slate-200 bg-white p-8 shadow-sm">
      <h1 className="text-2xl font-bold text-slate-900">{t("public.download.title")}</h1>
      <p className="mt-2 max-w-2xl text-slate-600">{t("public.download.intro")}</p>
    </section>
  );
}

"use client";

import { useI18n } from "@/lib/i18n/useI18n";

/**
 * Client hero island for the public "Get involved" landing (C8 i18n). The page is a
 * server component (exports metadata); this eyebrow + heading + intro localise
 * client-side and fall back to English for locales without a translation.
 */
export function GetInvolvedHero() {
  const { t } = useI18n();

  return (
    <section className="mt-3 rounded-2xl border border-slate-200 bg-white p-8 shadow-sm">
      <p className="text-sm font-medium uppercase tracking-wide text-emerald-700">
        {t("public.getInvolved.eyebrow")}
      </p>
      <h1 className="mt-2 text-3xl font-bold text-slate-900 sm:text-4xl">
        {t("public.getInvolved.title")}
      </h1>
      <p className="mt-4 max-w-2xl text-lg text-slate-600">{t("public.getInvolved.intro")}</p>
    </section>
  );
}

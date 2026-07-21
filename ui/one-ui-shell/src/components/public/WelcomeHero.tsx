"use client";

import Link from "next/link";
import { useI18n } from "@/lib/i18n/useI18n";

/**
 * Client hero island for the public welcome landing (C8 i18n). The page itself is a
 * server component (it exports route metadata), so the localisable hero — heading,
 * intro and the three primary CTAs — is extracted here. Hydration-safe: English on
 * SSR + first paint, stored locale after mount.
 */
export function WelcomeHero() {
  const { t } = useI18n();

  return (
    <section className="rounded-2xl border border-emerald-100 bg-gradient-to-br from-emerald-50 via-white to-teal-50 p-8 shadow-sm">
      <p className="text-sm font-medium uppercase tracking-wide text-emerald-700">
        {t("public.welcome.eyebrow")}
      </p>
      <h1 className="mt-2 text-3xl font-bold text-slate-900 sm:text-4xl">
        {t("public.welcome.title")}
      </h1>
      <p className="mt-4 max-w-2xl text-lg text-slate-600">{t("public.welcome.intro")}</p>
      <div className="mt-6 flex flex-wrap gap-3">
        <Link
          href="/auth/register/contact"
          className="rounded-lg bg-emerald-600 px-5 py-2.5 font-semibold text-white hover:bg-emerald-700 focus:outline-none focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-700"
        >
          {t("public.welcome.createAccount")}
        </Link>
        <Link
          href="/auth/login"
          className="rounded-lg border border-slate-300 bg-white px-5 py-2.5 font-semibold text-slate-800 hover:bg-slate-50 focus:outline-none focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-600"
        >
          {t("public.welcome.signIn")}
        </Link>
        <Link
          href="/auth/register?intent=health-id"
          className="rounded-lg border border-emerald-300 bg-emerald-50 px-5 py-2.5 font-semibold text-emerald-800 hover:bg-emerald-100 focus:outline-none focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-700"
        >
          {t("public.welcome.requestHealthId")}
        </Link>
      </div>
      <p className="mt-3 text-sm text-slate-500">{t("public.welcome.noHealthIdNeeded")}</p>
    </section>
  );
}

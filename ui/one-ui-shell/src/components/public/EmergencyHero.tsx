"use client";

import { useI18n } from "@/lib/i18n/useI18n";

/**
 * Client hero island for the public emergency page (C8 i18n) — the most important
 * surface to localise for low-literacy / emergency users, so the headline and intro
 * carry real chiShona / isiNdebele translations (not just an English fallback).
 *
 * The life-critical call numbers (999 / 112 / 2019) stay in the server-rendered page
 * and are never gated behind localisation: numbers are language-neutral, and keeping
 * them server-rendered means they show even before any client JS runs.
 */
export function EmergencyHero() {
  const { t } = useI18n();

  return (
    <>
      <h1 className="text-2xl font-bold text-red-800">{t("public.emergency.title")}</h1>
      <p className="mt-2 text-red-900">{t("public.emergency.intro")}</p>
    </>
  );
}

"use client";

import Link from "next/link";
import { Home, Phone } from "lucide-react";
import { ImpiloBrandLogo } from "@/components/brand/ImpiloBrandLogo";
import { PublicAccessibilityMenu } from "./PublicAccessibilityMenu";
import { LanguageSwitcher } from "@/components/i18n/LanguageSwitcher";
import { useI18n } from "@/lib/i18n/useI18n";

/**
 * Client chrome island for the public L0 header (C8 i18n). Renders the OFFICIAL
 * Impilo wordmark (never a CSS-drawn approximation) and keeps the three anchors
 * a stranded visitor always needs — Find care, Emergency, and home — reachable
 * from every public page, alongside accessibility, language and account actions.
 * Hydration-safe: English on SSR + first paint, stored locale after mount.
 */
export function PublicHeader() {
  const { t } = useI18n();

  return (
    <header className="border-b border-slate-200 bg-white">
      <div className="mx-auto flex max-w-5xl flex-wrap items-center justify-between gap-x-4 gap-y-2 px-4 py-3">
        {/*
          The public marketing site (impilo.mohcc.gov.zw/, a separate deployment) is the
          landing page. A plain <a> forces a full navigation so Traefik routes "/" to the
          website; a Next <Link> would client-route into the shell's own root, which
          redirects guests straight back to /welcome (a loop).
        */}
        <a
          href="/"
          aria-label="Impilo — back to home"
          className="flex items-center focus:outline-none focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-600"
        >
          <ImpiloBrandLogo variant="full" tone="brand" size={30} />
          <span className="ml-2 hidden text-sm font-normal text-slate-500 md:inline">
            {t("public.chrome.healthOs")}
          </span>
        </a>

        <nav className="flex flex-wrap items-center gap-1.5 text-sm" aria-label="Public">
          <a
            href="/"
            data-testid="public-back-to-home"
            className="inline-flex items-center gap-1.5 rounded-md px-3 py-1.5 font-medium text-slate-700 hover:bg-slate-100 focus:outline-none focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-600"
          >
            <Home className="h-4 w-4" aria-hidden />
            {t("public.chrome.backToHome")}
          </a>
          <Link
            href="/welcome/find-care"
            className="hidden rounded-md px-3 py-1.5 font-medium text-slate-700 hover:bg-slate-100 focus:outline-none focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-600 sm:inline-block"
          >
            {t("public.chrome.findCare")}
          </Link>
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
          <Link
            href="/welcome/emergency"
            className="inline-flex items-center gap-1.5 rounded-md border border-red-300 bg-red-50 px-3 py-1.5 font-semibold text-red-700 hover:bg-red-100 focus:outline-none focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-red-600"
          >
            <Phone className="h-3.5 w-3.5" aria-hidden />
            {t("public.chrome.emergency")}
          </Link>
        </nav>
      </div>
    </header>
  );
}

"use client";

import Link from "next/link";
import Image from "next/image";
import {
  BadgeCheck,
  BookOpenCheck,
  HeartHandshake,
  Phone,
  Smartphone,
  UsersRound,
} from "lucide-react";
import { ImpiloBrandLogo } from "@/components/brand/ImpiloBrandLogo";
import { PublicAccessibilityMenu } from "./PublicAccessibilityMenu";
import { PublicKhulumaIndicator } from "./PublicKhulumaIndicator";
import { LanguageSwitcher } from "@/components/i18n/LanguageSwitcher";
import { useI18n } from "@/lib/i18n/useI18n";

// Short, plain-language public navigation (landing concept §4/§13). Rarer
// destinations live inside the journeys they belong to, not the top bar.
const PUBLIC_NAV = [
  { label: "Care", href: "/welcome/find-care", icon: HeartHandshake },
  { label: "Find", href: "/verify/practitioner", icon: BadgeCheck },
  { label: "Learn", href: "/welcome/health-info", icon: BookOpenCheck },
  { label: "Get involved", href: "/get-involved", icon: UsersRound },
  { label: "Get app", href: "/download", icon: Smartphone },
] as const;

/**
 * Shared responsive public shell header. Public discovery and protected account
 * entry live in the same navigation; no link hands the person to a second product.
 */
export function PublicHeader() {
  const { t } = useI18n();

  return (
    <header className="sticky top-0 z-[9000] border-b border-slate-200/90 bg-white/95 backdrop-blur supports-[backdrop-filter]:bg-white/85">
      <div className="mx-auto flex max-w-[90rem] items-center gap-3 px-4 py-3 sm:px-6 lg:px-8">
        <Link
          href="/"
          aria-label="Impilo — home"
          className="flex shrink-0 flex-col justify-center rounded-lg focus:outline-none focus-visible:ring-2 focus-visible:ring-emerald-600 focus-visible:ring-offset-2"
        >
          <ImpiloBrandLogo variant="full" tone="brand" size={30} />
        </Link>

        {/*
          Ministry identity belongs in the top bar and nowhere else on the page — the
          hero carries the Impilo wordmark alone, so the state seal is stated once
          rather than competing with the product mark for the same attention.
        */}
        <span className="hidden shrink-0 items-center gap-2 border-l border-slate-200 pl-3 md:inline-flex">
          <Image
            src="/brand/mohcc-logo.png"
            alt=""
            width={28}
            height={28}
            className="h-7 w-auto"
            unoptimized
          />
          <span className="text-[11px] font-medium leading-tight text-slate-600">
            Ministry of Health
            <br />
            and Child Care
          </span>
        </span>

        <nav className="hidden min-w-0 flex-1 items-center justify-center gap-0 lg:flex" aria-label="Public">
          {PUBLIC_NAV.map(({ label, href }) => (
            <Link
              key={href}
              href={href}
              className="inline-flex min-h-10 shrink-0 items-center whitespace-nowrap rounded-lg px-2 py-2 text-sm font-medium text-slate-700 hover:bg-emerald-50 hover:text-emerald-900 focus:outline-none focus-visible:ring-2 focus-visible:ring-emerald-600 xl:px-2.5"
            >
              {label}
            </Link>
          ))}
        </nav>

        <div className="ml-auto flex shrink-0 items-center gap-1.5">
          <PublicKhulumaIndicator />
          <span className="hidden sm:inline-flex">
            <PublicAccessibilityMenu />
          </span>
          <span className="hidden md:inline-flex">
            <LanguageSwitcher />
          </span>
          <Link
            href="/auth/login"
            className="inline-flex min-h-10 items-center rounded-lg px-3 py-2 text-sm font-semibold text-slate-800 hover:bg-slate-100 focus:outline-none focus-visible:ring-2 focus-visible:ring-emerald-600"
          >
            {t("public.chrome.signIn")}
          </Link>
          <Link
            href="/welcome/emergency"
            className="hidden min-h-10 items-center gap-1.5 rounded-lg border border-red-300 bg-red-50 px-3 py-2 text-sm font-bold text-red-800 hover:bg-red-100 focus:outline-none focus-visible:ring-2 focus-visible:ring-red-600 lg:inline-flex"
          >
            <Phone className="h-4 w-4" aria-hidden />
            <span>{t("public.chrome.emergency")}</span>
          </Link>
        </div>
      </div>

      <nav
        className="mx-auto grid max-w-[90rem] grid-cols-3 gap-1 border-t border-slate-100 px-3 py-1.5 sm:grid-cols-6 sm:px-6 lg:hidden"
        aria-label="Public mobile"
      >
        {PUBLIC_NAV.map(({ label, href, icon: Icon }) => (
          <Link
            key={href}
            href={href}
            className="inline-flex min-h-11 min-w-0 flex-col items-center justify-center gap-0.5 rounded-lg px-1 py-1 text-center text-[11px] font-semibold leading-tight text-slate-700 hover:bg-emerald-50 hover:text-emerald-900 focus:outline-none focus-visible:ring-2 focus-visible:ring-emerald-600 sm:text-xs"
          >
            <Icon className="h-4 w-4 shrink-0" aria-hidden />
            <span className="truncate">{label.replace("Impilo ", "")}</span>
          </Link>
        ))}
      </nav>
    </header>
  );
}

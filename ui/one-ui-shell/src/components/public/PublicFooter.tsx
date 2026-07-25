"use client";

import Link from "next/link";
import { ImpiloBrandLogo } from "@/components/brand/ImpiloBrandLogo";
import { useI18n } from "@/lib/i18n/useI18n";

const FOOTER_GROUPS = [
  {
    title: "Get help",
    links: [
      ["Find care", "/welcome/find-care"],
      ["Emergency", "/welcome/emergency"],
      ["Give feedback", "/welcome/report"],
      ["Contact and support", "/contact"],
    ],
  },
  {
    title: "Explore",
    links: [
      ["Health information", "/welcome/health-info"],
      ["Notices", "/welcome/notices"],
      ["Get involved", "/get-involved"],
      ["Download", "/download"],
    ],
  },
  {
    title: "Trust",
    links: [
      ["Privacy", "/privacy"],
      ["Terms", "/terms"],
      ["Accessibility and language", "/welcome/accessibility"],
      ["Service status", "/status"],
    ],
  },
] as const;

export function PublicFooter() {
  const { t } = useI18n();

  return (
    <footer className="border-t border-emerald-900/10 bg-emerald-950 text-white">
      <div className="mx-auto grid max-w-[90rem] gap-8 px-4 py-10 sm:px-6 md:grid-cols-[minmax(15rem,1.35fr)_repeat(3,minmax(9rem,1fr))] lg:px-8">
        <div>
          <Link
            href="/"
            aria-label="Impilo home"
            className="inline-flex rounded-lg bg-white p-2 focus:outline-none focus-visible:ring-2 focus-visible:ring-emerald-300"
          >
            <ImpiloBrandLogo variant="full" tone="brand" size={32} />
          </Link>
          <p className="mt-4 max-w-md text-sm leading-6 text-emerald-50/80">
            Your health, connected and protected. One public entry into Zimbabwe&apos;s National
            Health Operating System, led by the Ministry of Health and Child Care.
          </p>
          <p className="mt-4 text-xs text-emerald-100/70">{t("public.footer.copyright")}</p>
        </div>

        {FOOTER_GROUPS.map((group) => (
          <nav key={group.title} aria-label={group.title}>
            <h2 className="text-sm font-semibold text-white">{group.title}</h2>
            <ul className="mt-3 space-y-2.5">
              {group.links.map(([label, href]) => (
                <li key={href}>
                  <Link
                    href={href}
                    className="rounded text-sm text-emerald-50/75 hover:text-white focus:outline-none focus-visible:ring-2 focus-visible:ring-emerald-300"
                  >
                    {label}
                  </Link>
                </li>
              ))}
            </ul>
          </nav>
        ))}
      </div>
    </footer>
  );
}

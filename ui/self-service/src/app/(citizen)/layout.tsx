"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";

const NAV_ITEMS = [
  { href: "/verify", label: "Verify Credential" },
  { href: "/claim", label: "Claim Documents" },
  { href: "/my-documents", label: "My Documents" },
  { href: "/my-credentials", label: "My Credentials" },
] as const;

export default function CitizenLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  const pathname = usePathname();

  return (
    <div className="min-h-screen flex flex-col">
      <header className="bg-card border-b border-neutral-200 sticky top-0 z-10">
        <div className="max-w-5xl mx-auto px-4 sm:px-6">
          <div className="flex items-center justify-between h-14">
            <Link href="/verify" className="text-lg font-semibold text-brand-primary tracking-tight">
              Impilo Self-Service
            </Link>
            <nav className="flex items-center gap-1" aria-label="Main navigation">
              {NAV_ITEMS.map(({ href, label }) => {
                const isActive = pathname === href || pathname.startsWith(href + "/");
                return (
                  <Link
                    key={href}
                    href={href}
                    className={`px-3 py-2 rounded-lg text-sm font-medium transition-colors ${
                      isActive
                        ? "bg-brand-primary/10 text-brand-primary"
                        : "text-neutral-600 hover:text-neutral-900 hover:bg-neutral-100"
                    }`}
                    aria-current={isActive ? "page" : undefined}
                  >
                    {label}
                  </Link>
                );
              })}
            </nav>
          </div>
        </div>
      </header>

      <main className="flex-1 py-8 px-4 sm:px-6">
        <div className="max-w-5xl mx-auto">{children}</div>
      </main>

      <footer className="border-t border-neutral-200 py-4 text-center text-xs text-neutral-500">
        Impilo Health Identity System — Ministry of Health and Child Care
      </footer>
    </div>
  );
}

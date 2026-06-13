"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";

const NAV_ITEMS = [
  { href: "/request-id", label: "Request ID" },
  { href: "/recovery", label: "Recovery" },
  { href: "/my-qr", label: "My QR" },
  { href: "/pickup", label: "Pickup" },
] as const;

export default function CitizenLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  const pathname = usePathname();

  return (
    <div className="min-h-screen flex flex-col">
      {/* Top navigation bar */}
      <header className="bg-card border-b border-neutral-200 sticky top-0 z-10">
        <div className="max-w-5xl mx-auto px-4 sm:px-6">
          <div className="flex items-center justify-between h-14">
            {/* Brand */}
            <Link
              href="/request-id"
              className="text-lg font-semibold text-neutral-900 tracking-tight"
            >
              Impilo Portal
            </Link>

            {/* Navigation links */}
            <nav className="flex items-center gap-1" aria-label="Main navigation">
              {NAV_ITEMS.map(({ href, label }) => {
                const isActive = pathname === href;
                return (
                  <Link
                    key={href}
                    href={href}
                    className={`
                      px-3 py-2 rounded-lg text-sm font-medium transition-colors
                      ${
                        isActive
                          ? "bg-info-soft text-primary-hover"
                          : "text-neutral-600 hover:text-neutral-900 hover:bg-neutral-100"
                      }
                    `}
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

      {/* Page content */}
      <main className="flex-1 py-8 px-4 sm:px-6">
        <div className="max-w-5xl mx-auto">{children}</div>
      </main>

      {/* Footer */}
      <footer className="border-t border-neutral-200 py-4 text-center text-xs text-neutral-500">
        Impilo Health Identity System
      </footer>
    </div>
  );
}

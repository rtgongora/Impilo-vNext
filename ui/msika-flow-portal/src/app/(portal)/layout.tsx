"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";

const NAV_ITEMS = [
  { href: "/browse", label: "Browse" },
  { href: "/cart", label: "Cart" },
  { href: "/orders", label: "My Orders" },
  { href: "/pickup", label: "Pickup" },
  { href: "/substitutions", label: "Substitutions" },
] as const;

export default function PortalLayout({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();

  return (
    <div className="min-h-screen flex flex-col">
      <header className="bg-white border-b border-neutral-200 px-6 py-3">
        <div className="max-w-7xl mx-auto flex items-center justify-between">
          <Link href="/browse" className="flex items-center gap-2">
            <h1 className="text-lg font-semibold text-brand-primary tracking-tight">MSIKA Flow</h1>
            <span className="text-xs text-neutral-500">Health Marketplace</span>
          </Link>
          <nav className="flex gap-1">
            {NAV_ITEMS.map(({ href, label }) => {
              const isActive = pathname === href || pathname.startsWith(href + "/");
              return (
                <Link
                  key={href}
                  href={href}
                  className={`px-3 py-1.5 rounded-lg text-sm font-medium transition-colors ${
                    isActive
                      ? "bg-brand-primary/10 text-brand-primary"
                      : "text-neutral-600 hover:text-neutral-900 hover:bg-neutral-100"
                  }`}
                >
                  {label}
                </Link>
              );
            })}
          </nav>
        </div>
      </header>
      <main className="flex-1 max-w-7xl mx-auto w-full px-6 py-6">{children}</main>
      <footer className="border-t border-neutral-200 px-6 py-3 text-center text-xs text-neutral-400">
        MSIKA Flow v0.1.0 — Zimbabwe Ministry of Health
      </footer>
    </div>
  );
}

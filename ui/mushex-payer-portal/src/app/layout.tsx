"use client";

import "./globals.css";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { useState } from "react";

const NAV_ITEMS = [
  { href: "/", label: "Dashboard" },
  { href: "/payments", label: "Payments" },
  { href: "/receipts", label: "Receipts" },
  { href: "/remittance", label: "Remittance" },
] as const;

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  const pathname = usePathname();
  const [queryClient] = useState(
    () =>
      new QueryClient({
        defaultOptions: {
          queries: { staleTime: 30_000, retry: 1 },
        },
      })
  );

  return (
    <html lang="en">
      <body className="min-h-screen bg-neutral-50 text-neutral-900 antialiased">
        <QueryClientProvider client={queryClient}>
          {/* Top nav bar */}
          <header className="bg-white border-b border-neutral-200">
            <div className="max-w-7xl mx-auto px-6 flex items-center justify-between h-14">
              <Link href="/" className="flex items-center gap-2">
                <h1 className="text-lg font-semibold text-emerald-700 tracking-tight">
                  MUSHEX
                </h1>
                <span className="text-xs text-neutral-400">Payer Portal</span>
              </Link>

              <nav className="flex items-center gap-1" aria-label="Main navigation">
                {NAV_ITEMS.map(({ href, label }) => {
                  const isActive =
                    href === "/"
                      ? pathname === "/"
                      : pathname === href || pathname.startsWith(href + "/");
                  return (
                    <Link
                      key={href}
                      href={href}
                      className={`px-3 py-1.5 rounded-lg text-sm font-medium transition-colors ${
                        isActive
                          ? "bg-emerald-50 text-emerald-700"
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
          </header>

          <main className="max-w-7xl mx-auto px-6 py-8">{children}</main>
        </QueryClientProvider>
      </body>
    </html>
  );
}

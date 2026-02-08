"use client";

import "./globals.css";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { useState } from "react";

const NAV_SECTIONS = [
  {
    label: "Finance",
    items: [
      { href: "/settlements", label: "Settlements" },
      { href: "/reconciliation", label: "Reconciliation" },
      { href: "/refunds", label: "Refunds" },
    ],
  },
  {
    label: "Accounting",
    items: [
      { href: "/ledger", label: "Ledger" },
    ],
  },
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
          <div className="min-h-screen flex">
            {/* Sidebar */}
            <aside className="w-64 bg-white border-r border-neutral-200 flex flex-col">
              <div className="px-5 py-5 border-b border-neutral-200">
                <Link href="/settlements" className="block">
                  <h1 className="text-lg font-semibold text-emerald-700 tracking-tight">
                    MUSHEX
                  </h1>
                  <p className="text-xs text-neutral-500 mt-0.5">
                    Finance Console
                  </p>
                </Link>
              </div>

              <nav
                className="flex-1 px-3 py-4 overflow-y-auto"
                aria-label="Finance console navigation"
              >
                {NAV_SECTIONS.map((section) => (
                  <div key={section.label} className="mb-5">
                    <h2 className="px-2 mb-1.5 text-[11px] font-semibold uppercase tracking-wider text-neutral-400">
                      {section.label}
                    </h2>
                    <ul className="space-y-0.5">
                      {section.items.map(({ href, label }) => {
                        const isActive =
                          pathname === href ||
                          (pathname.startsWith(href + "/") && href !== "/");
                        return (
                          <li key={href}>
                            <Link
                              href={href}
                              className={`block px-2 py-1.5 rounded-lg text-sm font-medium transition-colors ${
                                isActive
                                  ? "bg-emerald-50 text-emerald-700"
                                  : "text-neutral-600 hover:text-neutral-900 hover:bg-neutral-100"
                              }`}
                              aria-current={isActive ? "page" : undefined}
                            >
                              {label}
                            </Link>
                          </li>
                        );
                      })}
                    </ul>
                  </div>
                ))}
              </nav>

              <footer className="px-5 py-3 border-t border-neutral-200 text-[11px] text-neutral-400">
                MUSHEX v0.1.0 &mdash; Finance Console
              </footer>
            </aside>

            {/* Main content */}
            <main className="flex-1 overflow-y-auto">
              {children}
            </main>
          </div>
        </QueryClientProvider>
      </body>
    </html>
  );
}

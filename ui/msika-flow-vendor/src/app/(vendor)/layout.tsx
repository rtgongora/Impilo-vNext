"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";

const NAV_SECTIONS = [
  {
    label: "Operations",
    items: [
      { href: "/queue", label: "Order Queue" },
      { href: "/orders", label: "All Orders" },
      { href: "/fulfillment", label: "Fulfillment" },
    ],
  },
  {
    label: "Clinical",
    items: [{ href: "/substitutions", label: "Substitutions" }],
  },
] as const;

export default function VendorLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  const pathname = usePathname();

  return (
    <div className="min-h-screen flex">
      <aside className="w-64 bg-card border-r border-neutral-200 flex flex-col">
        <div className="px-5 py-5 border-b border-neutral-200">
          <Link href="/queue" className="block">
            <h1 className="text-lg font-semibold text-brand-primary tracking-tight">
              MSIKA Flow
            </h1>
            <p className="text-xs text-neutral-500 mt-0.5">Vendor Portal</p>
          </Link>
        </div>

        <nav
          className="flex-1 px-3 py-4 overflow-y-auto"
          aria-label="Vendor navigation"
        >
          {NAV_SECTIONS.map((section) => (
            <div key={section.label} className="mb-5">
              <h2 className="px-2 mb-1.5 text-[11px] font-semibold uppercase tracking-wider text-neutral-400">
                {section.label}
              </h2>
              <ul className="space-y-0.5">
                {section.items.map(({ href, label }) => {
                  const isActive =
                    pathname === href || pathname.startsWith(`${href}/`);
                  return (
                    <li key={href}>
                      <Link
                        href={href}
                        className={`block px-2 py-1.5 rounded-lg text-sm font-medium transition-colors ${
                          isActive
                            ? "bg-brand-primary/10 text-brand-primary"
                            : "text-neutral-600 hover:text-neutral-900 hover:bg-neutral-100"
                        }`}
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
          MSIKA Flow v0.1.0 — Vendor Portal
        </footer>
      </aside>

      <main className="flex-1 overflow-y-auto">{children}</main>
    </div>
  );
}

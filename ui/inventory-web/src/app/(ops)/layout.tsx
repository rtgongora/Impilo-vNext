"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";

const NAV_SECTIONS = [
  {
    label: "Operations",
    items: [
      { href: "/dashboard", label: "Dashboard" },
      { href: "/movements", label: "Stock Movements" },
    ],
  },
  {
    label: "Workflows",
    items: [
      { href: "/counts", label: "Stock Counts" },
      { href: "/requisitions", label: "Requisitions" },
    ],
  },
  {
    label: "Management",
    items: [
      { href: "/reconciliation", label: "Reconciliation" },
    ],
  },
] as const;

export default function OpsLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  const pathname = usePathname();

  return (
    <div className="min-h-screen flex">
      {/* Sidebar */}
      <aside className="w-64 bg-white border-r border-neutral-200 flex flex-col">
        <div className="px-5 py-5 border-b border-neutral-200">
          <Link href="/dashboard" className="block">
            <h1 className="text-lg font-semibold text-amber-700 tracking-tight">
              Inventory
            </h1>
            <p className="text-xs text-neutral-500 mt-0.5">
              Supply Chain Management
            </p>
          </Link>
        </div>

        <nav
          className="flex-1 px-3 py-4 overflow-y-auto"
          aria-label="Inventory operations navigation"
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
                            ? "bg-amber-50 text-amber-700"
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
          Inventory v0.1.0 &mdash; Supply Chain Management
        </footer>
      </aside>

      {/* Main content */}
      <main className="flex-1 overflow-y-auto">
        {children}
      </main>
    </div>
  );
}

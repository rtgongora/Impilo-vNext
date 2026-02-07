"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";

const NAV_SECTIONS = [
  {
    label: "Clinical",
    items: [
      { href: "/timeline", label: "CPID Timeline Viewer" },
      { href: "/ips", label: "IPS Bundle Viewer" },
    ],
  },
  {
    label: "Reconciliation",
    items: [
      { href: "/reconciliation", label: "Reconciliation Queue" },
      { href: "/reconciliation/trigger", label: "Trigger Reconciliation" },
    ],
  },
  {
    label: "Operations",
    items: [
      { href: "/stats", label: "Resource Statistics" },
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
          <Link href="/timeline" className="block">
            <h1 className="text-lg font-semibold text-brand-primary tracking-tight">
              BUTANO
            </h1>
            <p className="text-xs text-neutral-500 mt-0.5">
              Shared Health Record Ops
            </p>
          </Link>
        </div>

        <nav className="flex-1 px-3 py-4 overflow-y-auto" aria-label="SHR operations navigation">
          {NAV_SECTIONS.map((section) => (
            <div key={section.label} className="mb-5">
              <h2 className="px-2 mb-1.5 text-[11px] font-semibold uppercase tracking-wider text-neutral-400">
                {section.label}
              </h2>
              <ul className="space-y-0.5">
                {section.items.map(({ href, label }) => {
                  const isActive =
                    pathname === href ||
                    (href !== "/reconciliation" && pathname.startsWith(href + "/")) ||
                    (href === "/reconciliation" && pathname === "/reconciliation");
                  return (
                    <li key={href}>
                      <Link
                        href={href}
                        className={`block px-2 py-1.5 rounded-lg text-sm font-medium transition-colors ${
                          isActive
                            ? "bg-brand-primary/10 text-brand-primary"
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
          BUTANO v0.1.0 &mdash; HAPI FHIR R4
        </footer>
      </aside>

      {/* Main content */}
      <main className="flex-1 overflow-y-auto">
        {children}
      </main>
    </div>
  );
}

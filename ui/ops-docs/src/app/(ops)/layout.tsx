"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";

const NAV_SECTIONS = [
  {
    title: "Documents",
    items: [
      { href: "/documents", label: "Document Search" },
      { href: "/documents/upload", label: "Upload Document" },
      { href: "/documents/audit", label: "Audit Trail" },
    ],
  },
  {
    title: "Credentials",
    items: [
      { href: "/credentials", label: "Credential Search" },
      { href: "/credentials/issue", label: "Issue Credential" },
      { href: "/credentials/verify", label: "Public Verify" },
    ],
  },
  {
    title: "Print Jobs",
    items: [
      { href: "/print-jobs", label: "Print Queue" },
      { href: "/print-jobs/create", label: "New Print Job" },
    ],
  },
  {
    title: "Share Slips",
    items: [
      { href: "/share-links", label: "Share Links" },
      { href: "/share-links/create", label: "Create Share Link" },
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
      <aside className="w-64 bg-card border-r border-neutral-200 flex flex-col">
        <div className="p-4 border-b border-neutral-200">
          <Link href="/documents" className="text-lg font-semibold text-brand-primary tracking-tight">
            Impilo Docs
          </Link>
          <p className="text-xs text-neutral-500 mt-1">Operations Console</p>
        </div>
        <nav className="flex-1 overflow-y-auto p-3">
          {NAV_SECTIONS.map((section) => (
            <div key={section.title} className="mb-4">
              <h3 className="px-3 text-xs font-semibold text-neutral-400 uppercase tracking-wider mb-1">
                {section.title}
              </h3>
              <ul>
                {section.items.map(({ href, label }) => {
                  const isActive = pathname === href || pathname.startsWith(href + "/");
                  return (
                    <li key={href}>
                      <Link
                        href={href}
                        className={`block px-3 py-2 rounded-lg text-sm font-medium transition-colors ${
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
      </aside>

      {/* Main content */}
      <main className="flex-1 p-8">
        <div className="max-w-6xl mx-auto">{children}</div>
      </main>
    </div>
  );
}

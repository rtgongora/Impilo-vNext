"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useSessionStore } from "@/stores/sessionStore";

const NAV_SECTIONS = [
  {
    label: "Care Tracking",
    items: [
      { href: "/work", label: "Work Session" },
      { href: "/sorting", label: "Patient Sorting" },
      { href: "/queues", label: "Queue Management" },
    ],
  },
  {
    label: "Facility Ops",
    items: [
      { href: "/control-tower", label: "Control Tower" },
    ],
  },
] as const;

export default function OpsLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  const pathname = usePathname();
  const activeSession = useSessionStore((s) => s.activeSession);

  return (
    <div className="min-h-screen flex">
      {/* Sidebar */}
      <aside className="w-64 bg-white border-r border-neutral-200 flex flex-col">
        <div className="px-5 py-5 border-b border-neutral-200">
          <Link href="/work" className="block">
            <h1 className="text-lg font-semibold text-brand-primary tracking-tight">
              PCT
            </h1>
            <p className="text-xs text-neutral-500 mt-0.5">
              Patient Care Tracker
            </p>
          </Link>
        </div>

        <nav
          className="flex-1 px-3 py-4 overflow-y-auto"
          aria-label="PCT operations navigation"
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

        {/* Active workspace display */}
        <div className="px-4 py-3 border-t border-neutral-200">
          {activeSession ? (
            <div className="space-y-1">
              <div className="flex items-center gap-2">
                <span className="w-2 h-2 rounded-full bg-emerald-500 animate-pulse" />
                <span className="text-xs font-medium text-neutral-900 truncate">
                  {activeSession.actorDisplayName}
                </span>
              </div>
              <p className="text-[11px] text-neutral-500 truncate">
                {activeSession.facilityName}
              </p>
              <p className="text-[11px] text-neutral-500 truncate">
                {activeSession.workspaceName} &middot; {activeSession.dutyMode}
              </p>
              <p className="text-[10px] text-neutral-400">
                Shift: {activeSession.shiftId.slice(0, 8)}...
              </p>
            </div>
          ) : (
            <div className="flex items-center gap-2">
              <span className="w-2 h-2 rounded-full bg-neutral-300" />
              <span className="text-xs text-neutral-500">No active session</span>
            </div>
          )}
        </div>

        <footer className="px-5 py-3 border-t border-neutral-200 text-[11px] text-neutral-400">
          PCT v0.1.0 &mdash; Patient Care Tracker
        </footer>
      </aside>

      {/* Main content */}
      <main className="flex-1 overflow-y-auto">
        {children}
      </main>
    </div>
  );
}

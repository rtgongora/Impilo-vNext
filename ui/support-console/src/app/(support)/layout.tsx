"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useSupportSession } from "@/stores/sessionStore";

const NAV_ITEMS = [
  { href: "/dashboard", label: "Dashboard", icon: "chart" },
  { href: "/tickets", label: "Tickets", icon: "ticket" },
  { href: "/incidents", label: "Incidents", icon: "alert" },
  { href: "/knowledge", label: "Knowledge", icon: "book" },
  { href: "/messaging", label: "Messages", icon: "message" },
] as const;

function NavIcon({ icon }: { icon: string }) {
  switch (icon) {
    case "chart":
      return (
        <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
          <path strokeLinecap="round" strokeLinejoin="round" d="M3 13h4v8H3zM10 9h4v12h-4zM17 5h4v16h-4z" />
        </svg>
      );
    case "ticket":
      return (
        <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
          <path strokeLinecap="round" strokeLinejoin="round" d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2" />
        </svg>
      );
    case "alert":
      return (
        <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
          <path strokeLinecap="round" strokeLinejoin="round" d="M12 9v2m0 4h.01M5.07 19h13.86c1.54 0 2.5-1.67 1.73-3L13.73 4c-.77-1.33-2.69-1.33-3.46 0L3.34 16c-.77 1.33.19 3 1.73 3z" />
        </svg>
      );
    case "book":
      return (
        <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
          <path strokeLinecap="round" strokeLinejoin="round" d="M12 6.253v13m0-13C10.832 5.477 9.246 5 7.5 5S4.168 5.477 3 6.253v13C4.168 18.477 5.754 18 7.5 18s3.332.477 4.5 1.253m0-13C13.168 5.477 14.754 5 16.5 5c1.747 0 3.332.477 4.5 1.253v13C19.832 18.477 18.247 18 16.5 18c-1.746 0-3.332.477-4.5 1.253" />
        </svg>
      );
    case "message":
      return (
        <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
          <path strokeLinecap="round" strokeLinejoin="round" d="M8 10h.01M12 10h.01M16 10h.01M21 12c0 4.418-4.03 8-9 8a9.863 9.863 0 01-4.255-.949L3 20l1.395-3.72C3.512 15.042 3 13.574 3 12c0-4.418 4.03-8 9-8s9 3.582 9 8z" />
        </svg>
      );
    default:
      return null;
  }
}

export default function SupportLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  const pathname = usePathname();
  const session = useSupportSession((s) => s.session);

  return (
    <div className="min-h-screen flex">
      {/* Sidebar */}
      <aside className="w-60 bg-card border-r border-neutral-200 flex flex-col sticky top-0 h-screen">
        <div className="p-4 border-b border-neutral-100">
          <Link href="/dashboard" className="text-lg font-semibold text-brand-primary tracking-tight">
            Support Console
          </Link>
          <p className="text-xs text-neutral-500 mt-0.5">Impilo Helpdesk</p>
        </div>

        <nav className="flex-1 p-3 space-y-0.5" aria-label="Main navigation">
          {NAV_ITEMS.map(({ href, label, icon }) => {
            const isActive = pathname === href || pathname.startsWith(href + "/");
            return (
              <Link
                key={href}
                href={href}
                className={`flex items-center gap-2.5 px-3 py-2 rounded-lg text-sm font-medium transition-colors ${
                  isActive
                    ? "bg-brand-primary/10 text-brand-primary"
                    : "text-neutral-600 hover:text-neutral-900 hover:bg-neutral-100"
                }`}
                aria-current={isActive ? "page" : undefined}
              >
                <NavIcon icon={icon} />
                {label}
              </Link>
            );
          })}
        </nav>

        {/* Agent info */}
        <div className="p-4 border-t border-neutral-100">
          <div className="flex items-center gap-2">
            <div className="w-8 h-8 rounded-full bg-brand-primary/10 flex items-center justify-center text-brand-primary text-xs font-semibold">
              {session?.displayName?.charAt(0)?.toUpperCase() ?? "A"}
            </div>
            <div className="flex-1 min-w-0">
              <p className="text-sm font-medium text-neutral-900 truncate">
                {session?.displayName ?? "Support Agent"}
              </p>
              <p className="text-xs text-neutral-500 truncate">
                {session?.roles?.[0] ?? "SUPPORT_AGENT"}
              </p>
            </div>
          </div>
        </div>
      </aside>

      {/* Main content */}
      <main className="flex-1 min-w-0">
        <div className="max-w-7xl mx-auto px-6 py-8">
          {children}
        </div>
      </main>
    </div>
  );
}

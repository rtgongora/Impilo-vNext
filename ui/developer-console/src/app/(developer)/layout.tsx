"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useDeveloperSession } from "@/stores/sessionStore";
import { EnvironmentBadge } from "@/components/StatusBadge";

const NAV_ITEMS = [
  { href: "/dashboard", label: "Dashboard", icon: "chart" },
  { href: "/clients", label: "Clients", icon: "users" },
  { href: "/certification", label: "Certification", icon: "shield" },
  { href: "/catalog", label: "API Catalog", icon: "book" },
  { href: "/sandbox", label: "Sandbox", icon: "flask" },
  { href: "/federation", label: "Federation", icon: "globe" },
] as const;

function NavIcon({ icon }: { icon: string }) {
  switch (icon) {
    case "chart":
      return (
        <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
          <path strokeLinecap="round" strokeLinejoin="round" d="M3 13h4v8H3zM10 9h4v12h-4zM17 5h4v16h-4z" />
        </svg>
      );
    case "users":
      return (
        <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
          <path strokeLinecap="round" strokeLinejoin="round" d="M17 21v-2a4 4 0 00-4-4H5a4 4 0 00-4 4v2M9 7a4 4 0 100-8 4 4 0 000 8zM23 21v-2a4 4 0 00-3-3.87M16 3.13a4 4 0 010 7.75" />
        </svg>
      );
    case "shield":
      return (
        <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
          <path strokeLinecap="round" strokeLinejoin="round" d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" />
        </svg>
      );
    case "book":
      return (
        <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
          <path strokeLinecap="round" strokeLinejoin="round" d="M12 6.253v13m0-13C10.832 5.477 9.246 5 7.5 5S4.168 5.477 3 6.253v13C4.168 18.477 5.754 18 7.5 18s3.332.477 4.5 1.253m0-13C13.168 5.477 14.754 5 16.5 5c1.747 0 3.332.477 4.5 1.253v13C19.832 18.477 18.247 18 16.5 18c-1.746 0-3.332.477-4.5 1.253" />
        </svg>
      );
    case "flask":
      return (
        <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
          <path strokeLinecap="round" strokeLinejoin="round" d="M9 3h6m-5 0v5.172a2 2 0 01-.586 1.414l-4.828 4.828A2 2 0 004 15.828V17a3 3 0 003 3h10a3 3 0 003-3v-1.172a2 2 0 00-.586-1.414l-4.828-4.828A2 2 0 0114 8.172V3" />
        </svg>
      );
    case "globe":
      return (
        <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
          <circle cx="12" cy="12" r="10" />
          <path strokeLinecap="round" strokeLinejoin="round" d="M2 12h20M12 2a15.3 15.3 0 014 10 15.3 15.3 0 01-4 10 15.3 15.3 0 01-4-10 15.3 15.3 0 014-10z" />
        </svg>
      );
    default:
      return null;
  }
}

export default function DeveloperLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  const pathname = usePathname();
  const session = useDeveloperSession((s) => s.session);
  const environment = useDeveloperSession((s) => s.environment);
  const setEnvironment = useDeveloperSession((s) => s.setEnvironment);

  return (
    <div className="min-h-screen flex">
      {/* Sidebar */}
      <aside className="w-60 bg-card border-r border-neutral-200 flex flex-col sticky top-0 h-screen">
        <div className="p-4 border-b border-neutral-100">
          <Link href="/dashboard" className="text-lg font-semibold text-brand-primary tracking-tight">
            Developer Console
          </Link>
          <p className="text-xs text-neutral-500 mt-0.5">Impilo Partner Portal</p>
        </div>

        {/* Environment selector */}
        <div className="px-4 py-3 border-b border-neutral-100">
          <label className="text-xs font-medium text-neutral-500 uppercase tracking-wide block mb-1.5">
            Environment
          </label>
          <div className="flex gap-1">
            {(["SANDBOX", "PRODUCTION"] as const).map((env) => (
              <button
                key={env}
                onClick={() => setEnvironment(env)}
                className={`flex-1 py-1.5 px-2 rounded-lg text-xs font-medium transition-colors ${
                  environment === env
                    ? env === "SANDBOX"
                      ? "bg-info/10 text-info"
                      : "bg-brand-primary/10 text-brand-primary"
                    : "text-neutral-500 hover:bg-neutral-100"
                }`}
              >
                {env === "SANDBOX" ? "Sandbox" : "Prod"}
              </button>
            ))}
          </div>
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

        {/* User info */}
        <div className="p-4 border-t border-neutral-100">
          <div className="flex items-center gap-2">
            <div className="w-8 h-8 rounded-full bg-brand-primary/10 flex items-center justify-center text-brand-primary text-xs font-semibold">
              {session?.displayName?.charAt(0)?.toUpperCase() ?? "D"}
            </div>
            <div className="flex-1 min-w-0">
              <p className="text-sm font-medium text-neutral-900 truncate">
                {session?.displayName ?? "Developer"}
              </p>
              <p className="text-xs text-neutral-500 truncate">
                {session?.roles?.[0] ?? "DEVELOPER"}
              </p>
            </div>
            <EnvironmentBadge env={environment} />
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

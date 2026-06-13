"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import {
  classifyRouteJourney,
  CLIENT_NAVIGATION,
  MANAGER_NAVIGATION,
  PROVIDER_NAVIGATION,
  type RoleNavigationItem,
} from "@/lib/ui-route-journey-map";
import { useAuthStore } from "@/hooks/useAuthStore";

function isActive(pathname: string, item: RoleNavigationItem): boolean {
  if (pathname === item.route || pathname.startsWith(`${item.route}/`)) return true;
  return (item.aliases ?? []).some(
    (alias) => pathname === alias || pathname.startsWith(`${alias}/`),
  );
}

function navForJourney(
  journey: ReturnType<typeof classifyRouteJourney>,
  roles: string[],
): RoleNavigationItem[] {
  if (journey === "PERSON" || roles.includes("CITIZEN")) {
    return CLIENT_NAVIGATION.slice(0, 6);
  }
  if (journey === "PLATFORM" || roles.some((r) => r.includes("ADMIN"))) {
    return MANAGER_NAVIGATION.slice(0, 6);
  }
  return PROVIDER_NAVIGATION.slice(0, 6);
}

function dedupeNavItems(items: RoleNavigationItem[]): RoleNavigationItem[] {
  const seenRoutes = new Set<string>();
  return items.filter((item) => {
    if (item.route !== "/ask") return true;
    if (seenRoutes.has("/ask")) return false;
    seenRoutes.add("/ask");
    return true;
  });
}

export function RoleJourneyNavigation() {
  const pathname = usePathname() ?? "/home";
  const roles = useAuthStore((s) => s.user?.roles ?? []);
  const journey = classifyRouteJourney(pathname);
  const items = dedupeNavItems(navForJourney(journey, roles));

  if (journey === "DOMAIN_SPECIFIC" && pathname.startsWith("/auth")) {
    return null;
  }

  return (
    <nav
      className="flex flex-wrap items-center gap-1 border-b border-[color:var(--border-soft)] bg-[color:var(--surface-soft)] px-3 py-1.5"
      aria-label="Journey shortcuts"
    >
      {items.map((item) => {
        const active = isActive(pathname, item);
        return (
          <Link
            key={item.route}
            href={item.route}
            className={`rounded-full px-2.5 py-1 text-xs font-medium transition-colors ${
              active
                ? "bg-[color:var(--primary-soft)] text-[color:var(--primary-hover)]"
                : "text-[color:var(--text-muted)] hover:bg-[color:var(--surface)] hover:text-[color:var(--text-primary)]"
            }`}
          >
            {item.label}
          </Link>
        );
      })}
      <Link
        href="/production-command-centre"
        className={`ml-auto rounded-full px-2.5 py-1 text-xs font-medium ${
          pathname.startsWith("/production-command-centre") ||
          pathname.startsWith("/health-os/command-centre")
            ? "bg-primary-soft text-primary-hover"
            : "text-primary hover:bg-primary-soft"
        }`}
      >
        Command Centre
      </Link>
    </nav>
  );
}

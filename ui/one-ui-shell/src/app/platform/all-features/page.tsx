"use client";

import { useMemo, useState } from "react";
import Link from "next/link";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { ROUTES, type RouteDefinition } from "@/lib/routes";
import { classifyRouteJourney } from "@/lib/ui-route-journey-map";
import { LayoutGrid, Search } from "lucide-react";

function groupRoutes(routes: RouteDefinition[]) {
  const groups = new Map<string, RouteDefinition[]>();
  for (const route of routes) {
    const key = route.zone || "other";
    const list = groups.get(key) ?? [];
    list.push(route);
    groups.set(key, list);
  }
  return [...groups.entries()].sort(([a], [b]) => a.localeCompare(b));
}

export default function AllFeaturesPage() {
  const [query, setQuery] = useState("");

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    if (!q) return ROUTES;
    return ROUTES.filter(
      (r) =>
        r.path.toLowerCase().includes(q) ||
        r.pageTitle.toLowerCase().includes(q) ||
        r.navLabel.toLowerCase().includes(q) ||
        r.zone.toLowerCase().includes(q),
    );
  }, [query]);

  const groups = useMemo(() => groupRoutes(filtered), [filtered]);

  return (
    <AppLayout>
      <PageShell
        title="All Features"
        subtitle={`Systematic route catalog for end-to-end testing — ${ROUTES.length} registered routes grouped by zone. Sign in as super@mohcc.gov.zw for broadest access.`}
        icon={<LayoutGrid className="h-6 w-6" />}
      >
        <div className="mb-4 flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
          <label className="relative flex-1 max-w-md">
            <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
            <input
              type="search"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="Filter by path, title, zone…"
              className="impilo-pill-input w-full pl-9"
            />
          </label>
          <p className="text-xs text-muted-foreground">
            Showing {filtered.length} of {ROUTES.length} routes
          </p>
        </div>

        <div className="space-y-6">
          {groups.map(([zone, routes]) => (
            <section key={zone} className="impilo-surface-card p-4">
              <h2 className="text-sm font-semibold uppercase tracking-wide text-muted-foreground">{zone}</h2>
              <ul className="mt-3 grid gap-2 sm:grid-cols-2 lg:grid-cols-3">
                {routes.map((route) => (
                  <li key={route.path}>
                    <Link
                      href={route.path}
                      className="block rounded-xl border border-border bg-background px-3 py-2 text-sm transition hover:border-primary/40 hover:bg-primary-soft/30"
                    >
                      <span className="font-medium text-foreground">{route.pageTitle}</span>
                      <span className="mt-0.5 block truncate font-mono text-[10px] text-muted-foreground">
                        {route.path}
                      </span>
                      <span className="mt-1 inline-block text-[10px] text-muted-foreground">
                        {route.guard}
                        {route.requiredRole ? ` · ${route.requiredRole}` : ""} · {classifyRouteJourney(route.path)}
                      </span>
                    </Link>
                  </li>
                ))}
              </ul>
            </section>
          ))}
        </div>
      </PageShell>
    </AppLayout>
  );
}

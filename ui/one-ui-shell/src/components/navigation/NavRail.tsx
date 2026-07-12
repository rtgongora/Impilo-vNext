"use client";

import { useEffect } from "react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { Menu, PanelLeftClose, PanelLeftOpen } from "lucide-react";
import { ImpiloBrandLogo } from "@/components/brand/ImpiloBrandLogo";
import { useAuthStore } from "@/hooks/useAuthStore";
import { useLayoutPrefsStore } from "@/hooks/useLayoutPrefsStore";
import { useOperationalContextStore } from "@/hooks/useOperationalContextStore";
import { useSessionExperienceContract } from "@/hooks/useSessionExperienceContract";
import { useShellStore } from "@/hooks/useShellStore";
import { isWorkZoneGrantedBySession } from "@/lib/administration-governance";
import { resolveIdentityContext } from "@/lib/identity-context";
import { resolveNavRailMode } from "@/lib/shell/workspace-context";
import { useExperienceEntry } from "@/providers/ExperienceEntryProvider";
import { computeVisibleZones, matchesPath } from "./sidebar-zones";

/**
 * Persistent collapsible navigation rail (≥lg viewports).
 *
 * Expanded (icon + label) on landing/hub surfaces where the user is
 * navigating; auto-compacts to icons when an application or record workspace
 * is opened so the active task gets the width. Hidden entirely in focus mode
 * and on phones/tablets-portrait (the off-canvas drawer serves those).
 *
 * The full drawer (zones, spotlight, context) stays one tap away via the
 * menu button. Pure chrome: mode changes never touch workflow state.
 */
export function NavRail() {
  const pathname = usePathname() ?? "/";
  const { user, hasRole } = useAuthStore();
  const { contract } = useSessionExperienceContract();
  const { currentRoute } = useExperienceEntry();
  const focusedWorkMode = useOperationalContextStore((s) => s.focusedWorkMode);
  const toggleNavDrawer = useShellStore((s) => s.toggleNavDrawer);

  const hydrateFromStorage = useLayoutPrefsStore((s) => s.hydrateFromStorage);
  const navRailPref = useLayoutPrefsStore((s) => s.navRailPref);
  const focusMode = useLayoutPrefsStore((s) => s.focusMode);
  const setNavRailPref = useLayoutPrefsStore((s) => s.setNavRailPref);

  useEffect(() => {
    hydrateFromStorage();
  }, [hydrateFromStorage]);

  const mode = resolveNavRailMode({ pathname, pref: navRailPref, focusMode });

  if (!user || mode === "hidden") return null;

  const citizenOnly = resolveIdentityContext({ user }).isCitizenOnly;
  const zones = computeVisibleZones({
    citizenOnly,
    sessionWorkZone: isWorkZoneGrantedBySession(contract),
    focusedWorkMode: Boolean(focusedWorkMode),
    activeZoneId: currentRoute?.navZone,
    contract,
    hasRole,
  });

  const compact = mode === "compact";

  return (
    <aside
      data-nav-rail
      data-mode={mode}
      aria-label="Primary navigation"
      className={[
        "hidden shrink-0 flex-col border-r border-[color:var(--border-soft)] bg-[color:var(--surface)]/70 lg:flex",
        "transition-[width] duration-200",
        compact ? "w-16" : "w-60",
      ].join(" ")}
    >
      <div className={`flex h-11 shrink-0 items-center border-b border-[color:var(--border-soft)] ${compact ? "justify-center" : "gap-2 px-3"}`}>
        <button
          type="button"
          onClick={() => toggleNavDrawer()}
          className="inline-flex h-8 w-8 items-center justify-center rounded-lg text-[color:var(--text-secondary)] transition hover:bg-[color:var(--surface-soft)] hover:text-[color:var(--primary-hover)]"
          title="All zones and context"
          aria-label="Open full navigation menu"
        >
          <Menu className="h-4 w-4" />
        </button>
        {!compact ? (
          <Link href="/home" className="min-w-0" aria-label="Home">
            <ImpiloBrandLogo variant="full" tone="brand" size={18} />
          </Link>
        ) : null}
      </div>

      <nav className="flex-1 overflow-y-auto px-2 py-2" aria-label="Zone navigation">
        {zones.map((zone) => (
          <section key={zone.id} className="mb-3">
            {!compact ? (
              <p className="mb-1 px-2 text-[10px] font-semibold uppercase tracking-[0.2em] text-[color:var(--text-muted)]">
                {zone.label}
              </p>
            ) : (
              <p className="sr-only">{zone.label}</p>
            )}
            <div className="space-y-0.5">
              {zone.items.map((item) => {
                const active = matchesPath(pathname, item.href);
                const Icon = item.icon;
                return (
                  <Link
                    key={`${zone.id}:${item.href}:${item.label}`}
                    href={item.href}
                    title={compact ? item.label : undefined}
                    aria-label={compact ? item.label : undefined}
                    aria-current={active ? "page" : undefined}
                    className={[
                      "flex items-center gap-2.5 rounded-xl text-sm transition",
                      compact ? "h-10 justify-center" : "px-2.5 py-2",
                      active
                        ? "impilo-nav-active font-medium shadow-impilo-card"
                        : "text-[color:var(--text-secondary)] hover:bg-primary-soft hover:text-[color:var(--primary-hover)]",
                    ].join(" ")}
                  >
                    <Icon className="h-4 w-4 shrink-0" />
                    {!compact ? <span className="min-w-0 truncate">{item.label}</span> : null}
                  </Link>
                );
              })}
            </div>
          </section>
        ))}
      </nav>

      <div className={`shrink-0 border-t border-[color:var(--border-soft)] p-2 ${compact ? "flex justify-center" : ""}`}>
        <button
          type="button"
          onClick={() => setNavRailPref(compact ? "expanded" : "compact")}
          className={[
            "inline-flex items-center gap-2 rounded-xl text-xs text-[color:var(--text-secondary)] transition hover:bg-[color:var(--surface-soft)] hover:text-[color:var(--text-primary)]",
            compact ? "h-9 w-9 justify-center" : "w-full px-2.5 py-2",
          ].join(" ")}
          aria-label={compact ? "Expand navigation labels" : "Collapse navigation to icons"}
          title={compact ? "Expand navigation" : "Collapse navigation"}
          aria-pressed={compact}
          data-testid="nav-rail-toggle"
        >
          {compact ? <PanelLeftOpen className="h-4 w-4" /> : <PanelLeftClose className="h-4 w-4" />}
          {!compact ? <span>Collapse</span> : null}
        </button>
      </div>
    </aside>
  );
}

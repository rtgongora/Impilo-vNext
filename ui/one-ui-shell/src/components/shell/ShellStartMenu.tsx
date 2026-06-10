"use client";

import { useMemo } from "react";
import { useRouter } from "next/navigation";
import { Pin, PinOff, X, ShoppingBag, AlertCircle } from "lucide-react";
import { useAuthStore } from "@/hooks/useAuthStore";
import { useShellStore } from "@/hooks/useShellStore";
import { listVisibleShellApps } from "@/lib/shell/app-registry";
import type { AppDefinition, ShellAppCategory } from "@/lib/shell/types";
import { ShellAppIcon } from "@/components/branding/ShellAppIcon";
import { useHealthOsLauncher, type LauncherApp } from "@/hooks/queries/useHealthOsLauncher";

const CATEGORY_LABEL: Record<ShellAppCategory, string> = {
  clinical: "Clinical & care",
  operations: "Operations & commerce",
  registry: "Registry spine",
  finance: "Finance",
  citizen: "Citizen & life",
  intelligence: "Intelligence",
  system: "System",
};

function formatLauncherState(state: LauncherApp["state"]): string {
  switch (state) {
    case "REQUEST_ACCESS":             return "Available in marketplace — request access";
    case "PENDING_APPROVAL":           return "Pending approval";
    case "NOT_AVAILABLE_AT_FACILITY":  return "Not enabled for this facility";
    case "REQUIRES_CONFIGURATION":     return "Awaiting configuration";
    case "TEMPORARILY_UNAVAILABLE":    return "Temporarily unavailable";
    case "SUSPENDED":                  return "Suspended pending review";
    case "DEPRECATED":                 return "Deprecated";
    default:                           return state;
  }
}

export function ShellStartMenu() {
  const router = useRouter();
  const hasRole = useAuthStore((s) => s.hasRole);
  const setStartOpen = useShellStore((s) => s.setStartOpen);
  const setNavDrawerOpen = useShellStore((s) => s.setNavDrawerOpen);
  const pinnedAppCodes = useShellStore((s) => s.pinnedAppCodes);
  const togglePinApp = useShellStore((s) => s.togglePinApp);
  const launchApp = useShellStore((s) => s.launchApp);
  const recentItems = useShellStore((s) => s.recentItems);

  const apps = useMemo(() => listVisibleShellApps(hasRole), [hasRole]);

  const byCategory = useMemo(() => {
    const map = new Map<ShellAppCategory, AppDefinition[]>();
    for (const app of apps) {
      const list = map.get(app.category) ?? [];
      list.push(app);
      map.set(app.category, list);
    }
    return map;
  }, [apps]);

  // Marketplace-installed capabilities (apps + AI skills) for this user/facility.
  // Failures are silent — the static SHELL_APPS catalogue above always renders,
  // preserving existing functionality when the BFF endpoint is unreachable.
  const launcherQuery = useHealthOsLauncher({});
  const marketplaceApps = launcherQuery.data ?? [];

  return (
    <div className="fixed inset-0 z-[10001] flex items-end justify-start">
      <button
        type="button"
        className="absolute inset-0 bg-black/40 backdrop-blur-sm"
        aria-label="Close Start menu"
        onClick={() => setStartOpen(false)}
      />
      <div className="relative mb-[58px] ml-2 w-[min(440px,calc(100vw-1rem))] max-h-[min(72vh,640px)] overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-2xl dark:border-slate-700 dark:bg-slate-950">
        <div className="flex items-center justify-between border-b border-slate-100 px-4 py-3 dark:border-slate-800">
          <div>
            <p className="text-sm font-semibold text-slate-900 dark:text-slate-50">Start</p>
            <p className="text-xs text-slate-500 dark:text-slate-400">Launch apps, utilities, and recent work</p>
          </div>
          <button
            type="button"
            className="rounded-lg p-2 text-slate-400 hover:bg-slate-100 hover:text-slate-700 dark:hover:bg-slate-900"
            onClick={() => setStartOpen(false)}
            aria-label="Close"
          >
            <X className="h-4 w-4" />
          </button>
        </div>

        <div className="max-h-[calc(min(72vh,640px)-56px)] overflow-y-auto px-3 py-3">
          {recentItems.length > 0 ? (
            <section className="mb-4">
              <h3 className="mb-2 px-1 text-[11px] font-semibold uppercase tracking-wide text-slate-400">
                Recent
              </h3>
              <ul className="space-y-1">
                {recentItems.slice(0, 8).map((item) => (
                  <li key={item.id}>
                    <button
                      type="button"
                      onClick={() => {
                        router.push(item.href);
                        setStartOpen(false);
                      }}
                      className="flex w-full items-start gap-2 rounded-lg px-2 py-2 text-left hover:bg-slate-50 dark:hover:bg-slate-900"
                    >
                      <span className="mt-0.5 h-2 w-2 shrink-0 rounded-full bg-impilo-500" />
                      <span className="min-w-0">
                        <span className="block truncate text-sm font-medium text-slate-800 dark:text-slate-100">
                          {item.title}
                        </span>
                        {item.subtitle ? (
                          <span className="block truncate text-xs text-slate-500">{item.subtitle}</span>
                        ) : null}
                      </span>
                    </button>
                  </li>
                ))}
              </ul>
            </section>
          ) : null}

          {marketplaceApps.length > 0 ? (
            <section className="mb-4">
              <h3 className="mb-2 flex items-center gap-1.5 px-1 text-[11px] font-semibold uppercase tracking-wide text-slate-400">
                <ShoppingBag className="h-3 w-3" />
                Marketplace apps
              </h3>
              <ul className="grid grid-cols-1 gap-1 sm:grid-cols-2">
                {marketplaceApps.map((app: LauncherApp) => {
                  const isActionable = app.state === "INSTALLED" && !!app.launchUrl;
                  return (
                    <li
                      key={app.id}
                      className="flex items-center gap-1 rounded-lg border border-transparent hover:border-slate-200 hover:bg-slate-50 dark:hover:border-slate-700 dark:hover:bg-slate-900"
                    >
                      <button
                        type="button"
                        disabled={!isActionable}
                        className="flex min-w-0 flex-1 items-center gap-2 px-2 py-2 text-left disabled:cursor-not-allowed disabled:opacity-70"
                        title={app.reason ?? undefined}
                        onClick={() => {
                          if (!isActionable) {
                            router.push(`/marketplace/${encodeURIComponent(app.itemCode)}`);
                          } else if (app.launchUrl) {
                            router.push(app.launchUrl);
                          }
                          setStartOpen(false);
                        }}
                      >
                        <ShellAppIcon
                          icon={app.iconRef ?? "ShoppingBag"}
                          itemCode={app.itemCode}
                          name={app.name}
                          size="card"
                        />
                        <span className="min-w-0">
                          <span className="block truncate text-sm font-medium text-slate-800 dark:text-slate-100">
                            {app.name}
                          </span>
                          <span className="block truncate text-[11px] text-slate-500">
                            {isActionable ? app.description : (
                              <span className="inline-flex items-center gap-1 text-amber-700 dark:text-amber-400">
                                <AlertCircle className="h-3 w-3" />
                                {formatLauncherState(app.state)}
                              </span>
                            )}
                          </span>
                        </span>
                      </button>
                    </li>
                  );
                })}
              </ul>
            </section>
          ) : null}

          {Array.from(byCategory.entries()).map(([category, list]) => (
            <section key={category} className="mb-4">
              <h3 className="mb-2 px-1 text-[11px] font-semibold uppercase tracking-wide text-slate-400">
                {CATEGORY_LABEL[category]}
              </h3>
              <ul className="grid grid-cols-1 gap-1 sm:grid-cols-2">
                {list.map((app) => {
                  const pinned = pinnedAppCodes.includes(app.appCode);
                  return (
                    <li
                      key={app.appCode}
                      className="flex items-center gap-1 rounded-lg border border-transparent hover:border-slate-200 hover:bg-slate-50 dark:hover:border-slate-700 dark:hover:bg-slate-900"
                    >
                      <button
                        type="button"
                        className="flex min-w-0 flex-1 items-center gap-2 px-2 py-2 text-left"
                        onClick={() => launchApp(app, (href) => router.push(href))}
                      >
                        <ShellAppIcon icon={app.icon} serviceSlug={app.serviceSlug} name={app.name} size="card" />
                        <span className="min-w-0">
                          <span className="block truncate text-sm font-medium text-slate-800 dark:text-slate-100">
                            {app.name}
                          </span>
                          <span className="block truncate text-[11px] text-slate-500">{app.description}</span>
                        </span>
                      </button>
                      <button
                        type="button"
                        className="mr-1 shrink-0 rounded-md p-2 text-slate-400 hover:bg-slate-200 hover:text-slate-700 dark:hover:bg-slate-800"
                        title={pinned ? "Unpin from taskbar" : "Pin to taskbar"}
                        onClick={() => togglePinApp(app.appCode)}
                      >
                        {pinned ? <PinOff className="h-4 w-4" /> : <Pin className="h-4 w-4" />}
                      </button>
                    </li>
                  );
                })}
              </ul>
            </section>
          ))}

          <div className="border-t border-slate-100 px-1 py-3 dark:border-slate-800">
            <button
              type="button"
              className="w-full rounded-xl border border-slate-200 px-3 py-2.5 text-left text-sm font-medium text-slate-800 transition hover:bg-slate-50 dark:border-slate-700 dark:text-slate-100 dark:hover:bg-slate-900"
              onClick={() => {
                setStartOpen(false);
                setNavDrawerOpen(true);
              }}
            >
              Full navigation map (Work · Professional · Life)
            </button>
            <p className="mt-1 px-1 text-[11px] text-slate-500">
              Same destinations as before — now off-canvas so the workspace stays wide.
            </p>
          </div>
        </div>
      </div>
    </div>
  );
}

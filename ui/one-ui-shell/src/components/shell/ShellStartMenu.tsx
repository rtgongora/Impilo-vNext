"use client";

import { useMemo } from "react";
import { useRouter } from "next/navigation";
import { Bell, Pin, PinOff, X, ShoppingBag, AlertCircle, Zap } from "lucide-react";
import { useQuery } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";
import { useAuthStore } from "@/hooks/useAuthStore";
import { useShellStore } from "@/hooks/useShellStore";
import { listVisibleShellApps, listQuickActions } from "@/lib/shell/app-registry";
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
  const quickActions = useMemo(() => listQuickActions(hasRole), [hasRole]);
  const pinnedApps = useMemo(
    () => pinnedAppCodes.map((code) => apps.find((a) => a.appCode === code)).filter(Boolean) as AppDefinition[],
    [apps, pinnedAppCodes],
  );

  // Pending work signal — silent on failure so the launcher never degrades.
  const unreadQuery = useQuery<{ data?: { count?: number } }>({
    queryKey: ["shell-inbox-unread"],
    queryFn: () => apiClient.get("/internal/v1/notifications/inbox/unread-count"),
    refetchInterval: 60_000,
    retry: false,
  });
  const unread = unreadQuery.data?.data?.count ?? 0;

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
      <div className="relative mb-[58px] ml-2 w-[min(440px,calc(100vw-1rem))] max-h-[min(72vh,640px)] overflow-hidden rounded-2xl border border-border bg-card shadow-2xl dark:border-border dark:bg-card">
        <div className="flex items-center justify-between border-b border-border px-4 py-3 dark:border-border">
          <div>
            <p className="text-sm font-semibold text-foreground dark:text-foreground">Start</p>
            <p className="text-xs text-muted-foreground dark:text-muted-foreground">Launch apps, utilities, and recent work</p>
          </div>
          <button
            type="button"
            className="rounded-lg p-2 text-muted-foreground hover:bg-primary-soft hover:text-foreground dark:hover:bg-primary-soft"
            onClick={() => setStartOpen(false)}
            aria-label="Close"
          >
            <X className="h-4 w-4" />
          </button>
        </div>

        <div className="max-h-[calc(min(72vh,640px)-56px)] overflow-y-auto px-3 py-3">
          {unread > 0 ? (
            <button
              type="button"
              className="mb-3 flex w-full items-center gap-2 rounded-lg border border-amber-400/40 bg-amber-500/10 px-3 py-2 text-left text-sm text-foreground hover:bg-amber-500/20"
              onClick={() => {
                router.push("/home/notifications");
                setStartOpen(false);
              }}
            >
              <Bell className="h-4 w-4 text-amber-600" />
              <span className="font-medium">Needs attention</span>
              <span className="ml-auto rounded-full bg-amber-500/20 px-2 py-0.5 text-xs font-semibold text-amber-700">
                {unread} unread
              </span>
            </button>
          ) : null}

          {pinnedApps.length > 0 ? (
            <section className="mb-4">
              <h3 className="mb-2 flex items-center gap-1.5 px-1 text-[11px] font-semibold uppercase tracking-wide text-muted-foreground">
                <Pin className="h-3 w-3" />
                Pinned
              </h3>
              <ul className="grid grid-cols-1 gap-1 sm:grid-cols-2">
                {pinnedApps.map((app) => (
                  <li
                    key={`pin-${app.appCode}`}
                    className="flex items-center gap-1 rounded-lg border border-transparent hover:border-border hover:bg-background dark:hover:border-border dark:hover:bg-primary-soft"
                  >
                    <button
                      type="button"
                      className="flex min-w-0 flex-1 items-center gap-2 px-2 py-2 text-left"
                      onClick={() => launchApp(app, (href) => router.push(href))}
                    >
                      <ShellAppIcon icon={app.icon} serviceSlug={app.serviceSlug} name={app.name} size="card" />
                      <span className="min-w-0">
                        <span className="block truncate text-sm font-medium text-foreground dark:text-foreground">
                          {app.name}
                        </span>
                        <span className="block truncate text-[11px] text-muted-foreground">{app.description}</span>
                      </span>
                    </button>
                    <button
                      type="button"
                      className="mr-1 shrink-0 rounded-md p-2 text-muted-foreground hover:bg-primary-soft hover:text-foreground dark:hover:bg-neutral-900"
                      title="Unpin from taskbar"
                      onClick={() => togglePinApp(app.appCode)}
                    >
                      <PinOff className="h-4 w-4" />
                    </button>
                  </li>
                ))}
              </ul>
            </section>
          ) : null}

          {quickActions.length > 0 ? (
            <section className="mb-4">
              <h3 className="mb-2 flex items-center gap-1.5 px-1 text-[11px] font-semibold uppercase tracking-wide text-muted-foreground">
                <Zap className="h-3 w-3" />
                Quick actions
              </h3>
              <ul className="flex flex-wrap gap-1.5 px-1">
                {quickActions.map((cmd) => (
                  <li key={cmd.id}>
                    <button
                      type="button"
                      title={cmd.description ?? cmd.label}
                      className="rounded-full border border-border px-3 py-1.5 text-xs font-medium text-foreground transition hover:border-primary hover:bg-primary-soft dark:border-border"
                      onClick={() => {
                        if (cmd.action.type === "navigate") router.push(cmd.action.href);
                        setStartOpen(false);
                      }}
                    >
                      {cmd.label}
                    </button>
                  </li>
                ))}
              </ul>
            </section>
          ) : null}

          {recentItems.length > 0 ? (
            <section className="mb-4">
              <h3 className="mb-2 px-1 text-[11px] font-semibold uppercase tracking-wide text-muted-foreground">
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
                      className="flex w-full items-start gap-2 rounded-lg px-2 py-2 text-left hover:bg-background dark:hover:bg-primary-soft"
                    >
                      <span className="mt-0.5 h-2 w-2 shrink-0 rounded-full bg-primary" />
                      <span className="min-w-0">
                        <span className="block truncate text-sm font-medium text-foreground dark:text-foreground">
                          {item.title}
                        </span>
                        {item.subtitle ? (
                          <span className="block truncate text-xs text-muted-foreground">{item.subtitle}</span>
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
              <h3 className="mb-2 flex items-center gap-1.5 px-1 text-[11px] font-semibold uppercase tracking-wide text-muted-foreground">
                <ShoppingBag className="h-3 w-3" />
                Marketplace apps
              </h3>
              <ul className="grid grid-cols-1 gap-1 sm:grid-cols-2">
                {marketplaceApps.map((app: LauncherApp) => {
                  const isActionable = app.state === "INSTALLED" && !!app.launchUrl;
                  return (
                    <li
                      key={app.id}
                      className="flex items-center gap-1 rounded-lg border border-transparent hover:border-border hover:bg-background dark:hover:border-border dark:hover:bg-primary-soft"
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
                          <span className="block truncate text-sm font-medium text-foreground dark:text-foreground">
                            {app.name}
                          </span>
                          <span className="block truncate text-[11px] text-muted-foreground">
                            {isActionable ? app.description : (
                              <span className="inline-flex items-center gap-1 text-warning-foreground dark:text-amber-400">
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
              <h3 className="mb-2 px-1 text-[11px] font-semibold uppercase tracking-wide text-muted-foreground">
                {CATEGORY_LABEL[category]}
              </h3>
              <ul className="grid grid-cols-1 gap-1 sm:grid-cols-2">
                {list.map((app) => {
                  const pinned = pinnedAppCodes.includes(app.appCode);
                  return (
                    <li
                      key={app.appCode}
                      className="flex items-center gap-1 rounded-lg border border-transparent hover:border-border hover:bg-background dark:hover:border-border dark:hover:bg-primary-soft"
                    >
                      <button
                        type="button"
                        className="flex min-w-0 flex-1 items-center gap-2 px-2 py-2 text-left"
                        onClick={() => launchApp(app, (href) => router.push(href))}
                      >
                        <ShellAppIcon icon={app.icon} serviceSlug={app.serviceSlug} name={app.name} size="card" />
                        <span className="min-w-0">
                          <span className="block truncate text-sm font-medium text-foreground dark:text-foreground">
                            {app.name}
                          </span>
                          <span className="block truncate text-[11px] text-muted-foreground">{app.description}</span>
                        </span>
                      </button>
                      <button
                        type="button"
                        className="mr-1 shrink-0 rounded-md p-2 text-muted-foreground hover:bg-primary-soft hover:text-foreground dark:hover:bg-neutral-900"
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

          <div className="border-t border-border px-1 py-3 dark:border-border">
            <button
              type="button"
              className="w-full rounded-xl border border-border px-3 py-2.5 text-left text-sm font-medium text-foreground transition hover:bg-background dark:border-border dark:text-foreground dark:hover:bg-primary-soft"
              onClick={() => {
                setStartOpen(false);
                setNavDrawerOpen(true);
              }}
            >
              Full navigation map (Work · Professional · Life)
            </button>
            <p className="mt-1 px-1 text-[11px] text-muted-foreground">
              Same destinations as before — now off-canvas so the workspace stays wide.
            </p>
          </div>
        </div>
      </div>
    </div>
  );
}

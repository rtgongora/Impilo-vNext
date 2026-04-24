"use client";

import { useMemo } from "react";
import { useRouter } from "next/navigation";
import { Pin, PinOff, X } from "lucide-react";
import { useAuthStore } from "@/hooks/useAuthStore";
import { useShellStore } from "@/hooks/useShellStore";
import { listVisibleShellApps } from "@/lib/shell/app-registry";
import type { AppDefinition, ShellAppCategory } from "@/lib/shell/types";
import { ShellIcon } from "./ShellIcon";

const CATEGORY_LABEL: Record<ShellAppCategory, string> = {
  clinical: "Clinical & care",
  operations: "Operations & commerce",
  registry: "Registry spine",
  finance: "Finance",
  citizen: "Citizen & life",
  intelligence: "Intelligence",
  system: "System",
};

export function ShellStartMenu() {
  const router = useRouter();
  const hasRole = useAuthStore((s) => s.hasRole);
  const setStartOpen = useShellStore((s) => s.setStartOpen);
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
                        <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-md bg-slate-100 dark:bg-slate-800">
                          <ShellIcon name={app.icon} className="h-5 w-5 text-slate-700 dark:text-slate-200" />
                        </span>
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
        </div>
      </div>
    </div>
  );
}

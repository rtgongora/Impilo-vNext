"use client";

import Link from "next/link";
import { Search, Keyboard } from "lucide-react";
import { useShellStore } from "@/hooks/useShellStore";

/**
 * Home surfaces for shell-first navigation: command prompt, session recents, minimal real attention signals.
 */

export function HomeSearchCommandPrompt() {
  const focusSearch = () => useShellStore.getState().focusSearchPalette();

  return (
    <div className="rounded-xl border border-impilo-200 bg-gradient-to-br from-impilo-50 to-white p-4 shadow-sm">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h3 className="text-sm font-semibold text-gray-900">Search & commands</h3>
          <p className="mt-1 text-xs text-gray-600">
            Patients, modules, facilities, tasks, appointments — or type a command. Same as the shell taskbar Search
            <span className="hidden sm:inline"> (Ctrl+K)</span>.
          </p>
        </div>
        <button
          type="button"
          onClick={focusSearch}
          className="inline-flex w-full shrink-0 items-center justify-center gap-2 rounded-lg border border-impilo-300 bg-white px-4 py-2.5 text-sm font-medium text-impilo-700 shadow-sm transition hover:bg-impilo-50 sm:w-auto"
        >
          <Search className="h-4 w-4" aria-hidden />
          Open search
          <span className="hidden items-center gap-1 text-xs text-gray-500 sm:inline-flex" aria-hidden>
            <Keyboard className="h-3.5 w-3.5" />
            Ctrl+K
          </span>
        </button>
      </div>
    </div>
  );
}

export function HomeRecentFromShell() {
  const recentItems = useShellStore((s) => s.recentItems);

  if (recentItems.length === 0) {
    return (
      <div className="rounded-xl border border-dashed border-gray-200 bg-gray-50/80 p-4">
        <h3 className="text-sm font-semibold text-gray-800">Continue where you left off</h3>
        <p className="mt-1 text-xs text-gray-500">
          Open a module or chart from Start or Search — recent places you visit will appear here on this device.
        </p>
      </div>
    );
  }

  return (
    <div className="rounded-xl border border-gray-200 bg-white p-4">
      <div className="mb-3 flex items-center justify-between">
        <h3 className="text-sm font-semibold text-gray-900">Recent on this session</h3>
        <span className="text-[10px] font-medium uppercase tracking-wide text-gray-400">Local</span>
      </div>
      <ul className="divide-y divide-gray-100">
        {recentItems.slice(0, 6).map((item) => (
          <li key={item.id}>
            <Link
              href={item.href}
              className="flex flex-col gap-0.5 py-2.5 text-sm text-impilo-700 hover:text-impilo-900"
            >
              <span className="font-medium">{item.title}</span>
              {item.subtitle ? <span className="text-xs text-gray-500">{item.subtitle}</span> : null}
            </Link>
          </li>
        ))}
      </ul>
    </div>
  );
}

export interface HomeAttentionSignalsProps {
  enabled: boolean;
  queueWaiting: number;
  activeEncounterCount: number;
  hasWorkContext: boolean;
}

/**
 * Only surfaces counts backed by current API/query data — no invented alerts.
 */
export function HomeAttentionFromData({ enabled, queueWaiting, activeEncounterCount, hasWorkContext }: HomeAttentionSignalsProps) {
  if (!enabled || !hasWorkContext) {
    return (
      <div className="rounded-xl border border-gray-100 bg-slate-50 p-4 text-xs text-gray-600">
        <p className="font-medium text-gray-800">Needs attention</p>
        <p className="mt-1">
          Select a workplace to see live queue and encounter signals from your facility when the service is available.
        </p>
      </div>
    );
  }

  const items: { label: string; detail: string; href: string }[] = [];
  if (queueWaiting > 0) {
    items.push({
      label: `${queueWaiting} patient${queueWaiting === 1 ? "" : "s"} waiting`,
      detail: "From facility queue stats",
      href: "/queue/waiting",
    });
  }
  if (activeEncounterCount > 0) {
    items.push({
      label: `${activeEncounterCount} active encounter${activeEncounterCount === 1 ? "" : "s"}`,
      detail: "From your recent encounters list",
      href: "/queue",
    });
  }

  if (items.length === 0) {
    return (
      <div className="rounded-xl border border-gray-100 bg-slate-50 p-4 text-xs text-gray-600">
        <p className="font-medium text-gray-800">Needs attention</p>
        <p className="mt-1">No waiting queue backlog or in-progress encounters in the current snapshot.</p>
      </div>
    );
  }

  return (
    <div className="rounded-xl border border-amber-200 bg-amber-50/60 p-4">
      <h3 className="text-sm font-semibold text-amber-950">Needs attention</h3>
      <ul className="mt-2 space-y-2">
        {items.map((row) => (
          <li key={row.href + row.label}>
            <Link href={row.href} className="block text-sm font-medium text-amber-900 hover:underline">
              {row.label}
            </Link>
            <p className="text-[11px] text-amber-800/80">{row.detail}</p>
          </li>
        ))}
      </ul>
    </div>
  );
}

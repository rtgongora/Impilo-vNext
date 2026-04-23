"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useIsFetching } from "@tanstack/react-query";
import { Activity, Bell, Loader2, X } from "lucide-react";
import { useAssistantNotifications, type AssistantTrayNotification } from "@/hooks/queries/useAssistantNotifications";
import { SHELL_TASKBAR_HEIGHT_PX } from "@/lib/shell/app-registry";

function severityRank(s: string): number {
  switch (s.toUpperCase()) {
    case "CRITICAL":
      return 4;
    case "HIGH":
      return 3;
    case "MEDIUM":
      return 2;
    case "LOW":
    case "INFO":
      return 1;
    default:
      return 0;
  }
}

export function ShellNotificationTray() {
  const router = useRouter();
  const { data: remote = [], isLoading, isError, refetch, isFetching } = useAssistantNotifications(true);
  const pendingFetches = useIsFetching({ stale: false });
  const [open, setOpen] = useState(false);
  const [dismissed, setDismissed] = useState<Set<string>>(() => new Set());
  const rootRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) return;
    const onDoc = (e: MouseEvent) => {
      if (!rootRef.current?.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener("mousedown", onDoc);
    return () => document.removeEventListener("mousedown", onDoc);
  }, [open]);

  const items = useMemo(() => {
    const list = remote.filter((n) => !dismissed.has(n.id));
    return [...list].sort((a, b) => severityRank(b.severity) - severityRank(a.severity));
  }, [remote, dismissed]);

  const badgeCount = useMemo(
    () => items.filter((n) => n.severity === "CRITICAL" || n.severity === "HIGH").length,
    [items],
  );

  return (
    <div ref={rootRef} className="relative flex items-center gap-1">
      <button
        type="button"
        onClick={() => {
          setOpen((v) => !v);
          if (!open) void refetch();
        }}
        className="relative flex h-8 w-8 items-center justify-center rounded-md border border-slate-200 bg-white text-slate-700 shadow-sm hover:bg-slate-50 dark:border-slate-600 dark:bg-slate-900 dark:text-slate-100 dark:hover:bg-slate-800"
        aria-label={open ? "Close notifications" : "Open notifications"}
        title="Assistant notifications"
      >
        {isLoading || isFetching ? (
          <Loader2 className="h-4 w-4 animate-spin text-impilo-500" />
        ) : (
          <Bell className="h-4 w-4" />
        )}
        {badgeCount > 0 ? (
          <span className="absolute -right-1 -top-1 flex h-4 min-w-4 items-center justify-center rounded-full bg-red-500 px-0.5 text-[9px] font-bold text-white">
            {badgeCount > 9 ? "9+" : badgeCount}
          </span>
        ) : null}
      </button>

      <button
        type="button"
        className="relative flex h-8 w-8 items-center justify-center rounded-md border border-slate-200 bg-white text-slate-700 shadow-sm hover:bg-slate-50 dark:border-slate-600 dark:bg-slate-900 dark:text-slate-100 dark:hover:bg-slate-800"
        aria-label="Background data refresh activity"
        title="Background requests (React Query in-flight fetches)"
        onClick={() => router.push("/home")}
      >
        <Activity className="h-4 w-4 text-slate-500" />
        {pendingFetches > 0 ? (
          <span className="absolute -right-0.5 -top-0.5 h-2 w-2 rounded-full bg-impilo-500" />
        ) : null}
      </button>

      {open ? (
        <div
          className="fixed right-4 z-[10002] w-[min(100vw-2rem,22rem)] overflow-hidden rounded-xl border border-slate-200 bg-white shadow-2xl dark:border-slate-700 dark:bg-slate-950"
          style={{ bottom: `calc(${SHELL_TASKBAR_HEIGHT_PX}px + 10px)` }}
        >
          <div className="flex items-center justify-between border-b border-slate-100 px-3 py-2 dark:border-slate-800">
            <p className="text-xs font-semibold uppercase tracking-wide text-slate-500">Notifications</p>
            <button
              type="button"
              className="rounded p-1 text-slate-400 hover:bg-slate-100 dark:hover:bg-slate-800"
              aria-label="Close panel"
              onClick={() => setOpen(false)}
            >
              <X className="h-4 w-4" />
            </button>
          </div>
          <div className="max-h-[50vh] overflow-y-auto">
            {isError ? (
              <p className="px-3 py-4 text-xs text-red-600">Could not load notifications.</p>
            ) : items.length === 0 ? (
              <p className="px-3 py-6 text-center text-xs text-slate-500">No active assistant alerts.</p>
            ) : (
              <ul className="divide-y divide-slate-100 dark:divide-slate-800">
                {items.map((n) => (
                  <TrayRow key={n.id} n={n} onDismiss={() => setDismissed((prev) => new Set(prev).add(n.id))} />
                ))}
              </ul>
            )}
          </div>
          <div className="border-t border-slate-100 px-3 py-2 dark:border-slate-800">
            <Link
              href="/home/notifications"
              className="text-xs font-medium text-impilo-600 hover:underline"
              onClick={() => setOpen(false)}
            >
              View all in My Life →
            </Link>
          </div>
        </div>
      ) : null}
    </div>
  );
}

function TrayRow({ n, onDismiss }: { n: AssistantTrayNotification; onDismiss: () => void }) {
  const tone =
    n.severity === "CRITICAL"
      ? "border-l-red-500 bg-red-50/80 dark:bg-red-950/30"
      : n.severity === "HIGH"
        ? "border-l-amber-500 bg-amber-50/80 dark:bg-amber-950/20"
        : "border-l-slate-300 bg-slate-50/80 dark:bg-slate-900/40";

  return (
    <li className={`border-l-4 px-3 py-2.5 text-left ${tone}`}>
      <p className="text-xs font-semibold text-slate-900 dark:text-slate-100">{n.title}</p>
      <p className="mt-0.5 text-[11px] leading-snug text-slate-600 dark:text-slate-300">{n.body}</p>
      <div className="mt-1.5 flex flex-wrap items-center gap-2">
        {n.action?.href ? (
          <Link
            href={n.action.href}
            className="text-[11px] font-medium text-impilo-600 hover:underline"
            onClick={() => {
              /* shell popover closes via navigation */
            }}
          >
            {n.action.label}
          </Link>
        ) : null}
        {n.dismissible ? (
          <button type="button" className="text-[10px] text-slate-400 hover:text-slate-600" onClick={onDismiss}>
            Dismiss
          </button>
        ) : null}
      </div>
    </li>
  );
}

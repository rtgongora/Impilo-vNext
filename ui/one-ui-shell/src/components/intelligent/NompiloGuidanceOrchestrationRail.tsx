"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { Bell, BookOpen, Loader2, Sparkles } from "lucide-react";
import { useReminders } from "@/hooks/queries/useGuidance";
import { buildNompiloRouteContext } from "@/lib/nompilo-route-context";

export function NompiloGuidanceOrchestrationRail() {
  const pathname = usePathname() ?? "/guidance";
  const routeContext = buildNompiloRouteContext(pathname);
  const { data: reminders, isLoading } = useReminders();
  const reminderList = Array.isArray(reminders?.data) ? reminders.data : [];
  const activeReminders = reminderList.filter((r) => !r.dismissed).length;

  return (
    <section
      className="mb-6 rounded-2xl border border-violet-200 bg-gradient-to-r from-violet-50 to-card p-4"
      data-testid="nompilo-guidance-orchestration-rail"
    >
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <p className="flex items-center gap-2 text-sm font-semibold text-violet-900">
            <Sparkles className="h-4 w-4" />
            Nompilo guidance orchestration
          </p>
          <p className="mt-1 text-xs text-violet-800" data-testid="nompilo-route-context">
            Route context: <span className="font-mono">{routeContext.routePath}</span> · {routeContext.surface} journey
          </p>
        </div>
        <Link
          href={`/ask?from=${encodeURIComponent(routeContext.routePath)}`}
          className="inline-flex rounded-xl bg-violet-600 px-3 py-2 text-xs font-medium text-white hover:bg-violet-700"
        >
          Ask with context
        </Link>
      </div>
      <div className="mt-3 flex flex-wrap gap-3 text-xs">
        <span className="inline-flex items-center gap-1 rounded-full bg-card/80 px-2.5 py-1 text-violet-900">
          <Bell className="h-3.5 w-3.5" />
          {isLoading ? "Loading reminders…" : `${activeReminders} active reminder(s)`}
        </span>
        <Link
          href="/guidance/reminders"
          className="inline-flex items-center gap-1 rounded-full border border-violet-200 bg-card px-2.5 py-1 text-violet-900 hover:border-violet-300"
        >
          Reminders
        </Link>
        <Link
          href="/guidance/education"
          className="inline-flex items-center gap-1 rounded-full border border-violet-200 bg-card px-2.5 py-1 text-violet-900 hover:border-violet-300"
        >
          <BookOpen className="h-3.5 w-3.5" />
          Education
        </Link>
      </div>
      {isLoading ? (
        <div className="mt-2 flex items-center gap-2 text-xs text-violet-700">
          <Loader2 className="h-3.5 w-3.5 animate-spin" />
          Probing guidance plane…
        </div>
      ) : null}
    </section>
  );
}

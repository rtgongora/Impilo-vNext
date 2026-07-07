"use client";

import { useMemo } from "react";
import {
  Activity,
  AlertTriangle,
  Bell,
  CalendarClock,
  CheckCircle2,
  ClipboardList,
  HeartPulse,
  Loader2,
  Route,
} from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useAuthStore } from "@/hooks/useAuthStore";
import {
  flattenTimeline,
  useWellnessTimeline,
  type TimelineEvent,
} from "@/hooks/queries/useSimbaWellnessCore";

const CATEGORY_ICON: Record<string, React.ReactNode> = {
  ASSESSMENT: <ClipboardList className="h-4 w-4" />,
  CHECK_IN: <CheckCircle2 className="h-4 w-4" />,
  MEASUREMENT: <HeartPulse className="h-4 w-4" />,
  GOAL: <Activity className="h-4 w-4" />,
  ENROLMENT: <Route className="h-4 w-4" />,
  RISK_CHANGE: <AlertTriangle className="h-4 w-4" />,
  FOLLOW_UP: <ClipboardList className="h-4 w-4" />,
  ESCALATION: <AlertTriangle className="h-4 w-4" />,
  REMINDER: <Bell className="h-4 w-4" />,
  COMMS: <Bell className="h-4 w-4" />,
};

const SEVERITY_RING: Record<string, string> = {
  POSITIVE: "bg-emerald-100 text-emerald-700",
  INFO: "bg-slate-100 text-slate-600",
  ATTENTION: "bg-amber-100 text-amber-700",
  URGENT: "bg-red-100 text-red-700",
};

/** Person-facing wellness timeline — the longitudinal story of a person's wellness journey. */
export default function WellnessTimelinePage() {
  const user = useAuthStore((s) => s.user);
  const cpid = user?.id;

  const q = useWellnessTimeline(cpid);
  const events = useMemo(() => flattenTimeline(q.data?.pages), [q.data]);

  return (
    <AppLayout>
      <PageShell
        title="Wellness timeline"
        subtitle="Everything on your wellness journey, in one place."
        icon={<CalendarClock className="h-6 w-6" />}
      >
        <div className="mx-auto max-w-2xl">
          {q.isLoading && (
            <div className="flex items-center justify-center py-16 text-slate-400">
              <Loader2 className="h-6 w-6 animate-spin" />
            </div>
          )}

          {q.isError && (
            <div className="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
              We could not load your timeline. Please try again.
            </div>
          )}

          {!q.isLoading && !q.isError && events.length === 0 && (
            <div className="rounded-xl border border-dashed border-slate-200 bg-white px-6 py-12 text-center">
              <CalendarClock className="mx-auto mb-3 h-8 w-8 text-slate-300" />
              <p className="text-sm text-slate-500">
                Your wellness story starts here. Take a wellness assessment or start a journey to see
                events appear.
              </p>
              <a
                href="/wellness/assessment"
                className="mt-4 inline-flex items-center gap-2 rounded-lg bg-emerald-600 px-4 py-2 text-sm font-medium text-white"
              >
                Take your assessment
              </a>
            </div>
          )}

          <ol className="relative space-y-4 border-l border-slate-200 pl-6">
            {events.map((e: TimelineEvent) => (
              <li key={e.eventId} className="relative">
                <span
                  className={`absolute -left-[34px] flex h-7 w-7 items-center justify-center rounded-full ${
                    SEVERITY_RING[e.severity] ?? SEVERITY_RING.INFO
                  }`}
                >
                  {CATEGORY_ICON[e.eventCategory] ?? <Activity className="h-4 w-4" />}
                </span>
                <div className="rounded-xl border border-slate-200 bg-white p-4">
                  <div className="flex items-start justify-between gap-2">
                    <h3 className="text-sm font-semibold text-slate-800">{e.title}</h3>
                    <time className="whitespace-nowrap text-xs text-slate-400">
                      {new Date(e.occurredAt).toLocaleString()}
                    </time>
                  </div>
                  {e.summary && <p className="mt-1 text-sm text-slate-600">{e.summary}</p>}
                </div>
              </li>
            ))}
          </ol>

          {q.hasNextPage && (
            <div className="mt-6 text-center">
              <button
                type="button"
                onClick={() => void q.fetchNextPage()}
                disabled={q.isFetchingNextPage}
                className="inline-flex items-center gap-2 rounded-lg border border-slate-200 px-4 py-2 text-sm font-medium text-slate-700 disabled:opacity-50"
              >
                {q.isFetchingNextPage && <Loader2 className="h-4 w-4 animate-spin" />}
                Load more
              </button>
            </div>
          )}
        </div>
      </PageShell>
    </AppLayout>
  );
}

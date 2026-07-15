"use client";

import { useMemo } from "react";
import { CalendarClock, ClipboardList, Loader2 } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useAuthStore } from "@/hooks/useAuthStore";
import { useFollowUps } from "@/hooks/queries/useSimbaWellnessCore";

interface FollowUp {
  followUpId?: string;
  id?: number;
  actionType?: string;
  title?: string;
  detail?: string;
  dueDate?: string | null;
  status?: string;
  severity?: string;
  createdByProvider?: string;
  createdAt?: string;
}

const STATUS_TONE: Record<string, string> = {
  OPEN: "bg-amber-100 text-amber-700",
  IN_PROGRESS: "bg-sky-100 text-sky-700",
  DONE: "bg-emerald-100 text-emerald-700",
  CANCELLED: "bg-slate-100 text-slate-500",
};

const SEVERITY_TONE: Record<string, string> = {
  URGENT: "text-red-600",
  PRIORITY: "text-amber-600",
  ROUTINE: "text-slate-500",
};

/** Citizen wellness follow-ups — actions a provider or coach has set for the person. */
export default function WellnessFollowUpsPage() {
  const user = useAuthStore((s) => s.user);
  const cpid = user?.id;

  const q = useFollowUps(cpid);
  const followUps = useMemo<FollowUp[]>(() => {
    const raw = q.data?.data;
    return Array.isArray(raw) ? (raw as FollowUp[]) : [];
  }, [q.data]);

  return (
    <AppLayout>
      <PageShell
        title="My follow-ups"
        subtitle="Wellness actions your care team has recommended for you."
        icon={<ClipboardList className="h-6 w-6" />}
      >
        <div className="mx-auto max-w-2xl space-y-4">
          {q.isLoading && (
            <div className="flex items-center justify-center py-16 text-slate-400">
              <Loader2 className="h-6 w-6 animate-spin" />
            </div>
          )}

          {q.isError && (
            <div className="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
              We could not load your follow-ups. Please try again.
            </div>
          )}

          {!q.isLoading && !q.isError && followUps.length === 0 && (
            <div className="rounded-xl border border-dashed border-slate-200 bg-white px-6 py-12 text-center">
              <ClipboardList className="mx-auto mb-3 h-8 w-8 text-slate-300" />
              <p className="text-sm text-slate-500">
                No follow-ups right now. Your care team will add actions here when needed.
              </p>
            </div>
          )}

          <ul className="space-y-3">
            {followUps.map((f, i) => (
              <li
                key={f.followUpId ?? f.id ?? i}
                className="rounded-xl border border-slate-200 bg-white p-4"
              >
                <div className="flex items-start justify-between gap-2">
                  <h3 className="text-sm font-semibold text-slate-800">
                    {f.title ?? f.actionType ?? "Follow-up"}
                  </h3>
                  <span
                    className={`rounded-full px-2 py-0.5 text-xs font-medium ${
                      STATUS_TONE[f.status ?? "OPEN"] ?? STATUS_TONE.OPEN
                    }`}
                  >
                    {f.status ?? "OPEN"}
                  </span>
                </div>
                {f.detail && <p className="mt-1 text-sm text-slate-600">{f.detail}</p>}
                <div className="mt-2 flex items-center gap-4 text-xs text-slate-400">
                  {f.dueDate && (
                    <span className="inline-flex items-center gap-1">
                      <CalendarClock className="h-3.5 w-3.5" />
                      Due {new Date(f.dueDate).toLocaleDateString()}
                    </span>
                  )}
                  {f.severity && (
                    <span className={SEVERITY_TONE[f.severity] ?? SEVERITY_TONE.ROUTINE}>
                      {f.severity}
                    </span>
                  )}
                </div>
              </li>
            ))}
          </ul>
        </div>
      </PageShell>
    </AppLayout>
  );
}

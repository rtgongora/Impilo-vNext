"use client";

import { useMemo } from "react";
import { Bell, CalendarClock, CheckCircle2, Clock, Loader2 } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useAuthStore } from "@/hooks/useAuthStore";
import {
  useReminders,
  useSnoozeReminder,
  useCompleteReminder,
} from "@/hooks/queries/useSimbaWellnessCore";

function asRows(payload: unknown): Array<Record<string, unknown>> {
  const data = (payload as { data?: unknown })?.data ?? payload;
  return Array.isArray(data) ? (data as Array<Record<string, unknown>>) : [];
}

const STATUS_STYLE: Record<string, string> = {
  SCHEDULED: "bg-slate-100 text-slate-600",
  DUE: "bg-amber-100 text-amber-700",
  FIRED: "bg-blue-100 text-blue-700",
  SNOOZED: "bg-slate-100 text-slate-500",
  COMPLETED: "bg-emerald-100 text-emerald-700",
  CANCELLED: "bg-slate-100 text-slate-400",
};

/** Preventive wellness reminders — screening / follow-up / habit nudges (delivered by Khuluma). */
export default function WellnessRemindersPage() {
  const user = useAuthStore((s) => s.user);
  const cpid = user?.id;

  const q = useReminders(cpid);
  const snooze = useSnoozeReminder();
  const complete = useCompleteReminder();

  const reminders = useMemo(() => asRows(q.data), [q.data]);

  function snoozeOneWeek(reminderId: string) {
    const until = new Date();
    until.setDate(until.getDate() + 7);
    snooze.mutate({ reminderId, until: until.toISOString() });
  }

  return (
    <AppLayout>
      <PageShell
        title="Reminders"
        subtitle="Gentle nudges to stay on top of your wellness."
        icon={<Bell className="h-6 w-6" />}
      >
        <div className="mx-auto max-w-2xl space-y-3">
          {q.isLoading && (
            <div className="flex items-center justify-center py-16 text-slate-400">
              <Loader2 className="h-6 w-6 animate-spin" />
            </div>
          )}

          {!q.isLoading && reminders.length === 0 && (
            <div className="rounded-xl border border-dashed border-slate-200 bg-white px-6 py-12 text-center text-sm text-slate-500">
              No reminders yet. Start a wellness journey and we will help you keep on track.
            </div>
          )}

          {reminders.map((r) => {
            const id = String(r.reminderId);
            const status = String(r.status);
            const done = status === "COMPLETED" || status === "CANCELLED";
            return (
              <div key={id} className="rounded-xl border border-slate-200 bg-white p-4">
                <div className="flex items-start justify-between gap-3">
                  <div>
                    <div className="flex items-center gap-2">
                      <h3 className="text-sm font-semibold text-slate-800">{String(r.title)}</h3>
                      <span
                        className={`rounded-full px-2 py-0.5 text-xs font-medium ${
                          STATUS_STYLE[status] ?? STATUS_STYLE.SCHEDULED
                        }`}
                      >
                        {status}
                      </span>
                    </div>
                    {r.detail ? (
                      <p className="mt-1 text-sm text-slate-600">{String(r.detail)}</p>
                    ) : null}
                    <p className="mt-2 flex items-center gap-1 text-xs text-slate-400">
                      <CalendarClock className="h-3.5 w-3.5" />
                      Due {new Date(String(r.dueAt)).toLocaleString()}
                    </p>
                  </div>
                  {!done && (
                    <div className="flex shrink-0 gap-2">
                      <button
                        type="button"
                        onClick={() => snoozeOneWeek(id)}
                        disabled={snooze.isPending}
                        className="inline-flex items-center gap-1 rounded-lg border border-slate-200 px-2.5 py-1.5 text-xs font-medium text-slate-600"
                      >
                        <Clock className="h-3.5 w-3.5" />
                        Snooze
                      </button>
                      <button
                        type="button"
                        onClick={() => complete.mutate({ reminderId: id })}
                        disabled={complete.isPending}
                        className="inline-flex items-center gap-1 rounded-lg bg-emerald-600 px-2.5 py-1.5 text-xs font-medium text-white"
                      >
                        <CheckCircle2 className="h-3.5 w-3.5" />
                        Done
                      </button>
                    </div>
                  )}
                </div>
              </div>
            );
          })}
        </div>
      </PageShell>
    </AppLayout>
  );
}

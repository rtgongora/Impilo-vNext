"use client";

/**
 * Screening Schedule — Health OS §5, §6
 * Route: /wellness/screenings | Zone: wellness | Guard: auth
 *
 * Wave 4: surfaces screening reminders from guidance-service via BFF
 * GET /internal/v1/guidance/reminders (SCREENING type). No fabricated schedule rows.
 */

import Link from "next/link";
import { AlertCircle, CalendarCheck, Clock, Loader2 } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { FeatureMaturityBadge } from "@/components/FeatureMaturityBadge";
import { useReminders, type Reminder } from "@/hooks/queries/useGuidance";
import { useScreeningProgrammes } from "@/hooks/queries/useSimba";

function extractReminders(payload: unknown): Reminder[] {
  if (!payload || typeof payload !== "object") return [];
  const root = payload as Record<string, unknown>;
  if (Array.isArray(root.data)) return root.data as Reminder[];
  if (Array.isArray(root)) return root as Reminder[];
  return [];
}

function priorityTone(priority: Reminder["priority"]): string {
  if (priority === "HIGH") return "border-red-200 bg-red-50 text-red-700";
  if (priority === "MEDIUM") return "border-amber-200 bg-amber-50 text-amber-700";
  return "border-green-200 bg-green-50 text-green-700";
}

function extractProgrammes(payload: unknown): Array<Record<string, unknown>> {
  if (!payload) return [];
  if (Array.isArray(payload)) return payload as Array<Record<string, unknown>>;
  if (typeof payload === "object" && Array.isArray((payload as { data?: unknown }).data)) {
    return (payload as { data: Array<Record<string, unknown>> }).data;
  }
  return [];
}

export default function WellnessScreeningsPage() {
  const remindersQ = useReminders();
  const programmesQ = useScreeningProgrammes();
  const programmes = extractProgrammes(programmesQ.data);
  const allReminders = extractReminders(remindersQ.data);
  const screeningReminders = allReminders.filter(
    (r) => r.type === "SCREENING" && !r.dismissed,
  );

  const overdueCount = screeningReminders.filter((r) => {
    if (!r.dueDate) return false;
    return new Date(r.dueDate).getTime() < Date.now();
  }).length;
  const dueSoonCount = screeningReminders.filter((r) => {
    if (!r.dueDate) return !r.dismissed;
    const due = new Date(r.dueDate).getTime();
    const now = Date.now();
    const week = 7 * 24 * 60 * 60 * 1000;
    return due >= now && due <= now + week;
  }).length;
  const upToDateCount = Math.max(0, screeningReminders.length - overdueCount - dueSoonCount);

  return (
    <AppLayout>
      <PageShell
        title="Screening Schedule"
        subtitle="Due and upcoming preventive screenings from your guidance reminders — not fabricated clinical schedules"
        icon={<CalendarCheck className="h-6 w-6" />}
      >
        <div className="mb-4 flex flex-wrap items-center gap-3">
          <FeatureMaturityBadge
            status={programmes.length > 0 ? "wired" : "partial"}
            detail="Reminders from guidance-service; national screening programmes from GET /internal/v1/wellness/screening-programmes (Simba SOR)."
          />
          <Link
            href="/guidance/reminders"
            className="text-sm font-medium text-impilo-600 underline-offset-2 hover:underline"
          >
            All health reminders
          </Link>
        </div>

        <div className="space-y-6">
          {programmes.length > 0 && (
            <div className="rounded-lg border border-emerald-200 bg-emerald-50 p-4">
              <h3 className="text-sm font-semibold text-emerald-900 mb-2">National screening programmes (Simba)</h3>
              <ul className="space-y-2">
                {programmes.map((p, idx) => (
                  <li key={String(p.programmeId ?? p.programme_id ?? idx)} className="text-sm text-emerald-900">
                    <span className="font-medium">{String(p.title ?? p.programmeCode ?? "Programme")}</span>
                    {p.frequencyMonths != null || p.frequency_months != null ? (
                      <span className="text-emerald-700">
                        {" "}
                        — every {String(p.frequencyMonths ?? p.frequency_months)} months
                      </span>
                    ) : null}
                  </li>
                ))}
              </ul>
            </div>
          )}

          <div className="rounded-lg border border-slate-200 bg-slate-50 p-4 text-sm text-slate-700">
            <p className="font-medium text-slate-900">Integration boundary</p>
            <p className="mt-1">
              Personal due dates come from guidance reminders only — not fabricated. Programme catalogue is
              governed by Simba; individual scheduling still depends on guidance + clinical context.
            </p>
          </div>

          {remindersQ.isLoading ? (
            <p className="flex items-center gap-2 text-sm text-slate-500">
              <Loader2 className="h-4 w-4 animate-spin" /> Loading screening reminders…
            </p>
          ) : remindersQ.isError ? (
            <div className="flex items-start gap-2 rounded-lg border border-red-200 bg-red-50 p-4 text-sm text-red-800">
              <AlertCircle className="mt-0.5 h-4 w-4 shrink-0" />
              <div>
                <p className="font-medium">Could not load screening reminders</p>
                <p className="mt-1">
                  The guidance BFF endpoint{" "}
                  <code className="text-xs">GET /internal/v1/guidance/reminders</code> failed. Try again
                  later or open{" "}
                  <Link href="/ask" className="font-semibold underline">
                    Nompilo guidance
                  </Link>
                  .
                </p>
              </div>
            </div>
          ) : (
            <>
              <div className="grid grid-cols-3 gap-4">
                {[
                  { label: "Overdue", count: overdueCount, Icon: AlertCircle, color: "border-red-200 bg-red-50 text-red-700" },
                  { label: "Due Soon", count: dueSoonCount, Icon: Clock, color: "border-amber-200 bg-amber-50 text-amber-700" },
                  { label: "Tracked", count: upToDateCount, Icon: CalendarCheck, color: "border-green-200 bg-green-50 text-green-700" },
                ].map(({ label, count, Icon, color }) => (
                  <div key={label} className={`rounded-lg border p-4 text-center ${color}`}>
                    <Icon className="mx-auto h-6 w-6 mb-1" />
                    <p className="text-2xl font-bold">{count}</p>
                    <p className="text-sm font-medium">{label}</p>
                  </div>
                ))}
              </div>

              {screeningReminders.length === 0 ? (
                <div className="rounded-lg border border-dashed border-gray-300 bg-gray-50 p-8 text-center">
                  <CalendarCheck className="mx-auto h-10 w-10 text-gray-300" />
                  <p className="mt-3 text-sm font-medium text-gray-900">No screening reminders returned</p>
                  <p className="mt-1 text-sm text-gray-600">
                    Guidance-service did not return active SCREENING-type reminders for your profile.
                  </p>
                </div>
              ) : (
                <div className="space-y-3">
                  {screeningReminders.map((reminder) => (
                    <div
                      key={reminder.id}
                      className="flex items-center justify-between rounded-lg border border-gray-200 bg-white p-4 hover:shadow-sm transition-all"
                    >
                      <div className="flex-1">
                        <div className="flex items-center gap-2 mb-1">
                          <h3 className="font-semibold text-gray-900">{reminder.title}</h3>
                          <span
                            className={`rounded border px-2 py-0.5 text-xs font-medium ${priorityTone(reminder.priority)}`}
                          >
                            {reminder.priority}
                          </span>
                        </div>
                        <p className="text-sm text-gray-600">{reminder.description}</p>
                        {reminder.dueDate ? (
                          <p className="text-xs text-gray-400 mt-1">
                            Due: {new Date(reminder.dueDate).toLocaleDateString()}
                          </p>
                        ) : (
                          <p className="text-xs text-gray-400 mt-1">No due date on reminder payload</p>
                        )}
                      </div>
                      <Link
                        href="/guidance/reminders"
                        className="ml-4 rounded-lg border border-gray-300 px-3 py-1.5 text-sm text-gray-700 hover:bg-gray-50 transition-colors"
                      >
                        View in reminders
                      </Link>
                    </div>
                  ))}
                </div>
              )}
            </>
          )}
        </div>
      </PageShell>
    </AppLayout>
  );
}

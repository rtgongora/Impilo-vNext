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
import { GlassSurface, LuminousStage } from "shared-ui";
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
  if (priority === "HIGH") return "border-danger/28 bg-danger-soft text-danger";
  if (priority === "MEDIUM") return "border-warning/35 bg-warning-soft text-warning-foreground";
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
        <LuminousStage className="space-y-6 p-5 sm:p-6">
        <div className="mb-4 flex flex-wrap items-center gap-3">
          <FeatureMaturityBadge
            status={programmes.length > 0 ? "connected" : "partial"}
            detail="Reminders from guidance-service; national screening programmes from GET /internal/v1/wellness/screening-programmes (Simba SOR)."
          />
          <Link
            href="/guidance/reminders"
            className="text-sm font-medium text-primary underline-offset-2 hover:underline"
          >
            All health reminders
          </Link>
        </div>

        <div className="space-y-6">
          {programmes.length > 0 && (
            <div className="rounded-lg border border-success/25 bg-success-soft p-4">
              <h3 className="text-sm font-semibold text-primary-hover mb-2">National screening programmes (Simba)</h3>
              <ul className="space-y-2">
                {programmes.map((p, idx) => (
                  <li key={String(p.programmeId ?? p.programme_id ?? idx)} className="text-sm text-primary-hover">
                    <span className="font-medium">{String(p.title ?? p.programmeCode ?? "Programme")}</span>
                    {p.frequencyMonths != null || p.frequency_months != null ? (
                      <span className="text-primary-hover">
                        {" "}
                        — every {String(p.frequencyMonths ?? p.frequency_months)} months
                      </span>
                    ) : null}
                  </li>
                ))}
              </ul>
            </div>
          )}

          <div className="rounded-lg border border-border bg-background p-4 text-sm text-foreground">
            <p className="font-medium text-foreground">Integration boundary</p>
            <p className="mt-1">
              Personal due dates come from guidance reminders only — not fabricated. Programme catalogue is
              governed by Simba; individual scheduling still depends on guidance + clinical context.
            </p>
          </div>

          {remindersQ.isLoading ? (
            <p className="flex items-center gap-2 text-sm text-muted-foreground">
              <Loader2 className="h-4 w-4 animate-spin" /> Loading screening reminders…
            </p>
          ) : remindersQ.isError ? (
            <div className="flex items-start gap-2 rounded-lg border border-danger/28 bg-danger-soft p-4 text-sm text-red-800">
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
                  { label: "Overdue", count: overdueCount, Icon: AlertCircle, color: "border-danger/28 bg-danger-soft text-danger" },
                  { label: "Due Soon", count: dueSoonCount, Icon: Clock, color: "border-warning/35 bg-warning-soft text-warning-foreground" },
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
                <div className="rounded-lg border border-dashed border-border bg-background p-8 text-center">
                  <CalendarCheck className="mx-auto h-10 w-10 text-muted-foreground" />
                  <p className="mt-3 text-sm font-medium text-foreground">No screening reminders returned</p>
                  <p className="mt-1 text-sm text-muted-foreground">
                    Guidance-service did not return active SCREENING-type reminders for your profile.
                  </p>
                </div>
              ) : (
                <div className="space-y-3">
                  {screeningReminders.map((reminder) => (
                    <GlassSurface
                      key={reminder.id}
                      className="flex items-center justify-between p-4 transition-all hover:shadow-glow-teal"
                    >
                      <div className="flex-1">
                        <div className="flex items-center gap-2 mb-1">
                          <h3 className="font-semibold text-foreground">{reminder.title}</h3>
                          <span
                            className={`rounded border px-2 py-0.5 text-xs font-medium ${priorityTone(reminder.priority)}`}
                          >
                            {reminder.priority}
                          </span>
                        </div>
                        <p className="text-sm text-muted-foreground">{reminder.description}</p>
                        {reminder.dueDate ? (
                          <p className="text-xs text-muted-foreground mt-1">
                            Due: {new Date(reminder.dueDate).toLocaleDateString()}
                          </p>
                        ) : (
                          <p className="text-xs text-muted-foreground mt-1">No due date on reminder payload</p>
                        )}
                      </div>
                      <Link
                        href="/guidance/reminders"
                        className="ml-4 rounded-lg border border-border px-3 py-1.5 text-sm text-foreground hover:bg-background transition-colors"
                      >
                        View in reminders
                      </Link>
                    </GlassSurface>
                  ))}
                </div>
              )}
            </>
          )}
        </div>
        </LuminousStage>
      </PageShell>
    </AppLayout>
  );
}

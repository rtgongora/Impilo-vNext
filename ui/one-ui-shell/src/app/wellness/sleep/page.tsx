"use client";

import { useMemo, useState } from "react";
import { Moon, Star, BedDouble, Sun, Plus, X, Loader2 } from "lucide-react";
import { GlassSurface, LuminousStage } from "shared-ui";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useAuthStore } from "@/hooks/useAuthStore";
import { useWellnessActivities, useLogWellnessActivity } from "@/hooks/queries/useCitizenWellness";

function shortDayLabel(isoDate: string): string {
  const d = new Date(isoDate.slice(0, 10) + "T12:00:00");
  return ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"][d.getDay()] ?? isoDate.slice(5, 10);
}

function parseQuality(q: string | undefined): number {
  if (q == null || q === "") return 3;
  const n = Number(q);
  return Number.isFinite(n) && n >= 1 && n <= 5 ? n : 3;
}

/** Sleep & Recovery — persisted on daily wellness activity row (BFF), same as mobile. */
export default function SleepPage() {
  const patientId = useAuthStore((s) => s.user?.id);
  const today = new Date().toISOString().slice(0, 10);
  const { data: activities = [], isLoading, isError } = useWellnessActivities(patientId, 14);
  const logActivity = useLogWellnessActivity(patientId);

  const todayRow = activities.find((a) => (a.activityDate || "").slice(0, 10) === today);

  const chartDays = useMemo(() => {
    const withDate = [...activities]
      .filter((a) => (a.activityDate || "").length >= 10)
      .sort((a, b) => (a.activityDate || "").localeCompare(b.activityDate || ""));
    const last = withDate.slice(-7);
    return last.map((a) => ({
      day: shortDayLabel(a.activityDate),
      hours: a.sleepHours ?? 0,
      quality: parseQuality(a.sleepQuality),
      iso: a.activityDate.slice(0, 10),
    }));
  }, [activities]);

  const lastWithSleep = useMemo(() => {
    for (const a of activities) {
      if (a.sleepHours != null && a.sleepHours > 0) {
        return { hours: a.sleepHours, quality: parseQuality(a.sleepQuality), date: a.activityDate };
      }
    }
    return null;
  }, [activities]);

  const lastNight = lastWithSleep ?? { hours: chartDays.at(-1)?.hours ?? 0, quality: chartDays.at(-1)?.quality ?? 3, date: "" };
  const avgHours =
    chartDays.length > 0 ? chartDays.reduce((s, d) => s + d.hours, 0) / chartDays.length : lastNight.hours;
  const recoveryScore = Math.round(Math.min(((avgHours / 8) * 70 + (lastNight.quality / 5) * 30), 100));
  const maxBar = 10;

  const [showForm, setShowForm] = useState(false);
  const [form, setForm] = useState({ bedtime: "22:30", wakeTime: "06:30", quality: 4 });

  const submitSleep = () => {
    if (!patientId) return;
    const [bh, bm] = form.bedtime.split(":").map(Number);
    const [wh, wm] = form.wakeTime.split(":").map(Number);
    let duration = wh + wm / 60 - (bh + bm / 60);
    if (duration < 0) duration += 24;
    const hours = Math.round(duration * 10) / 10;

    logActivity.mutate(
      {
        patientId,
        steps: todayRow?.steps ?? 0,
        waterMl: todayRow?.waterMl ?? 0,
        activeMinutes: todayRow?.activeMinutes ?? 0,
        distanceKm: todayRow?.distanceKm ?? 0,
        caloriesBurned: todayRow?.caloriesBurned ?? 0,
        sleepHours: hours,
        sleepQuality: form.quality,
      },
      { onSuccess: () => setShowForm(false) },
    );
  };

  return (
    <AppLayout>
      <PageShell title="Sleep & Recovery" subtitle="Sleep hours and quality sync to your wellness activity log" icon={<Moon className="h-6 w-6" />}>
        <LuminousStage className="space-y-6 p-5 sm:p-6">
        {!patientId && (
          <p className="text-sm text-warning-foreground bg-warning-soft border border-warning/35 rounded-lg px-4 py-3">Sign in to log sleep.</p>
        )}

        {patientId && isLoading && (
          <div className="flex items-center gap-2 text-muted-foreground py-8">
            <Loader2 className="h-5 w-5 animate-spin" /> Loading…
          </div>
        )}

        {patientId && isError && (
          <p className="text-sm text-danger bg-danger-soft border border-danger/28 rounded-lg px-4 py-3">Could not load sleep history.</p>
        )}

        {patientId && !isLoading && !isError && (
          <>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-6 mb-6">
              <div className="rounded-xl bg-gradient-to-br from-indigo-600 to-purple-600 text-white p-6 shadow-lg">
                <h2 className="font-semibold text-lg mb-4 flex items-center gap-2">
                  <BedDouble className="h-5 w-5" /> Latest logged sleep
                </h2>
                <div className="grid grid-cols-2 gap-4">
                  <div>
                    <p className="text-xs text-indigo-200">Date</p>
                    <p className="text-xl font-bold">{(lastNight.date || "").slice(0, 10) || "—"}</p>
                  </div>
                  <div>
                    <p className="text-xs text-indigo-200">Duration</p>
                    <p className="text-xl font-bold">{lastNight.hours}h</p>
                  </div>
                  <div className="col-span-2">
                    <p className="text-xs text-indigo-200">Quality</p>
                    <div className="flex gap-0.5 mt-1">
                      {Array.from({ length: 5 }).map((_, i) => (
                        <Star
                          key={i}
                          className={`h-5 w-5 ${i < lastNight.quality ? "fill-yellow-300 text-yellow-300" : "text-indigo-300"}`}
                        />
                      ))}
                    </div>
                  </div>
                </div>
              </div>

              <GlassSurface className="p-6 flex flex-col items-center justify-center">
                <p className="text-sm font-medium text-muted-foreground mb-3">Recovery Score</p>
                <div className="relative h-32 w-32">
                  <svg viewBox="0 0 36 36" className="h-32 w-32 -rotate-90">
                    <circle cx="18" cy="18" r="15.5" fill="none" stroke="#e5e7eb" strokeWidth="3" />
                    <circle
                      cx="18"
                      cy="18"
                      r="15.5"
                      fill="none"
                      stroke={recoveryScore >= 75 ? "#22c55e" : recoveryScore >= 50 ? "#eab308" : "#ef4444"}
                      strokeWidth="3"
                      strokeDasharray={`${recoveryScore} ${100 - recoveryScore}`}
                      strokeLinecap="round"
                    />
                  </svg>
                  <div className="absolute inset-0 flex flex-col items-center justify-center">
                    <span className="text-3xl font-bold text-foreground">{recoveryScore}%</span>
                    <span className="text-xs text-muted-foreground">{recoveryScore >= 75 ? "Great" : recoveryScore >= 50 ? "Fair" : "Low"}</span>
                  </div>
                </div>
                <p className="text-xs text-muted-foreground mt-2">Based on recent logged sleep (target 8h)</p>
              </GlassSurface>
            </div>

            <GlassSurface className="p-6 mb-6">
              <h3 className="font-semibold text-foreground mb-4">Recent sleep (from wellness log)</h3>
              {chartDays.length === 0 ? (
                <p className="text-sm text-muted-foreground">No rows yet. Log sleep below — data will match the citizen mobile app.</p>
              ) : (
                <div className="flex items-end gap-3 h-44">
                  {chartDays.map((d, i) => (
                    <div key={d.iso + i} className="flex-1 flex flex-col items-center gap-1">
                      <span className="text-xs font-medium text-muted-foreground">{d.hours}h</span>
                      <div
                        className="w-full rounded-t-lg transition-all"
                        style={{
                          height: `${Math.min((d.hours / maxBar) * 100, 100)}%`,
                          background:
                            d.quality >= 4
                              ? "linear-gradient(to top, #6366f1, #818cf8)"
                              : d.quality >= 3
                                ? "linear-gradient(to top, #eab308, #facc15)"
                                : "linear-gradient(to top, #ef4444, #f87171)",
                        }}
                      />
                      <span className="text-xs text-muted-foreground">{d.day}</span>
                    </div>
                  ))}
                </div>
              )}
            </GlassSurface>

            <GlassSurface className="p-6">
              <div className="flex items-center justify-between mb-4">
                <h3 className="font-semibold text-foreground">Log sleep (today)</h3>
                <button
                  type="button"
                  onClick={() => setShowForm(!showForm)}
                  className="text-indigo-600 hover:text-primary-hover text-sm font-medium flex items-center gap-1"
                >
                  {showForm ? <X className="h-4 w-4" /> : <Plus className="h-4 w-4" />}
                  {showForm ? "Cancel" : "New Entry"}
                </button>
              </div>
              {showForm && (
                <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
                  <div>
                    <label className="block text-xs font-medium text-muted-foreground mb-1">
                      <Moon className="h-3 w-3 inline mr-1" />
                      Bedtime
                    </label>
                    <input
                      type="time"
                      value={form.bedtime}
                      onChange={(e) => setForm({ ...form, bedtime: e.target.value })}
                      className="w-full rounded-lg border px-3 py-2 text-sm"
                    />
                  </div>
                  <div>
                    <label className="block text-xs font-medium text-muted-foreground mb-1">
                      <Sun className="h-3 w-3 inline mr-1" />
                      Wake time
                    </label>
                    <input
                      type="time"
                      value={form.wakeTime}
                      onChange={(e) => setForm({ ...form, wakeTime: e.target.value })}
                      className="w-full rounded-lg border px-3 py-2 text-sm"
                    />
                  </div>
                  <div>
                    <label className="block text-xs font-medium text-muted-foreground mb-1">
                      <Star className="h-3 w-3 inline mr-1" />
                      Quality (1-5)
                    </label>
                    <div className="flex gap-1 mt-1">
                      {Array.from({ length: 5 }).map((_, i) => (
                        <button key={i} type="button" onClick={() => setForm({ ...form, quality: i + 1 })} className="focus:outline-none">
                          <Star className={`h-6 w-6 ${i < form.quality ? "fill-yellow-400 text-yellow-400" : "text-muted-foreground"}`} />
                        </button>
                      ))}
                    </div>
                  </div>
                  <div className="col-span-2 md:col-span-4 flex items-center gap-3">
                    <button
                      type="button"
                      onClick={submitSleep}
                      disabled={logActivity.isPending}
                      className="rounded-lg bg-indigo-600 text-white px-6 py-2 text-sm font-medium hover:bg-primary disabled:opacity-60 inline-flex items-center gap-2"
                    >
                      {logActivity.isPending && <Loader2 className="h-4 w-4 animate-spin" />}
                      Save Sleep Entry
                    </button>
                  </div>
                </div>
              )}
            </GlassSurface>
          </>
        )}
        </LuminousStage>
      </PageShell>
    </AppLayout>
  );
}

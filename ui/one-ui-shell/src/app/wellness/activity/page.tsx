"use client";

/**
 * Activity & Fitness — persisted via Experience BFF (same contract as citizen mobile).
 */

import { useState } from "react";
import { Activity, Footprints, Droplets, Timer, Loader2 } from "lucide-react";
import { GlassSurface, LuminousStage } from "shared-ui";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useAuthStore } from "@/hooks/useAuthStore";
import { useWellnessActivities, useLogWellnessActivity } from "@/hooks/queries/useCitizenWellness";

export default function WellnessActivityPage() {
  const patientId = useAuthStore((s) => s.user?.id);
  const { data: activities = [], isLoading, isError, error } = useWellnessActivities(patientId, 14);
  const logActivity = useLogWellnessActivity(patientId);

  const today = new Date().toISOString().slice(0, 10);
  const todayRow = activities.find((a) => (a.activityDate || "").slice(0, 10) === today);

  const [form, setForm] = useState({
    steps: "",
    waterMl: "",
    activeMinutes: "",
    sleepHours: "",
  });

  const save = () => {
    if (!patientId) return;
    logActivity.mutate({
      patientId,
      steps: Number(form.steps) || todayRow?.steps || 0,
      waterMl: Number(form.waterMl) || todayRow?.waterMl || 0,
      activeMinutes: Number(form.activeMinutes) || todayRow?.activeMinutes || 0,
      distanceKm: todayRow?.distanceKm ?? 0,
      caloriesBurned: todayRow?.caloriesBurned ?? 0,
      sleepHours:
        form.sleepHours !== ""
          ? Number(form.sleepHours)
          : todayRow?.sleepHours != null
            ? todayRow.sleepHours
            : undefined,
      sleepQuality: todayRow?.sleepQuality,
    });
  };

  return (
    <AppLayout>
      <PageShell
        title="Activity & Fitness"
        subtitle="Steps, hydration, and active minutes — synced with your mobile wellness log"
        icon={<Activity className="h-6 w-6" />}
      >
        <LuminousStage className="space-y-6 p-5 sm:p-6">
        {!patientId && (
          <p className="text-sm text-warning-foreground bg-warning-soft border border-warning/35 rounded-lg px-4 py-3">
            Sign in to track activity against your Impilo ID.
          </p>
        )}

        {patientId && isLoading && (
          <div className="flex items-center gap-2 text-muted-foreground py-8">
            <Loader2 className="h-5 w-5 animate-spin" /> Loading your activity…
          </div>
        )}

        {patientId && isError && (
          <p className="text-sm text-danger bg-danger-soft border border-danger/28 rounded-lg px-4 py-3">
            Could not load wellness data. {error instanceof Error ? error.message : "Try again later."}
          </p>
        )}

        {patientId && !isLoading && !isError && (
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
            <GlassSurface className="p-6">
              <h3 className="font-semibold text-foreground mb-4 flex items-center gap-2">
                <Footprints className="h-5 w-5 text-primary" /> Today
              </h3>
              <div className="grid grid-cols-2 gap-4 text-center">
                <div className="rounded-lg bg-primary-soft p-4">
                  <p className="text-2xl font-bold text-primary">{todayRow?.steps ?? 0}</p>
                  <p className="text-xs text-primary font-medium">Steps</p>
                </div>
                <div className="rounded-lg bg-cyan-50 p-4">
                  <p className="text-2xl font-bold text-cyan-700">{todayRow?.waterMl ?? 0}</p>
                  <p className="text-xs text-cyan-600 font-medium">Water (ml)</p>
                </div>
                <div className="rounded-lg bg-violet-50 p-4">
                  <p className="text-2xl font-bold text-violet-700">{todayRow?.activeMinutes ?? 0}</p>
                  <p className="text-xs text-violet-600 font-medium">Active min</p>
                </div>
                <div className="rounded-lg bg-info-soft p-4">
                  <p className="text-2xl font-bold text-primary-hover">{todayRow?.sleepHours != null ? todayRow.sleepHours : "—"}</p>
                  <p className="text-xs text-indigo-600 font-medium">Sleep (h)</p>
                </div>
              </div>
            </GlassSurface>

            <GlassSurface className="p-6">
              <h3 className="font-semibold text-foreground mb-4">Log or update today</h3>
              <div className="space-y-3">
                <label className="block text-xs font-medium text-muted-foreground">
                  <Footprints className="h-3 w-3 inline mr-1" />
                  Steps
                </label>
                <input
                  data-testid="wellness-activity-steps"
                  type="number"
                  min={0}
                  placeholder={String(todayRow?.steps ?? 0)}
                  value={form.steps}
                  onChange={(e) => setForm({ ...form, steps: e.target.value })}
                  className="w-full rounded-lg border px-3 py-2 text-sm"
                />
                <label className="block text-xs font-medium text-muted-foreground">
                  <Droplets className="h-3 w-3 inline mr-1" />
                  Water (ml)
                </label>
                <input
                  type="number"
                  min={0}
                  placeholder={String(todayRow?.waterMl ?? 0)}
                  value={form.waterMl}
                  onChange={(e) => setForm({ ...form, waterMl: e.target.value })}
                  className="w-full rounded-lg border px-3 py-2 text-sm"
                />
                <label className="block text-xs font-medium text-muted-foreground">
                  <Timer className="h-3 w-3 inline mr-1" />
                  Active minutes
                </label>
                <input
                  type="number"
                  min={0}
                  placeholder={String(todayRow?.activeMinutes ?? 0)}
                  value={form.activeMinutes}
                  onChange={(e) => setForm({ ...form, activeMinutes: e.target.value })}
                  className="w-full rounded-lg border px-3 py-2 text-sm"
                />
                <label className="block text-xs font-medium text-muted-foreground">Sleep hours (optional)</label>
                <input
                  type="number"
                  min={0}
                  max={24}
                  step={0.1}
                  placeholder={todayRow?.sleepHours != null ? String(todayRow.sleepHours) : ""}
                  value={form.sleepHours}
                  onChange={(e) => setForm({ ...form, sleepHours: e.target.value })}
                  className="w-full rounded-lg border px-3 py-2 text-sm"
                />
                <button
                  type="button"
                  data-testid="wellness-activity-save"
                  onClick={save}
                  disabled={logActivity.isPending}
                  className="mt-2 w-full rounded-lg bg-primary text-white py-2.5 text-sm font-medium hover:bg-primary-hover disabled:opacity-60 flex items-center justify-center gap-2"
                >
                  {logActivity.isPending && <Loader2 className="h-4 w-4 animate-spin" />}
                  Save to wellness log
                </button>
                {logActivity.isSuccess && !logActivity.isPending && (
                  <p className="text-xs text-green-600">Saved.</p>
                )}
                {logActivity.isError && (
                  <p className="text-xs text-red-600">Save failed. Check you are signed in and the BFF is running.</p>
                )}
              </div>
            </GlassSurface>

            {activities.length > 0 && (
              <GlassSurface className="lg:col-span-2 overflow-hidden p-0">
                <div className="px-4 py-3 bg-background border-b border-border">
                  <h3 className="font-semibold text-foreground">Recent days</h3>
                </div>
                <div className="divide-y divide-gray-100 max-h-64 overflow-y-auto">
                  {activities.slice(0, 10).map((a) => (
                    <div key={a.id + a.activityDate} className="px-4 py-2 flex flex-wrap gap-4 text-sm text-foreground">
                      <span className="font-medium w-28">{(a.activityDate || "").slice(0, 10)}</span>
                      <span>{a.steps} steps</span>
                      <span>{a.waterMl} ml water</span>
                      <span>{a.activeMinutes} active min</span>
                      {a.sleepHours != null && <span>{a.sleepHours}h sleep</span>}
                    </div>
                  ))}
                </div>
              </GlassSurface>
            )}
          </div>
        )}
        </LuminousStage>
      </PageShell>
    </AppLayout>
  );
}

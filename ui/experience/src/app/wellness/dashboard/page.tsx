"use client";

/**
 * SIMBA wellness dashboard — personal view across SIMBA wellness domains.
 * Route: /wellness/dashboard
 */

import type { ReactNode } from "react";
import Link from "next/link";
import {
  Activity,
  Apple,
  Award,
  Flame,
  LayoutDashboard,
  Loader2,
  Moon,
  Smile,
  Target,
  Trophy,
  Users2,
} from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useAuthStore } from "@/hooks/useAuthStore";
import {
  useWellnessProfile,
  useActivities,
  useSleepSegments,
  useDietEntries,
  useMoodLog,
  useWellnessGoals,
  useWellnessClubs,
  useWellnessChallenges,
} from "@/hooks/queries/useSimba";

const DEMO_CPID = "00000000-0000-0000-0000-000000000001";

function unwrapList(res: unknown): Record<string, unknown>[] {
  if (!res) return [];
  if (Array.isArray(res)) return res as Record<string, unknown>[];
  const r = res as { data?: unknown };
  if (r.data == null) return [];
  if (Array.isArray(r.data)) return r.data as Record<string, unknown>[];
  const inner = r.data as { items?: unknown };
  if (Array.isArray(inner.items)) return inner.items as Record<string, unknown>[];
  return [];
}

function unwrapProfile(res: unknown): Record<string, unknown> | null {
  if (!res || typeof res !== "object") return null;
  const r = res as { data?: unknown };
  if (r.data != null && typeof r.data === "object" && !Array.isArray(r.data)) {
    return r.data as Record<string, unknown>;
  }
  if (!("data" in r)) return res as Record<string, unknown>;
  return null;
}

function daysAgoIso(days: number): string {
  const d = new Date();
  d.setDate(d.getDate() - days);
  return d.toISOString().slice(0, 10);
}

function todayIso(): string {
  return new Date().toISOString().slice(0, 10);
}

function rowDate(row: Record<string, unknown>): string {
  const v =
    row.activityDate ??
    row.activity_date ??
    row.recordedAt ??
    row.recorded_at ??
    row.date ??
    row.startDate ??
    row.start_date ??
    row.sleepDate ??
    row.sleep_date ??
    "";
  const s = String(v);
  return s.length >= 10 ? s.slice(0, 10) : s;
}

function moodColor(score: number): string {
  if (score <= 1) return "bg-red-500";
  if (score === 2) return "bg-orange-500";
  if (score === 3) return "bg-amber-400";
  if (score === 4) return "bg-lime-500";
  return "bg-green-500";
}

function progressPercent(row: Record<string, unknown>): number {
  const p =
    row.progressPercentage ??
    row.progress_percentage ??
    row.progressPct ??
    row.progress_pct;
  if (typeof p === "number" && !Number.isNaN(p)) {
    return Math.min(100, Math.max(0, p <= 1 ? p * 100 : p));
  }
  const cur = Number(row.currentValue ?? row.current_value ?? row.current ?? 0);
  const tgt = Number(row.targetValue ?? row.target_value ?? row.target ?? 0);
  if (tgt > 0) return Math.min(100, Math.round((cur / tgt) * 100));
  return 0;
}

function SectionCard({
  title,
  icon,
  isLoading,
  isError,
  children,
}: {
  title: string;
  icon: ReactNode;
  isLoading: boolean;
  isError: boolean;
  children: ReactNode;
}) {
  return (
    <section className="rounded-xl border border-gray-200 bg-white shadow-sm overflow-hidden">
      <div className="flex items-center gap-2 border-b border-gray-100 px-4 py-3 bg-gray-50/80">
        {icon}
        <h2 className="text-sm font-semibold text-gray-800">{title}</h2>
      </div>
      <div className="p-4">
        {isLoading && (
          <div className="flex items-center gap-2 text-gray-600 py-6 text-sm">
            <Loader2 className="h-5 w-5 animate-spin shrink-0" />
            Loading…
          </div>
        )}
        {!isLoading && isError && (
          <p className="text-sm text-red-700 bg-red-50 border border-red-100 rounded-lg px-3 py-2">
            Could not load this section.
          </p>
        )}
        {!isLoading && !isError && children}
      </div>
    </section>
  );
}

export default function WellnessDashboardPage() {
  const authId = useAuthStore((s) => s.user?.id);
  const cpid = authId || DEMO_CPID;

  const profileQ = useWellnessProfile(cpid);
  const activitiesQ = useActivities(cpid);
  const sleepQ = useSleepSegments(cpid);
  const dietQ = useDietEntries(cpid);
  const moodQ = useMoodLog(cpid);
  const goalsQ = useWellnessGoals(cpid);
  const clubsQ = useWellnessClubs();
  const challengesQ = useWellnessChallenges();

  const profile = unwrapProfile(profileQ.data);
  const activities = unwrapList(activitiesQ.data);
  const sleepRows = unwrapList(sleepQ.data);
  const dietRows = unwrapList(dietQ.data);
  const moodRows = unwrapList(moodQ.data);
  const goals = unwrapList(goalsQ.data);
  const clubs = unwrapList(clubsQ.data);
  const challenges = unwrapList(challengesQ.data);

  const since = daysAgoIso(7);
  const recentActivities = activities.filter((a) => rowDate(a) >= since);
  const totalSteps = recentActivities.reduce(
    (sum, a) => sum + Number(a.steps ?? a.stepCount ?? a.step_count ?? 0),
    0,
  );
  const totalCalories = recentActivities.reduce(
    (sum, a) => sum + Number(a.caloriesBurned ?? a.calories_burned ?? a.calories ?? 0),
    0,
  );

  const sortedSleep = [...sleepRows].sort(
    (a, b) => rowDate(b).localeCompare(rowDate(a)),
  );
  const lastNight = sortedSleep[0];
  const sleepLast7 = sortedSleep.filter((s) => rowDate(s) >= since);
  const sleepHours = (r: Record<string, unknown>) =>
    Number(r.durationHours ?? r.duration_hours ?? r.totalHours ?? r.total_hours ?? r.hours ?? 0);
  const weeklySleepAvg =
    sleepLast7.length > 0
      ? sleepLast7.reduce((s, r) => s + sleepHours(r), 0) / sleepLast7.length
      : 0;

  const today = todayIso();
  const todayMeals = dietRows.filter((d) => rowDate(d) === today);
  const dietCalories = todayMeals.reduce(
    (s, d) => s + Number(d.calories ?? d.totalCalories ?? d.total_calories ?? 0),
    0,
  );
  const protein = todayMeals.reduce(
    (s, d) => s + Number(d.proteinG ?? d.protein_g ?? d.protein ?? 0),
    0,
  );
  const carbs = todayMeals.reduce(
    (s, d) => s + Number(d.carbsG ?? d.carbs_g ?? d.carbohydratesG ?? 0),
    0,
  );
  const fat = todayMeals.reduce((s, d) => s + Number(d.fatG ?? d.fat_g ?? d.fat ?? 0), 0);

  const moodSorted = [...moodRows]
    .sort((a, b) => rowDate(b).localeCompare(rowDate(a)))
    .slice(0, 7)
    .reverse();

  const goalsSummary =
    (profile?.goalsSummary as string) ??
    (profile?.goals_summary as string) ??
    (Array.isArray(profile?.focusAreas) ? (profile?.focusAreas as string[]).join(", ") : null);
  const streak =
    Number(profile?.activeStreak ?? profile?.active_streak ?? profile?.streakDays ?? profile?.streak_days ?? 0) ||
    0;

  return (
    <AppLayout>
      <PageShell
        title="My Wellness Dashboard"
        subtitle="Your SIMBA wellness snapshot — activity, sleep, diet, mood, goals, clubs, and challenges"
        icon={<LayoutDashboard className="h-6 w-6" />}
      >
        <div className="mb-4">
          <Link
            href="/wellness"
            className="inline-flex items-center gap-1 text-sm text-gray-500 hover:text-gray-700"
          >
            ← Back to Wellness Hub
          </Link>
        </div>

        {!authId && (
          <p className="mb-4 text-xs text-amber-800 bg-amber-50 border border-amber-200 rounded-lg px-3 py-2">
            Not signed in — showing demo CPID. Sign in to load your profile.
          </p>
        )}

        <div className="flex flex-col gap-6">
          <SectionCard
            title="Wellness Profile"
            icon={<Award className="h-4 w-4 text-teal-600" />}
            isLoading={profileQ.isLoading}
            isError={profileQ.isError}
          >
            {!profile && !profileQ.isFetching ? (
              <p className="text-sm text-gray-500">No profile data returned for this person.</p>
            ) : profile ? (
              <div className="space-y-2 text-sm">
                <p className="text-gray-700">
                  <span className="font-medium text-gray-900">Goals: </span>
                  {goalsSummary || "—"}
                </p>
                <p className="flex items-center gap-2 text-gray-700">
                  <Flame className="h-4 w-4 text-orange-500" />
                  <span className="font-medium text-gray-900">Active streak: </span>
                  {streak} day{streak === 1 ? "" : "s"}
                </p>
              </div>
            ) : null}
          </SectionCard>

          <SectionCard
            title="Activity Summary"
            icon={<Activity className="h-4 w-4 text-impilo-500" />}
            isLoading={activitiesQ.isLoading}
            isError={activitiesQ.isError}
          >
            {recentActivities.length === 0 ? (
              <p className="text-sm text-gray-500">No activity logged in the last 7 days.</p>
            ) : (
              <div className="space-y-3">
                <div className="flex flex-wrap gap-6 text-sm">
                  <span>
                    <strong className="text-gray-900">{totalSteps.toLocaleString()}</strong>{" "}
                    steps (7d)
                  </span>
                  <span>
                    <strong className="text-gray-900">{Math.round(totalCalories)}</strong> kcal
                    burned (7d)
                  </span>
                </div>
                <ul className="divide-y divide-gray-100 border border-gray-100 rounded-lg max-h-48 overflow-y-auto text-xs">
                  {recentActivities.slice(0, 14).map((a, i) => (
                    <li key={i} className="flex justify-between px-3 py-2 text-gray-700">
                      <span>{rowDate(a)}</span>
                      <span>
                        {Number(a.steps ?? a.stepCount ?? 0).toLocaleString()} steps ·{" "}
                        {Number(a.caloriesBurned ?? a.calories ?? 0)} kcal
                      </span>
                    </li>
                  ))}
                </ul>
              </div>
            )}
          </SectionCard>

          <SectionCard
            title="Sleep Summary"
            icon={<Moon className="h-4 w-4 text-indigo-600" />}
            isLoading={sleepQ.isLoading}
            isError={sleepQ.isError}
          >
            {!lastNight && sleepLast7.length === 0 ? (
              <p className="text-sm text-gray-500">No sleep segments recorded.</p>
            ) : (
              <div className="text-sm text-gray-700 space-y-2">
                <p>
                  <span className="font-medium text-gray-900">Last night: </span>
                  {lastNight
                    ? `${sleepHours(lastNight).toFixed(1)} h (${rowDate(lastNight)})`
                    : "—"}
                </p>
                <p>
                  <span className="font-medium text-gray-900">7-day average: </span>
                  {weeklySleepAvg > 0 ? `${weeklySleepAvg.toFixed(1)} h` : "—"}
                </p>
              </div>
            )}
          </SectionCard>

          <SectionCard
            title="Diet Summary"
            icon={<Apple className="h-4 w-4 text-orange-600" />}
            isLoading={dietQ.isLoading}
            isError={dietQ.isError}
          >
            {todayMeals.length === 0 ? (
              <p className="text-sm text-gray-500">No meals logged for today.</p>
            ) : (
              <div className="space-y-2 text-sm">
                <p className="text-gray-700">
                  <span className="font-medium text-gray-900">Today: </span>
                  {todayMeals.length} entr{todayMeals.length === 1 ? "y" : "ies"} ·{" "}
                  <strong>{Math.round(dietCalories)}</strong> kcal
                </p>
                <p className="text-xs text-gray-600">
                  Macros — P: {Math.round(protein)}g · C: {Math.round(carbs)}g · F: {Math.round(fat)}g
                </p>
              </div>
            )}
          </SectionCard>

          <SectionCard
            title="Mood Tracker"
            icon={<Smile className="h-4 w-4 text-pink-600" />}
            isLoading={moodQ.isLoading}
            isError={moodQ.isError}
          >
            {moodSorted.length === 0 ? (
              <p className="text-sm text-gray-500">No mood entries in the last week.</p>
            ) : (
              <div className="flex items-end gap-2">
                {moodSorted.map((m, i) => {
                  const score = Math.min(5, Math.max(1, Math.round(Number(m.score ?? m.mood ?? m.rating ?? 3))));
                  return (
                    <div key={i} className="flex flex-col items-center gap-1">
                      <span
                        title={`Score ${score}`}
                        className={`h-8 w-8 rounded-full ${moodColor(score)} shadow-sm ring-2 ring-white`}
                      />
                      <span className="text-[10px] text-gray-500">{rowDate(m).slice(5)}</span>
                    </div>
                  );
                })}
              </div>
            )}
          </SectionCard>

          <SectionCard
            title="Active Goals"
            icon={<Target className="h-4 w-4 text-green-600" />}
            isLoading={goalsQ.isLoading}
            isError={goalsQ.isError}
          >
            {goals.length === 0 ? (
              <p className="text-sm text-gray-500">No active goals.</p>
            ) : (
              <ul className="space-y-3">
                {goals.map((g, i) => {
                  const id = String(g.goalId ?? g.goal_id ?? g.id ?? i);
                  const label = String(g.title ?? g.name ?? g.goalType ?? g.goal_type ?? "Goal");
                  const pct = progressPercent(g);
                  return (
                    <li key={id}>
                      <div className="flex justify-between text-xs text-gray-600 mb-1">
                        <span className="font-medium text-gray-800">{label}</span>
                        <span>{pct}%</span>
                      </div>
                      <div className="h-2 rounded-full bg-gray-100 overflow-hidden">
                        <div
                          className="h-full rounded-full bg-teal-500 transition-all"
                          style={{ width: `${pct}%` }}
                        />
                      </div>
                    </li>
                  );
                })}
              </ul>
            )}
          </SectionCard>

          <SectionCard
            title="My Clubs"
            icon={<Users2 className="h-4 w-4 text-purple-600" />}
            isLoading={clubsQ.isLoading}
            isError={clubsQ.isError}
          >
            {clubs.length === 0 ? (
              <p className="text-sm text-gray-500">You are not in any clubs yet.</p>
            ) : (
              <ul className="divide-y divide-gray-100 text-sm">
                {clubs.map((c, i) => (
                  <li key={String(c.clubId ?? c.club_id ?? c.id ?? i)} className="py-2 text-gray-800">
                    {String(c.name ?? c.title ?? "Club")}
                  </li>
                ))}
              </ul>
            )}
          </SectionCard>

          <SectionCard
            title="Active Challenges"
            icon={<Trophy className="h-4 w-4 text-amber-600" />}
            isLoading={challengesQ.isLoading}
            isError={challengesQ.isError}
          >
            {challenges.length === 0 ? (
              <p className="text-sm text-gray-500">No active challenges.</p>
            ) : (
              <ul className="space-y-3">
                {challenges.map((ch, i) => {
                  const id = String(ch.challengeId ?? ch.challenge_id ?? ch.id ?? i);
                  const label = String(ch.name ?? ch.title ?? "Challenge");
                  const pct = progressPercent(ch);
                  return (
                    <li key={id}>
                      <div className="flex justify-between text-xs text-gray-600 mb-1">
                        <span className="font-medium text-gray-800">{label}</span>
                        <span>{pct}%</span>
                      </div>
                      <div className="h-2 rounded-full bg-gray-100 overflow-hidden">
                        <div
                          className="h-full rounded-full bg-amber-500 transition-all"
                          style={{ width: `${pct}%` }}
                        />
                      </div>
                    </li>
                  );
                })}
              </ul>
            )}
          </SectionCard>
        </div>
      </PageShell>
    </AppLayout>
  );
}

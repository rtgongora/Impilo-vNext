"use client";

import { useMemo, useState } from "react";
import { HeartHandshake, Check, Flame, Loader2, Plus, Sparkles, X } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useAuthStore } from "@/hooks/useAuthStore";
import { useLogWellnessActivity } from "@/hooks/queries/useCitizenWellness";
import { useAskGuidance } from "@/hooks/queries/useGuidance";
import { useCoachingNudges, useCreateGoal, useWellnessGoals } from "@/hooks/queries/useSimba";

const DAYS = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"];

/** Coaching & Habits — wired to wellness BFF + SIMBA goals + guidance coaching prompts. */
export default function CoachingPage() {
  const user = useAuthStore((s) => s.user);
  const cpid = user?.id;
  const goalsQ = useWellnessGoals(cpid);
  const nudgesQ = useCoachingNudges(cpid);
  const createGoal = useCreateGoal();
  const logActivity = useLogWellnessActivity(cpid);
  const askGuidance = useAskGuidance();

  const [showForm, setShowForm] = useState(false);
  const [newHabit, setNewHabit] = useState("");
  const [completedToday, setCompletedToday] = useState<Set<string>>(new Set());

  const habits = useMemo(() => {
    const raw = goalsQ.data?.data;
    const rows = Array.isArray(raw) ? raw : raw && typeof raw === "object" && Array.isArray((raw as { items?: unknown[] }).items)
      ? (raw as { items: unknown[] }).items
      : [];
    return rows.map((row, index) => {
      const item = row as Record<string, unknown>;
      const id = String(item.id ?? item.goalId ?? `goal-${index}`);
      const name = String(item.title ?? item.description ?? item.name ?? "Wellness goal");
      const streak = Number(item.streak ?? item.progress ?? 0);
      return { id, name, streak, weekDone: DAYS.map(() => false) };
    });
  }, [goalsQ.data]);

  const todayIdx = 6;
  const totalToday = habits.length;
  const doneCount = habits.filter((h) => completedToday.has(h.id)).length;

  function toggleToday(id: string, name: string) {
    setCompletedToday((prev) => {
      const next = new Set(prev);
      if (next.has(id)) {
        next.delete(id);
      } else {
        next.add(id);
        if (cpid) {
          logActivity.mutate({
            patientId: cpid,
            activeMinutes: 5,
            steps: 0,
          });
        }
      }
      return next;
    });
  }

  function addHabit() {
    if (!newHabit.trim() || !cpid) return;
    createGoal.mutate(
      { person_cpid: cpid, title: newHabit.trim(), category: "HABIT", target_value: 7 },
      {
        onSuccess: () => {
          setNewHabit("");
          setShowForm(false);
          void goalsQ.refetch();
        },
      },
    );
  }

  function fetchCoachingTip() {
    askGuidance.mutate({
      question: "Give one practical habit coaching tip for today.",
      personalized: true,
      context: { zone: "wellness", surface: "coaching" },
    });
  }

  const quote = askGuidance.data?.data?.response;
  const nudges = useMemo(() => {
    const payload = nudgesQ.data;
    if (!payload) return [] as Array<Record<string, unknown>>;
    if (Array.isArray(payload)) return payload as Array<Record<string, unknown>>;
    if (Array.isArray((payload as { data?: unknown }).data)) {
      return (payload as { data: Array<Record<string, unknown>> }).data;
    }
    return [] as Array<Record<string, unknown>>;
  }, [nudgesQ.data]);

  return (
    <AppLayout>
      <PageShell title="Coaching & Habits" subtitle="SIMBA goals, wellness activity logging, and Nompilo coaching prompts" icon={<HeartHandshake className="h-6 w-6" />}>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-6 mb-6">
          <div className="rounded-xl bg-gradient-to-br from-violet-600 to-purple-600 text-white p-6 shadow-lg">
            <h2 className="font-semibold text-lg mb-2">Today&apos;s Progress</h2>
            <div className="flex items-end gap-4">
              <p className="text-5xl font-bold">{doneCount}<span className="text-2xl font-normal text-violet-200">/{totalToday || 0}</span></p>
              <p className="text-sm text-violet-200 mb-1">habits completed</p>
            </div>
            <div className="h-3 mt-4 rounded-full bg-white/20 overflow-hidden">
              <div className="h-full rounded-full bg-white transition-all" style={{ width: `${totalToday > 0 ? (doneCount / totalToday) * 100 : 0}%` }} />
            </div>
          </div>
          <div className="rounded-xl border border-gray-200 bg-white shadow-sm p-6 flex flex-col justify-center">
            <Sparkles className="h-6 w-6 text-amber-500 mb-2" />
            {askGuidance.isPending ? (
              <p className="text-sm text-gray-500 flex items-center gap-2"><Loader2 className="h-4 w-4 animate-spin" /> Loading coaching tip…</p>
            ) : (
              <p className="text-gray-800 font-medium italic">
                {quote ? `“${quote}”` : "Tap below for a governed coaching prompt from the guidance service."}
              </p>
            )}
            <button
              type="button"
              onClick={fetchCoachingTip}
              className="mt-3 self-start rounded-lg border border-violet-200 px-3 py-1.5 text-xs font-medium text-violet-700 hover:bg-violet-50"
            >
              Get coaching tip
            </button>
          </div>
        </div>

        {nudges.length > 0 && (
          <div className="rounded-xl border border-amber-200 bg-amber-50 mb-6 p-5">
            <h3 className="font-semibold text-amber-900 mb-2">Coaching nudges (Simba SOR)</h3>
            <ul className="space-y-2">
              {nudges.map((n, idx) => (
                <li key={String(n.nudgeId ?? n.nudge_id ?? idx)} className="text-sm text-amber-900">
                  {String(n.message ?? "")}
                </li>
              ))}
            </ul>
          </div>
        )}

        <div className="rounded-xl border border-gray-200 bg-white shadow-sm mb-6">
          <div className="flex items-center justify-between px-5 py-4 border-b border-gray-100 bg-gray-50">
            <h3 className="font-semibold text-gray-800">Today&apos;s Habits (SIMBA goals)</h3>
            <button onClick={() => setShowForm(!showForm)} className="text-violet-600 hover:text-violet-700 text-sm font-medium flex items-center gap-1">
              {showForm ? <X className="h-4 w-4" /> : <Plus className="h-4 w-4" />}{showForm ? "Cancel" : "New Habit"}
            </button>
          </div>
          {showForm && (
            <div className="px-5 py-3 bg-violet-50 border-b border-violet-100 flex gap-2">
              <input value={newHabit} onChange={(e) => setNewHabit(e.target.value)} placeholder="e.g. Walk for 20 minutes" className="flex-1 rounded-lg border px-3 py-2 text-sm" onKeyDown={(e) => e.key === "Enter" && addHabit()} />
              <button onClick={addHabit} disabled={createGoal.isPending} className="rounded-lg bg-violet-600 text-white px-4 py-2 text-sm font-medium hover:bg-violet-700 disabled:opacity-50">
                {createGoal.isPending ? "Saving…" : "Add"}
              </button>
            </div>
          )}
          {goalsQ.isLoading ? (
            <p className="px-5 py-6 text-sm text-gray-500 flex items-center gap-2"><Loader2 className="h-4 w-4 animate-spin" /> Loading goals…</p>
          ) : habits.length === 0 ? (
            <p className="px-5 py-6 text-sm text-gray-500">No wellness goals yet — add a habit to start tracking.</p>
          ) : (
            <div className="divide-y divide-gray-50">
              {habits.map((h) => (
                <div key={h.id} className="px-5 py-3 flex items-center gap-4">
                  <button onClick={() => toggleToday(h.id, h.name)} className={`h-6 w-6 rounded-md border-2 flex items-center justify-center transition-colors ${completedToday.has(h.id) ? "bg-violet-600 border-violet-600 text-white" : "border-gray-300 hover:border-violet-400"}`}>
                    {completedToday.has(h.id) && <Check className="h-4 w-4" />}
                  </button>
                  <span className={`flex-1 text-sm font-medium ${completedToday.has(h.id) ? "text-gray-400 line-through" : "text-gray-800"}`}>{h.name}</span>
                  <div className="flex items-center gap-1 text-sm">
                    <Flame className={`h-4 w-4 ${h.streak >= 7 ? "text-orange-500" : "text-gray-400"}`} />
                    <span className={`font-semibold ${h.streak >= 7 ? "text-orange-600" : "text-gray-500"}`}>{h.streak}</span>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      </PageShell>
    </AppLayout>
  );
}

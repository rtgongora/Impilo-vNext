"use client";
import { useState } from "react";
import { HeartHandshake, Check, Flame, Plus, X, Sparkles } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";

type Habit = { id: number; name: string; streak: number; weekDone: boolean[] };

const INITIAL_HABITS: Habit[] = [
  { id: 1, name: "Drink 8 glasses of water", streak: 12, weekDone: [true, true, true, false, true, true, true] },
  { id: 2, name: "30 min exercise", streak: 5, weekDone: [true, false, true, true, true, false, true] },
  { id: 3, name: "Eat 5 servings of vegetables", streak: 8, weekDone: [true, true, false, true, true, true, true] },
  { id: 4, name: "Meditate for 10 minutes", streak: 3, weekDone: [false, true, false, false, true, true, true] },
  { id: 5, name: "Sleep before 22:30", streak: 14, weekDone: [true, true, true, true, true, false, true] },
  { id: 6, name: "Take vitamins", streak: 21, weekDone: [true, true, true, true, true, true, true] },
];

const DAYS = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"];

const QUOTES = [
  { text: "Small daily improvements are the key to staggering long-term results.", author: "Robin Sharma" },
  { text: "We are what we repeatedly do. Excellence is not an act, but a habit.", author: "Aristotle" },
  { text: "The secret of getting ahead is getting started.", author: "Mark Twain" },
];

/** Coaching & Habits — Health OS §6: coaching, motivation, and self-improvement. */
export default function CoachingPage() {
  const [habits, setHabits] = useState(INITIAL_HABITS);
  const [showForm, setShowForm] = useState(false);
  const [newHabit, setNewHabit] = useState("");
  const [quote] = useState(QUOTES[Math.floor(Math.random() * QUOTES.length)]);

  const todayIdx = 6; // Sunday (last column)
  const toggleToday = (id: number) => {
    setHabits((prev) => prev.map((h) => {
      if (h.id !== id) return h;
      const weekDone = [...h.weekDone];
      weekDone[todayIdx] = !weekDone[todayIdx];
      const streak = weekDone[todayIdx] ? h.streak + 1 : Math.max(0, h.streak - 1);
      return { ...h, weekDone, streak };
    }));
  };

  const addHabit = () => {
    if (!newHabit.trim()) return;
    setHabits((prev) => [...prev, { id: Date.now(), name: newHabit, streak: 0, weekDone: [false, false, false, false, false, false, false] }]);
    setNewHabit("");
    setShowForm(false);
  };

  const completedToday = habits.filter((h) => h.weekDone[todayIdx]).length;
  const totalToday = habits.length;

  return (
    <AppLayout>
      <PageShell title="Coaching & Habits" subtitle="Build healthy habits and track your streaks" icon={<HeartHandshake className="h-6 w-6" />}>
        {/* Summary + Quote */}
        <div className="grid grid-cols-1 md:grid-cols-2 gap-6 mb-6">
          <div className="rounded-xl bg-gradient-to-br from-violet-600 to-purple-600 text-white p-6 shadow-lg">
            <h2 className="font-semibold text-lg mb-2">Today&apos;s Progress</h2>
            <div className="flex items-end gap-4">
              <p className="text-5xl font-bold">{completedToday}<span className="text-2xl font-normal text-violet-200">/{totalToday}</span></p>
              <p className="text-sm text-violet-200 mb-1">habits completed</p>
            </div>
            <div className="h-3 mt-4 rounded-full bg-white/20 overflow-hidden">
              <div className="h-full rounded-full bg-white transition-all" style={{ width: `${totalToday > 0 ? (completedToday / totalToday) * 100 : 0}%` }} />
            </div>
          </div>
          <div className="rounded-xl border border-gray-200 bg-white shadow-sm p-6 flex flex-col justify-center">
            <Sparkles className="h-6 w-6 text-amber-500 mb-2" />
            <p className="text-gray-800 font-medium italic">&ldquo;{quote.text}&rdquo;</p>
            <p className="text-sm text-gray-500 mt-2">&mdash; {quote.author}</p>
          </div>
        </div>

        {/* Today's habits checklist */}
        <div className="rounded-xl border border-gray-200 bg-white shadow-sm mb-6">
          <div className="flex items-center justify-between px-5 py-4 border-b border-gray-100 bg-gray-50">
            <h3 className="font-semibold text-gray-800">Today&apos;s Habits</h3>
            <button onClick={() => setShowForm(!showForm)} className="text-violet-600 hover:text-violet-700 text-sm font-medium flex items-center gap-1">
              {showForm ? <X className="h-4 w-4" /> : <Plus className="h-4 w-4" />}{showForm ? "Cancel" : "New Habit"}
            </button>
          </div>
          {showForm && (
            <div className="px-5 py-3 bg-violet-50 border-b border-violet-100 flex gap-2">
              <input value={newHabit} onChange={(e) => setNewHabit(e.target.value)} placeholder="e.g. Walk for 20 minutes" className="flex-1 rounded-lg border px-3 py-2 text-sm" onKeyDown={(e) => e.key === "Enter" && addHabit()} />
              <button onClick={addHabit} className="rounded-lg bg-violet-600 text-white px-4 py-2 text-sm font-medium hover:bg-violet-700">Add</button>
            </div>
          )}
          <div className="divide-y divide-gray-50">
            {habits.map((h) => (
              <div key={h.id} className="px-5 py-3 flex items-center gap-4">
                <button onClick={() => toggleToday(h.id)} className={`h-6 w-6 rounded-md border-2 flex items-center justify-center transition-colors ${h.weekDone[todayIdx] ? "bg-violet-600 border-violet-600 text-white" : "border-gray-300 hover:border-violet-400"}`}>
                  {h.weekDone[todayIdx] && <Check className="h-4 w-4" />}
                </button>
                <span className={`flex-1 text-sm font-medium ${h.weekDone[todayIdx] ? "text-gray-400 line-through" : "text-gray-800"}`}>{h.name}</span>
                <div className="flex items-center gap-1 text-sm">
                  <Flame className={`h-4 w-4 ${h.streak >= 7 ? "text-orange-500" : "text-gray-400"}`} />
                  <span className={`font-semibold ${h.streak >= 7 ? "text-orange-600" : "text-gray-500"}`}>{h.streak}</span>
                </div>
              </div>
            ))}
          </div>
        </div>

        {/* Weekly habit grid */}
        <div className="rounded-xl border border-gray-200 bg-white shadow-sm p-5">
          <h3 className="font-semibold text-gray-800 mb-4">Weekly Overview</h3>
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr>
                  <th className="text-left py-2 pr-4 text-gray-500 font-medium">Habit</th>
                  {DAYS.map((d) => <th key={d} className="text-center py-2 px-2 text-gray-500 font-medium w-12">{d}</th>)}
                </tr>
              </thead>
              <tbody>
                {habits.map((h) => (
                  <tr key={h.id} className="border-t border-gray-50">
                    <td className="py-2 pr-4 text-gray-700 font-medium whitespace-nowrap">{h.name}</td>
                    {h.weekDone.map((done, i) => (
                      <td key={i} className="text-center py-2 px-2">
                        <div className={`h-7 w-7 rounded-md mx-auto ${done ? "bg-violet-500" : "bg-gray-100"}`} title={done ? "Completed" : "Missed"} />
                      </td>
                    ))}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <div className="flex gap-4 mt-3 text-xs text-gray-500">
            <span className="flex items-center gap-1"><span className="h-3 w-3 rounded bg-violet-500" />Completed</span>
            <span className="flex items-center gap-1"><span className="h-3 w-3 rounded bg-gray-100" />Missed</span>
          </div>
        </div>
      </PageShell>
    </AppLayout>
  );
}

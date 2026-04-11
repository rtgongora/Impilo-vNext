"use client";
import { useState } from "react";
import { Trophy, Medal, Flame, Star, Target, Zap } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";

type Challenge = { id: number; name: string; desc: string; progress: number; total: number; unit: string; daysLeft: number; icon: string; color: string; joined: boolean };
type Badge = { name: string; icon: typeof Trophy; earned: string; color: string };

const ACTIVE: Challenge[] = [
  { id: 1, name: "10,000 Steps for 30 Days", desc: "Walk 10k steps every day for a month", progress: 18, total: 30, unit: "days", daysLeft: 12, icon: "footprints", color: "from-blue-500 to-cyan-500", joined: true },
  { id: 2, name: "Hydration Hero", desc: "Drink 8 glasses of water daily for 2 weeks", progress: 9, total: 14, unit: "days", daysLeft: 5, icon: "droplets", color: "from-sky-500 to-blue-500", joined: true },
  { id: 3, name: "Early Riser", desc: "Wake before 6am for 21 days", progress: 14, total: 21, unit: "days", daysLeft: 7, icon: "sunrise", color: "from-orange-400 to-amber-500", joined: true },
];

const AVAILABLE: Challenge[] = [
  { id: 4, name: "Meditation Marathon", desc: "Meditate 10 minutes daily for 30 days", progress: 0, total: 30, unit: "days", daysLeft: 30, icon: "brain", color: "from-purple-500 to-indigo-500", joined: false },
  { id: 5, name: "50km Running Month", desc: "Run a total of 50km this month", progress: 0, total: 50, unit: "km", daysLeft: 30, icon: "run", color: "from-red-500 to-pink-500", joined: false },
  { id: 6, name: "Sugar-Free Week", desc: "No added sugar for 7 days", progress: 0, total: 7, unit: "days", daysLeft: 7, icon: "apple", color: "from-green-500 to-emerald-500", joined: false },
];

const BADGES: Badge[] = [
  { name: "First Steps", icon: Target, earned: "2 Mar 2026", color: "bg-blue-100 text-blue-600" },
  { name: "Streak Master", icon: Flame, earned: "18 Feb 2026", color: "bg-orange-100 text-orange-600" },
  { name: "Hydration Pro", icon: Zap, earned: "10 Jan 2026", color: "bg-cyan-100 text-cyan-600" },
  { name: "Early Bird", icon: Star, earned: "28 Dec 2025", color: "bg-amber-100 text-amber-600" },
  { name: "Team Player", icon: Medal, earned: "15 Nov 2025", color: "bg-purple-100 text-purple-600" },
  { name: "Century Club", icon: Trophy, earned: "1 Oct 2025", color: "bg-emerald-100 text-emerald-600" },
];

const LEADERBOARD = [
  { rank: 1, name: "Thandi M.", score: 2450, trend: "+120" },
  { rank: 2, name: "Sipho K.", score: 2310, trend: "+85" },
  { rank: 3, name: "Lerato N.", score: 2180, trend: "+95" },
  { rank: 4, name: "You", score: 1960, trend: "+140", isUser: true },
  { rank: 5, name: "James P.", score: 1820, trend: "+60" },
  { rank: 6, name: "Fatima A.", score: 1750, trend: "+110" },
];

/** Challenges — Health OS §6: wellness challenges, achievements, and progress. */
export default function ChallengesPage() {
  const [challenges, setChallenges] = useState([...ACTIVE, ...AVAILABLE]);

  const joinChallenge = (id: number) => setChallenges((prev) => prev.map((c) => c.id === id ? { ...c, joined: true } : c));
  const active = challenges.filter((c) => c.joined);
  const available = challenges.filter((c) => !c.joined);

  return (
    <AppLayout>
      <PageShell title="Challenges" subtitle="Compete, achieve, and celebrate wellness milestones" icon={<Trophy className="h-6 w-6" />}>
        {/* Active challenges */}
        <div className="mb-6">
          <h2 className="font-semibold text-gray-800 mb-3">Active Challenges</h2>
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
            {active.map((c) => (
              <div key={c.id} className="rounded-xl overflow-hidden shadow-sm border border-gray-200 bg-white">
                <div className={`bg-gradient-to-r ${c.color} p-4 text-white`}>
                  <h3 className="font-bold">{c.name}</h3>
                  <p className="text-xs text-white/80 mt-0.5">{c.desc}</p>
                </div>
                <div className="p-4">
                  <div className="flex justify-between text-sm mb-1">
                    <span className="font-medium text-gray-700">{c.progress} / {c.total} {c.unit}</span>
                    <span className="text-gray-500">{c.daysLeft}d left</span>
                  </div>
                  <div className="h-3 rounded-full bg-gray-100 overflow-hidden">
                    <div className={`h-full rounded-full bg-gradient-to-r ${c.color} transition-all`} style={{ width: `${(c.progress / c.total) * 100}%` }} />
                  </div>
                  <p className="text-xs text-gray-500 mt-2 text-right">{Math.round((c.progress / c.total) * 100)}% complete</p>
                </div>
              </div>
            ))}
          </div>
        </div>

        {/* Achievements */}
        <div className="mb-6">
          <h2 className="font-semibold text-gray-800 mb-3">Achievements</h2>
          <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-6 gap-3">
            {BADGES.map((b) => (
              <div key={b.name} className="rounded-xl border border-gray-200 bg-white p-4 text-center shadow-sm hover:shadow-md transition-shadow">
                <div className={`h-12 w-12 rounded-full ${b.color} flex items-center justify-center mx-auto mb-2`}>
                  <b.icon className="h-6 w-6" />
                </div>
                <p className="text-sm font-semibold text-gray-800">{b.name}</p>
                <p className="text-xs text-gray-400 mt-0.5">{b.earned}</p>
              </div>
            ))}
          </div>
        </div>

        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
          {/* Leaderboard */}
          <div className="rounded-xl border border-gray-200 bg-white shadow-sm overflow-hidden">
            <div className="px-5 py-4 border-b border-gray-100 bg-gray-50">
              <h3 className="font-semibold text-gray-800">Leaderboard</h3>
            </div>
            <div className="divide-y divide-gray-50">
              {LEADERBOARD.map((r) => (
                <div key={r.rank} className={`px-5 py-3 flex items-center gap-4 ${(r as { isUser?: boolean }).isUser ? "bg-blue-50" : ""}`}>
                  <span className={`h-8 w-8 rounded-full flex items-center justify-center text-sm font-bold ${r.rank <= 3 ? "bg-gradient-to-br from-yellow-400 to-amber-500 text-white" : "bg-gray-100 text-gray-600"}`}>{r.rank}</span>
                  <span className={`flex-1 font-medium text-sm ${(r as { isUser?: boolean }).isUser ? "text-blue-700" : "text-gray-800"}`}>{r.name}</span>
                  <span className="text-sm font-semibold text-gray-700">{r.score.toLocaleString()}</span>
                  <span className="text-xs text-emerald-600 font-medium">{r.trend}</span>
                </div>
              ))}
            </div>
          </div>

          {/* Join new challenges */}
          <div>
            <h3 className="font-semibold text-gray-800 mb-3">Join a Challenge</h3>
            <div className="space-y-3">
              {available.map((c) => (
                <div key={c.id} className="rounded-xl border border-gray-200 bg-white shadow-sm p-4 flex items-center gap-4">
                  <div className={`h-12 w-12 rounded-xl bg-gradient-to-br ${c.color} flex items-center justify-center text-white`}>
                    <Target className="h-6 w-6" />
                  </div>
                  <div className="flex-1">
                    <h4 className="font-semibold text-gray-900 text-sm">{c.name}</h4>
                    <p className="text-xs text-gray-500">{c.desc}</p>
                    <p className="text-xs text-gray-400 mt-0.5">{c.total} {c.unit} &middot; {c.daysLeft} days</p>
                  </div>
                  <button onClick={() => joinChallenge(c.id)} className="rounded-lg bg-blue-600 text-white px-4 py-2 text-sm font-medium hover:bg-blue-700 transition-colors whitespace-nowrap">Join</button>
                </div>
              ))}
              {available.length === 0 && <p className="text-sm text-gray-400 text-center py-4">You have joined all available challenges!</p>}
            </div>
          </div>
        </div>
      </PageShell>
    </AppLayout>
  );
}

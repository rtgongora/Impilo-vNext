"use client";
import { useState } from "react";
import { Moon, Star, Clock, BedDouble, Sun, Plus, X } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";

const WEEK_DATA = [
  { day: "Mon", hours: 7.5, quality: 4 },
  { day: "Tue", hours: 6.0, quality: 3 },
  { day: "Wed", hours: 8.0, quality: 5 },
  { day: "Thu", hours: 7.0, quality: 4 },
  { day: "Fri", hours: 5.5, quality: 2 },
  { day: "Sat", hours: 8.5, quality: 5 },
  { day: "Sun", hours: 7.2, quality: 4 },
];

/** Sleep & Recovery — Health OS §6: how people sleep, rest, and recover. */
export default function SleepPage() {
  const [sleepLog, setSleepLog] = useState(WEEK_DATA);
  const [showForm, setShowForm] = useState(false);
  const [form, setForm] = useState({ bedtime: "22:30", wakeTime: "06:30", quality: 4, interruptions: 1 });

  const lastNight = sleepLog[sleepLog.length - 1];
  const avgHours = sleepLog.reduce((s, d) => s + d.hours, 0) / sleepLog.length;
  const recoveryScore = Math.round(Math.min(((avgHours / 8) * 70 + (lastNight.quality / 5) * 30), 100));
  const maxBar = 10;

  const submitSleep = () => {
    const [bh, bm] = form.bedtime.split(":").map(Number);
    const [wh, wm] = form.wakeTime.split(":").map(Number);
    let duration = (wh + wm / 60) - (bh + bm / 60);
    if (duration < 0) duration += 24;
    setSleepLog((prev) => [...prev.slice(-6), { day: "Today", hours: Math.round(duration * 10) / 10, quality: form.quality }]);
    setShowForm(false);
  };

  return (
    <AppLayout>
      <PageShell title="Sleep & Recovery" subtitle="Track sleep patterns, quality, and recovery readiness" icon={<Moon className="h-6 w-6" />}>
        {/* Last night card + Recovery score */}
        <div className="grid grid-cols-1 md:grid-cols-2 gap-6 mb-6">
          <div className="rounded-xl bg-gradient-to-br from-indigo-600 to-purple-600 text-white p-6 shadow-lg">
            <h2 className="font-semibold text-lg mb-4 flex items-center gap-2"><BedDouble className="h-5 w-5" /> Last Night</h2>
            <div className="grid grid-cols-2 gap-4">
              <div><p className="text-xs text-indigo-200">Bedtime</p><p className="text-xl font-bold">22:30</p></div>
              <div><p className="text-xs text-indigo-200">Wake time</p><p className="text-xl font-bold">06:12</p></div>
              <div><p className="text-xs text-indigo-200">Duration</p><p className="text-xl font-bold">{lastNight.hours}h</p></div>
              <div>
                <p className="text-xs text-indigo-200">Quality</p>
                <div className="flex gap-0.5 mt-1">{Array.from({ length: 5 }).map((_, i) => <Star key={i} className={`h-5 w-5 ${i < lastNight.quality ? "fill-yellow-300 text-yellow-300" : "text-indigo-300"}`} />)}</div>
              </div>
            </div>
          </div>

          <div className="rounded-xl border border-gray-200 bg-white shadow-sm p-6 flex flex-col items-center justify-center">
            <p className="text-sm font-medium text-gray-500 mb-3">Recovery Score</p>
            <div className="relative h-32 w-32">
              <svg viewBox="0 0 36 36" className="h-32 w-32 -rotate-90">
                <circle cx="18" cy="18" r="15.5" fill="none" stroke="#e5e7eb" strokeWidth="3" />
                <circle cx="18" cy="18" r="15.5" fill="none" stroke={recoveryScore >= 75 ? "#22c55e" : recoveryScore >= 50 ? "#eab308" : "#ef4444"} strokeWidth="3" strokeDasharray={`${recoveryScore} ${100 - recoveryScore}`} strokeLinecap="round" />
              </svg>
              <div className="absolute inset-0 flex flex-col items-center justify-center">
                <span className="text-3xl font-bold text-gray-900">{recoveryScore}%</span>
                <span className="text-xs text-gray-500">{recoveryScore >= 75 ? "Great" : recoveryScore >= 50 ? "Fair" : "Low"}</span>
              </div>
            </div>
            <p className="text-xs text-gray-400 mt-2">Based on 7-day average of {avgHours.toFixed(1)}h</p>
          </div>
        </div>

        {/* 7-day trend bar chart */}
        <div className="rounded-xl border border-gray-200 bg-white shadow-sm p-6 mb-6">
          <h3 className="font-semibold text-gray-800 mb-4">7-Day Sleep Trend</h3>
          <div className="flex items-end gap-3 h-44">
            {sleepLog.slice(-7).map((d, i) => (
              <div key={i} className="flex-1 flex flex-col items-center gap-1">
                <span className="text-xs font-medium text-gray-600">{d.hours}h</span>
                <div className="w-full rounded-t-lg transition-all" style={{ height: `${(d.hours / maxBar) * 100}%`, background: d.quality >= 4 ? "linear-gradient(to top, #6366f1, #818cf8)" : d.quality >= 3 ? "linear-gradient(to top, #eab308, #facc15)" : "linear-gradient(to top, #ef4444, #f87171)" }} />
                <span className="text-xs text-gray-500">{d.day}</span>
              </div>
            ))}
          </div>
          <div className="flex gap-4 mt-3 text-xs text-gray-500">
            <span className="flex items-center gap-1"><span className="h-2.5 w-2.5 rounded-full bg-indigo-500" />Good (4-5)</span>
            <span className="flex items-center gap-1"><span className="h-2.5 w-2.5 rounded-full bg-yellow-500" />Fair (3)</span>
            <span className="flex items-center gap-1"><span className="h-2.5 w-2.5 rounded-full bg-red-500" />Poor (1-2)</span>
          </div>
        </div>

        {/* Log sleep form */}
        <div className="rounded-xl border border-gray-200 bg-white shadow-sm p-6">
          <div className="flex items-center justify-between mb-4">
            <h3 className="font-semibold text-gray-800">Log Sleep</h3>
            <button onClick={() => setShowForm(!showForm)} className="text-indigo-600 hover:text-indigo-700 text-sm font-medium flex items-center gap-1">
              {showForm ? <X className="h-4 w-4" /> : <Plus className="h-4 w-4" />}{showForm ? "Cancel" : "New Entry"}
            </button>
          </div>
          {showForm && (
            <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
              <div>
                <label className="block text-xs font-medium text-gray-600 mb-1"><Moon className="h-3 w-3 inline mr-1" />Bedtime</label>
                <input type="time" value={form.bedtime} onChange={(e) => setForm({ ...form, bedtime: e.target.value })} className="w-full rounded-lg border px-3 py-2 text-sm" />
              </div>
              <div>
                <label className="block text-xs font-medium text-gray-600 mb-1"><Sun className="h-3 w-3 inline mr-1" />Wake time</label>
                <input type="time" value={form.wakeTime} onChange={(e) => setForm({ ...form, wakeTime: e.target.value })} className="w-full rounded-lg border px-3 py-2 text-sm" />
              </div>
              <div>
                <label className="block text-xs font-medium text-gray-600 mb-1"><Star className="h-3 w-3 inline mr-1" />Quality (1-5)</label>
                <div className="flex gap-1 mt-1">{Array.from({ length: 5 }).map((_, i) => (
                  <button key={i} onClick={() => setForm({ ...form, quality: i + 1 })} className="focus:outline-none"><Star className={`h-6 w-6 ${i < form.quality ? "fill-yellow-400 text-yellow-400" : "text-gray-300"}`} /></button>
                ))}</div>
              </div>
              <div>
                <label className="block text-xs font-medium text-gray-600 mb-1"><Clock className="h-3 w-3 inline mr-1" />Interruptions</label>
                <input type="number" min={0} max={10} value={form.interruptions} onChange={(e) => setForm({ ...form, interruptions: +e.target.value })} className="w-full rounded-lg border px-3 py-2 text-sm" />
              </div>
              <div className="col-span-2 md:col-span-4">
                <button onClick={submitSleep} className="rounded-lg bg-indigo-600 text-white px-6 py-2 text-sm font-medium hover:bg-indigo-700 transition-colors">Save Sleep Entry</button>
              </div>
            </div>
          )}
        </div>
      </PageShell>
    </AppLayout>
  );
}

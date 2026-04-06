"use client";

/**
 * Goals — Patient SMART goals with progress tracking and priority levels.
 * Route: /ehr/[patientId]/goals | pageTitle: "Goals"
 */

import { useState } from "react";
import { useParams } from "next/navigation";
import { Target, Loader2, Plus, X, Calendar, Link2, Edit3 } from "lucide-react";
import { useQuery } from "@tanstack/react-query";
import { EHRLayout } from "@/components/EHRLayout";
import { PageShell } from "@/components/PageShell";
import { apiClient } from "@/lib/api-client";

interface PatientGoal {
  id: string;
  title: string;
  description: string;
  status: "On Track" | "At Risk" | "Behind" | "Achieved" | "Cancelled";
  priority: "High" | "Medium" | "Low";
  progress: number;
  targetDate: string;
  createdDate: string;
  linkedCarePlan: string | null;
  category: string;
  notes: string;
}

const MOCK_GOALS: PatientGoal[] = [
  { id: "goal-1", title: "Reduce HbA1c to below 7%", description: "Through medication adherence, dietary changes, and regular exercise, aim to reduce HbA1c from current 8.2% to below 7%.", status: "On Track", priority: "High", progress: 65, targetDate: "2026-06-30", createdDate: "2026-01-15", linkedCarePlan: "Diabetes Management Plan", category: "Clinical", notes: "Last HbA1c: 7.4% (improving)" },
  { id: "goal-2", title: "Walk 30 minutes daily", description: "Establish a daily walking routine of at least 30 minutes to improve cardiovascular fitness and support weight management.", status: "At Risk", priority: "Medium", progress: 40, targetDate: "2026-04-30", createdDate: "2026-01-15", linkedCarePlan: "Diabetes Management Plan", category: "Lifestyle", notes: "Patient reports 3-4 days/week adherence" },
  { id: "goal-3", title: "Lose 5 kg body weight", description: "Reduce body weight from 85 kg to 80 kg through diet and exercise modifications.", status: "Behind", priority: "Medium", progress: 20, targetDate: "2026-09-30", createdDate: "2026-01-15", linkedCarePlan: "Diabetes Management Plan", category: "Clinical", notes: "Current weight: 84 kg (1 kg lost)" },
  { id: "goal-4", title: "Complete smoking cessation programme", description: "Maintain tobacco-free status and complete the 6-month cessation programme.", status: "Achieved", priority: "High", progress: 100, targetDate: "2025-12-31", createdDate: "2025-06-15", linkedCarePlan: null, category: "Lifestyle", notes: "Successfully completed. Smoke-free for 9 months." },
  { id: "goal-5", title: "PHQ-9 score below 5", description: "Reduce depression screening score through therapy and medication management.", status: "On Track", priority: "High", progress: 55, targetDate: "2026-07-31", createdDate: "2026-02-01", linkedCarePlan: "Mental Health Support Plan", category: "Mental Health", notes: "Last PHQ-9: 8 (down from 14)" },
  { id: "goal-6", title: "Blood pressure consistently below 130/80", description: "Achieve sustained blood pressure control through medication and lifestyle modifications.", status: "On Track", priority: "High", progress: 75, targetDate: "2026-05-31", createdDate: "2025-11-01", linkedCarePlan: null, category: "Clinical", notes: "Recent readings: 128/78, 132/82, 126/76" },
];

const STATUS_STYLES: Record<string, string> = {
  "On Track": "bg-green-100 text-green-700",
  "At Risk": "bg-amber-100 text-amber-700",
  "Behind": "bg-red-100 text-red-700",
  "Achieved": "bg-blue-100 text-blue-700",
  "Cancelled": "bg-gray-100 text-gray-600",
};

const PRIORITY_STYLES: Record<string, string> = {
  High: "bg-red-50 text-red-600 border-red-200",
  Medium: "bg-amber-50 text-amber-600 border-amber-200",
  Low: "bg-green-50 text-green-600 border-green-200",
};

function progressColor(p: number): string {
  if (p >= 75) return "bg-green-500";
  if (p >= 50) return "bg-blue-500";
  if (p >= 25) return "bg-amber-500";
  return "bg-red-500";
}

export default function GoalsPage() {
  const params = useParams<{ patientId: string }>();
  const patientId = params.patientId;

  const { data, isLoading } = useQuery({
    queryKey: ["goals", patientId],
    queryFn: async () => ({ data: MOCK_GOALS }),
  });

  const goals = data?.data ?? [];
  const [showForm, setShowForm] = useState(false);
  const [filterStatus, setFilterStatus] = useState("All");
  const [newTitle, setNewTitle] = useState("");
  const [newDescription, setNewDescription] = useState("");
  const [newPriority, setNewPriority] = useState("Medium");
  const [newTarget, setNewTarget] = useState("");

  const filtered = filterStatus === "All" ? goals : goals.filter((g) => g.status === filterStatus);

  const summary = {
    onTrack: goals.filter((g) => g.status === "On Track").length,
    atRisk: goals.filter((g) => g.status === "At Risk").length,
    behind: goals.filter((g) => g.status === "Behind").length,
    achieved: goals.filter((g) => g.status === "Achieved").length,
  };

  return (
    <EHRLayout>
      <PageShell title="Goals" subtitle="Patient health goals with SMART tracking">
        {isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="w-6 h-6 animate-spin text-gray-400" />
            <span className="ml-2 text-sm text-gray-500">Loading goals...</span>
          </div>
        ) : (
          <div className="space-y-6">
            {/* Header */}
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <Target className="w-5 h-5 text-green-600" />
                <h2 className="text-lg font-semibold text-gray-900">Patient Goals</h2>
              </div>
              <div className="flex items-center gap-3">
                <select value={filterStatus} onChange={(e) => setFilterStatus(e.target.value)} className="rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500">
                  <option>All</option>
                  <option>On Track</option>
                  <option>At Risk</option>
                  <option>Behind</option>
                  <option>Achieved</option>
                </select>
                <button type="button" onClick={() => setShowForm(true)} className="inline-flex items-center gap-1.5 px-4 py-2 bg-blue-600 text-white text-sm font-medium rounded-lg hover:bg-blue-700 transition-colors">
                  <Plus className="w-4 h-4" /> New Goal
                </button>
              </div>
            </div>

            {/* Summary Cards */}
            <div className="grid grid-cols-4 gap-4">
              {[
                { label: "On Track", count: summary.onTrack, color: "text-green-600 bg-green-50" },
                { label: "At Risk", count: summary.atRisk, color: "text-amber-600 bg-amber-50" },
                { label: "Behind", count: summary.behind, color: "text-red-600 bg-red-50" },
                { label: "Achieved", count: summary.achieved, color: "text-blue-600 bg-blue-50" },
              ].map((s) => (
                <div key={s.label} className={`rounded-lg p-3 ${s.color}`}>
                  <p className="text-2xl font-bold">{s.count}</p>
                  <p className="text-xs font-medium">{s.label}</p>
                </div>
              ))}
            </div>

            {/* Create Goal Form */}
            {showForm && (
              <div className="bg-white rounded-lg border border-gray-200 p-5">
                <div className="flex items-center justify-between mb-4">
                  <h3 className="font-medium text-gray-900">Create New Goal</h3>
                  <button onClick={() => setShowForm(false)} className="text-gray-400 hover:text-gray-600"><X className="w-4 h-4" /></button>
                </div>
                <div className="space-y-4">
                  <div>
                    <label className="block text-xs font-medium text-gray-600 mb-1">Goal Title</label>
                    <input type="text" value={newTitle} onChange={(e) => setNewTitle(e.target.value)} className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" placeholder="e.g., Reduce blood pressure to normal range" />
                  </div>
                  <div>
                    <label className="block text-xs font-medium text-gray-600 mb-1">Description</label>
                    <textarea value={newDescription} onChange={(e) => setNewDescription(e.target.value)} rows={2} className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" placeholder="Specific, measurable details..." />
                  </div>
                  <div className="grid grid-cols-2 gap-4">
                    <div>
                      <label className="block text-xs font-medium text-gray-600 mb-1">Priority</label>
                      <select value={newPriority} onChange={(e) => setNewPriority(e.target.value)} className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500">
                        <option>High</option>
                        <option>Medium</option>
                        <option>Low</option>
                      </select>
                    </div>
                    <div>
                      <label className="block text-xs font-medium text-gray-600 mb-1">Target Date</label>
                      <input type="date" value={newTarget} onChange={(e) => setNewTarget(e.target.value)} className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
                    </div>
                  </div>
                </div>
                <div className="flex items-center gap-3 pt-4">
                  <button className="px-4 py-2 bg-blue-600 text-white text-sm font-medium rounded-lg hover:bg-blue-700 transition-colors">Create Goal</button>
                  <button onClick={() => setShowForm(false)} className="px-4 py-2 text-sm font-medium text-gray-700 rounded-lg border border-gray-300 hover:bg-gray-50 transition-colors">Cancel</button>
                </div>
              </div>
            )}

            {/* Goals List */}
            {filtered.length === 0 ? (
              <div className="bg-white rounded-lg border border-gray-200 p-12 text-center">
                <Target className="w-10 h-10 text-gray-300 mx-auto mb-3" />
                <p className="text-gray-400 text-sm">No goals found</p>
              </div>
            ) : (
              <div className="space-y-3">
                {filtered.map((goal) => (
                  <div key={goal.id} className="bg-white rounded-lg border border-gray-200 p-5">
                    <div className="flex items-start justify-between mb-3">
                      <div className="flex-1">
                        <div className="flex items-center gap-2 mb-1">
                          <h3 className="font-medium text-gray-900 text-sm">{goal.title}</h3>
                          <span className={`px-2 py-0.5 rounded-full text-xs font-medium ${STATUS_STYLES[goal.status]}`}>{goal.status}</span>
                          <span className={`px-2 py-0.5 rounded text-xs font-medium border ${PRIORITY_STYLES[goal.priority]}`}>{goal.priority}</span>
                        </div>
                        <p className="text-xs text-gray-500">{goal.description}</p>
                      </div>
                      <button className="p-1 text-gray-400 hover:text-blue-600 transition-colors">
                        <Edit3 className="w-3.5 h-3.5" />
                      </button>
                    </div>
                    <div className="flex items-center gap-3 mb-3">
                      <div className="flex-1">
                        <div className="w-full bg-gray-200 rounded-full h-2.5">
                          <div className={`h-2.5 rounded-full ${progressColor(goal.progress)}`} style={{ width: `${goal.progress}%` }} />
                        </div>
                      </div>
                      <span className="text-sm font-medium text-gray-700 w-12 text-right">{goal.progress}%</span>
                    </div>
                    <div className="flex items-center gap-4 text-xs text-gray-500">
                      <span className="flex items-center gap-1"><Calendar className="w-3 h-3" /> Target: {goal.targetDate}</span>
                      <span className="text-gray-300">|</span>
                      <span>{goal.category}</span>
                      {goal.linkedCarePlan && (
                        <>
                          <span className="text-gray-300">|</span>
                          <span className="flex items-center gap-1 text-blue-500"><Link2 className="w-3 h-3" /> {goal.linkedCarePlan}</span>
                        </>
                      )}
                    </div>
                    {goal.notes && <p className="text-xs text-gray-500 mt-2 italic">{goal.notes}</p>}
                  </div>
                ))}
              </div>
            )}
          </div>
        )}
      </PageShell>
    </EHRLayout>
  );
}

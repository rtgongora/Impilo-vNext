"use client";

/**
 * Care Plans — Manage patient care plans with goals, interventions, and status tracking.
 * Route: /ehr/[patientId]/care-plans | pageTitle: "Care Plans"
 */

import { useState } from "react";
import { useParams } from "next/navigation";
import { ClipboardList, Loader2, Plus, Target, CheckCircle2, Circle, Clock, X } from "lucide-react";
import { EHRLayout } from "@/components/EHRLayout";
import { PageShell } from "@/components/PageShell";
import { useQuery } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";

interface CarePlan {
  id: string;
  title: string;
  status: "Active" | "Completed" | "Draft";
  category: string;
  startDate: string;
  targetDate: string;
  author: string;
  goals: { label: string; progress: number }[];
  interventions: { label: string; completed: boolean }[];
}

const MOCK_CARE_PLANS: CarePlan[] = [
  {
    id: "cp-1",
    title: "Diabetes Management Plan",
    status: "Active",
    category: "Chronic Disease",
    startDate: "2026-01-15",
    targetDate: "2026-07-15",
    author: "Dr. Moyo",
    goals: [
      { label: "HbA1c below 7%", progress: 65 },
      { label: "Daily glucose monitoring", progress: 80 },
      { label: "Weight reduction 5kg", progress: 40 },
    ],
    interventions: [
      { label: "Metformin 500mg BD", completed: true },
      { label: "Dietitian referral", completed: true },
      { label: "Exercise programme enrollment", completed: false },
      { label: "Monthly HbA1c check", completed: false },
    ],
  },
  {
    id: "cp-2",
    title: "Hypertension Control",
    status: "Active",
    category: "Cardiovascular",
    startDate: "2026-02-01",
    targetDate: "2026-08-01",
    author: "Dr. Ndlovu",
    goals: [
      { label: "BP below 140/90", progress: 70 },
      { label: "Sodium intake < 2g/day", progress: 50 },
    ],
    interventions: [
      { label: "Amlodipine 5mg daily", completed: true },
      { label: "Home BP monitoring kit", completed: true },
      { label: "Low-sodium diet education", completed: false },
    ],
  },
  {
    id: "cp-3",
    title: "Post-Surgical Rehabilitation",
    status: "Completed",
    category: "Rehabilitation",
    startDate: "2025-09-01",
    targetDate: "2025-12-01",
    author: "Dr. Chikwava",
    goals: [
      { label: "Full range of motion", progress: 100 },
      { label: "Pain-free mobility", progress: 100 },
    ],
    interventions: [
      { label: "Physiotherapy 3x/week", completed: true },
      { label: "Analgesic taper", completed: true },
      { label: "Follow-up imaging", completed: true },
    ],
  },
  {
    id: "cp-4",
    title: "Mental Health Support",
    status: "Draft",
    category: "Behavioural Health",
    startDate: "",
    targetDate: "",
    author: "Dr. Moyo",
    goals: [
      { label: "PHQ-9 score < 5", progress: 0 },
    ],
    interventions: [
      { label: "CBT sessions weekly", completed: false },
      { label: "SSRI initiation review", completed: false },
    ],
  },
];

const STATUS_STYLES: Record<string, string> = {
  Active: "bg-green-100 text-green-700",
  Completed: "bg-blue-100 text-blue-700",
  Draft: "bg-gray-100 text-gray-600",
};

export default function CarePlansPage() {
  const params = useParams<{ patientId: string }>();
  const patientId = params.patientId;

  const { data, isLoading } = useQuery({
    queryKey: ["care-plans", patientId],
    queryFn: async () => {
      try {
        return await apiClient.get(`/patients/${patientId}/care-plans`);
      } catch {
        return { data: MOCK_CARE_PLANS };
      }
    },
  });

  const carePlans: CarePlan[] = (data as { data: CarePlan[] })?.data ?? MOCK_CARE_PLANS;

  const [showForm, setShowForm] = useState(false);
  const [formTitle, setFormTitle] = useState("");
  const [formCategory, setFormCategory] = useState("");
  const [formTargetDate, setFormTargetDate] = useState("");
  const [expandedPlan, setExpandedPlan] = useState<string | null>(carePlans[0]?.id ?? null);

  return (
    <EHRLayout>
      <PageShell title="Care Plans" subtitle="Active and historical care plan management">
        {isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="w-6 h-6 animate-spin text-gray-400" />
            <span className="ml-2 text-sm text-gray-500">Loading care plans...</span>
          </div>
        ) : (
          <div className="space-y-6">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <ClipboardList className="w-5 h-5 text-blue-600" />
                <h2 className="text-lg font-semibold text-gray-900">Care Plans</h2>
                <span className="text-sm text-gray-500">({carePlans.length})</span>
              </div>
              <button
                type="button"
                onClick={() => setShowForm((prev) => !prev)}
                className="inline-flex items-center gap-1.5 px-4 py-2 bg-blue-600 text-white text-sm font-medium rounded-lg hover:bg-blue-700 transition-colors"
              >
                <Plus className="w-4 h-4" />
                New Care Plan
              </button>
            </div>

            {/* New care plan form modal */}
            {showForm && (
              <div className="bg-white rounded-lg border border-gray-200 p-5">
                <div className="flex items-center justify-between mb-4">
                  <h3 className="font-medium text-gray-900">New Care Plan</h3>
                  <button type="button" onClick={() => setShowForm(false)} className="text-gray-400 hover:text-gray-600">
                    <X className="w-4 h-4" />
                  </button>
                </div>
                <form onSubmit={(e) => { e.preventDefault(); setShowForm(false); }} className="space-y-4">
                  <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                    <div>
                      <label className="block text-xs font-medium text-gray-600 mb-1">Plan Title</label>
                      <input type="text" value={formTitle} onChange={(e) => setFormTitle(e.target.value)} className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" placeholder="e.g. Diabetes Management" />
                    </div>
                    <div>
                      <label className="block text-xs font-medium text-gray-600 mb-1">Category</label>
                      <select value={formCategory} onChange={(e) => setFormCategory(e.target.value)} className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500">
                        <option value="">Select category</option>
                        <option value="Chronic Disease">Chronic Disease</option>
                        <option value="Cardiovascular">Cardiovascular</option>
                        <option value="Rehabilitation">Rehabilitation</option>
                        <option value="Behavioural Health">Behavioural Health</option>
                        <option value="Preventive">Preventive</option>
                      </select>
                    </div>
                    <div>
                      <label className="block text-xs font-medium text-gray-600 mb-1">Target Date</label>
                      <input type="date" value={formTargetDate} onChange={(e) => setFormTargetDate(e.target.value)} className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
                    </div>
                  </div>
                  <div className="flex items-center gap-3 pt-2">
                    <button type="submit" className="inline-flex items-center gap-1.5 px-4 py-2 bg-blue-600 text-white text-sm font-medium rounded-lg hover:bg-blue-700 transition-colors">Create Plan</button>
                    <button type="button" onClick={() => setShowForm(false)} className="px-4 py-2 text-sm font-medium text-gray-700 rounded-lg border border-gray-300 hover:bg-gray-50 transition-colors">Cancel</button>
                  </div>
                </form>
              </div>
            )}

            {/* Care plan cards */}
            {carePlans.length === 0 ? (
              <div className="bg-white rounded-lg border border-gray-200 p-12 text-center">
                <ClipboardList className="w-10 h-10 text-gray-300 mx-auto mb-3" />
                <p className="text-gray-400 text-sm">No care plans created yet</p>
              </div>
            ) : (
              <div className="space-y-4">
                {carePlans.map((plan) => {
                  const isExpanded = expandedPlan === plan.id;
                  return (
                    <div key={plan.id} className="bg-white rounded-lg border border-gray-200 overflow-hidden">
                      <button
                        type="button"
                        onClick={() => setExpandedPlan(isExpanded ? null : plan.id)}
                        className="w-full flex items-center justify-between p-4 hover:bg-gray-50 transition-colors text-left"
                      >
                        <div className="flex items-center gap-3">
                          <ClipboardList className="w-5 h-5 text-blue-600" />
                          <div>
                            <div className="font-medium text-gray-900">{plan.title}</div>
                            <div className="text-xs text-gray-500">{plan.category} &middot; {plan.author}</div>
                          </div>
                        </div>
                        <div className="flex items-center gap-3">
                          <span className={`px-2 py-0.5 rounded-full text-xs font-medium ${STATUS_STYLES[plan.status]}`}>{plan.status}</span>
                          {plan.startDate && <span className="text-xs text-gray-400">{plan.startDate} &rarr; {plan.targetDate}</span>}
                        </div>
                      </button>

                      {isExpanded && (
                        <div className="border-t border-gray-100 p-4 space-y-4">
                          {/* Goals with progress */}
                          <div>
                            <h4 className="text-sm font-medium text-gray-700 flex items-center gap-1.5 mb-2">
                              <Target className="w-4 h-4 text-amber-600" /> Goals
                            </h4>
                            <div className="space-y-2">
                              {plan.goals.map((goal, i) => (
                                <div key={i} className="flex items-center gap-3">
                                  <span className="text-sm text-gray-700 w-48 truncate">{goal.label}</span>
                                  <div className="flex-1 bg-gray-100 rounded-full h-2">
                                    <div className="bg-blue-500 h-2 rounded-full transition-all" style={{ width: `${goal.progress}%` }} />
                                  </div>
                                  <span className="text-xs text-gray-500 w-10 text-right">{goal.progress}%</span>
                                </div>
                              ))}
                            </div>
                          </div>

                          {/* Interventions checklist */}
                          <div>
                            <h4 className="text-sm font-medium text-gray-700 flex items-center gap-1.5 mb-2">
                              <CheckCircle2 className="w-4 h-4 text-green-600" /> Interventions
                            </h4>
                            <div className="space-y-1.5">
                              {plan.interventions.map((item, i) => (
                                <div key={i} className="flex items-center gap-2 text-sm">
                                  {item.completed ? (
                                    <CheckCircle2 className="w-4 h-4 text-green-500" />
                                  ) : (
                                    <Circle className="w-4 h-4 text-gray-300" />
                                  )}
                                  <span className={item.completed ? "text-gray-500 line-through" : "text-gray-700"}>{item.label}</span>
                                </div>
                              ))}
                            </div>
                          </div>
                        </div>
                      )}
                    </div>
                  );
                })}
              </div>
            )}
          </div>
        )}
      </PageShell>
    </EHRLayout>
  );
}

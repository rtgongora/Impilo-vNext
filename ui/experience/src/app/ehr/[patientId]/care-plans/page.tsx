"use client";

import { useState } from "react";
import { useParams } from "next/navigation";
import {
  Activity,
  CheckCircle2,
  Circle,
  ClipboardList,
  Clock,
  FileText,
  Loader2,
  Plus,
  Target,
  Users,
  X,
} from "lucide-react";
import { useQuery } from "@tanstack/react-query";
import { ClinicalReviewHeader } from "@/components/ehr/ClinicalReviewHeader";
import { EHRLayout } from "@/components/EHRLayout";
import { PageShell } from "@/components/PageShell";
import { useEncounters } from "@/hooks/queries/useEncounters";
import { useFacilityStore } from "@/hooks/useFacilityStore";
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
    goals: [{ label: "PHQ-9 score < 5", progress: 0 }],
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
  const facility = useFacilityStore((state) => state.facility);
  const { data: encountersData } = useEncounters(patientId);

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
  const activeEncounter = (encountersData?.data ?? []).find(
    (encounter) =>
      encounter.attributes.status === "IN_PROGRESS" || encounter.attributes.status === "ACTIVE"
  );
  const activePlans = carePlans.filter((plan) => plan.status === "Active");
  const draftPlans = carePlans.filter((plan) => plan.status === "Draft");
  const activeGoals = activePlans.flatMap((plan) => plan.goals).filter((goal) => goal.progress < 100).length;
  const pendingInterventions = activePlans
    .flatMap((plan) => plan.interventions)
    .filter((intervention) => !intervention.completed).length;

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
            <Loader2 className="h-6 w-6 animate-spin text-gray-400" />
            <span className="ml-2 text-sm text-gray-500">Loading care plans...</span>
          </div>
        ) : (
          <div className="space-y-6">
            <ClinicalReviewHeader
              badge="Care planning"
              badgeIcon={ClipboardList}
              title="Keep goals, interventions, and ownership visible while the current encounter is still in motion"
              description="Care plans now sit in the same orchestration layer as goals, care team, and notes so teams can see who owns the next intervention and move the plan forward without losing patient context."
              facilityName={facility?.name}
              encounterLabel={
                activeEncounter
                  ? `${activeEncounter.attributes.encounterType} since ${new Date(activeEncounter.attributes.startedAt).toLocaleString()}`
                  : null
              }
              actions={[
                { href: `/ehr/${patientId}/summary`, label: "Summary", icon: Activity },
                { href: `/ehr/${patientId}/goals`, label: "Goals", icon: Target, tone: "secondary" },
                { href: `/ehr/${patientId}/care-team`, label: "Care Team", icon: Users, tone: "secondary" },
                { href: `/ehr/${patientId}/notes`, label: "Notes", icon: FileText, tone: "secondary" },
              ]}
              metrics={[
                {
                  label: "Active plans",
                  value: String(activePlans.length),
                  detail: "Plans currently guiding the patient journey.",
                },
                {
                  label: "Goals in motion",
                  value: String(activeGoals),
                  detail: "Goal targets still being worked through across active plans.",
                },
                {
                  label: "Pending interventions",
                  value: String(pendingInterventions),
                  detail: draftPlans.length > 0
                    ? `${draftPlans.length} draft plan${draftPlans.length === 1 ? "" : "s"} still need activation.`
                    : "No draft plans are waiting to be activated.",
                },
              ]}
            />

            <div className="rounded-3xl border border-slate-200 bg-slate-50/70 p-4">
              <p className="text-xs font-medium uppercase tracking-[0.18em] text-slate-500">
                Care plan loop status
              </p>
              <p className="mt-2 text-sm text-slate-800">
                {pendingInterventions > 0
                  ? `${pendingInterventions} intervention${pendingInterventions === 1 ? " is" : "s are"} still open across active plans, so the next step should stay visible here before the encounter closes.`
                  : "The active plans are documented, and the next continuity step is keeping goals, care team, and notes aligned as the plan progresses."}
              </p>
              <p className="mt-1 text-xs text-slate-500">
                Use Goals to track outcome movement, Care Team to confirm ownership, and Notes to record execution or barriers without leaving the patient workspace.
              </p>
            </div>

            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <ClipboardList className="h-5 w-5 text-blue-600" />
                <h2 className="text-lg font-semibold text-gray-900">Care Plans</h2>
                <span className="text-sm text-gray-500">({carePlans.length})</span>
              </div>
              <button
                type="button"
                onClick={() => setShowForm((prev) => !prev)}
                className="inline-flex items-center gap-1.5 rounded-lg bg-blue-600 px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-blue-700"
              >
                <Plus className="h-4 w-4" />
                New Care Plan
              </button>
            </div>

            {showForm && (
              <div className="rounded-lg border border-gray-200 bg-white p-5">
                <div className="mb-4 flex items-center justify-between">
                  <h3 className="font-medium text-gray-900">New Care Plan</h3>
                  <button type="button" onClick={() => setShowForm(false)} className="text-gray-400 hover:text-gray-600">
                    <X className="h-4 w-4" />
                  </button>
                </div>
                <form onSubmit={(e) => { e.preventDefault(); setShowForm(false); }} className="space-y-4">
                  <div className="grid grid-cols-1 gap-4 md:grid-cols-3">
                    <div>
                      <label className="mb-1 block text-xs font-medium text-gray-600">Plan Title</label>
                      <input type="text" value={formTitle} onChange={(e) => setFormTitle(e.target.value)} className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" placeholder="e.g. Diabetes Management" />
                    </div>
                    <div>
                      <label className="mb-1 block text-xs font-medium text-gray-600">Category</label>
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
                      <label className="mb-1 block text-xs font-medium text-gray-600">Target Date</label>
                      <input type="date" value={formTargetDate} onChange={(e) => setFormTargetDate(e.target.value)} className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
                    </div>
                  </div>
                  <div className="flex items-center gap-3 pt-2">
                    <button type="submit" className="inline-flex items-center gap-1.5 rounded-lg bg-blue-600 px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-blue-700">Create Plan</button>
                    <button type="button" onClick={() => setShowForm(false)} className="rounded-lg border border-gray-300 px-4 py-2 text-sm font-medium text-gray-700 transition-colors hover:bg-gray-50">Cancel</button>
                  </div>
                </form>
              </div>
            )}

            {carePlans.length === 0 ? (
              <div className="rounded-lg border border-gray-200 bg-white p-12 text-center">
                <ClipboardList className="mx-auto mb-3 h-10 w-10 text-gray-300" />
                <p className="text-sm text-gray-400">No care plans created yet</p>
              </div>
            ) : (
              <div className="space-y-4">
                {carePlans.map((plan) => {
                  const isExpanded = expandedPlan === plan.id;
                  return (
                    <div key={plan.id} className="overflow-hidden rounded-lg border border-gray-200 bg-white">
                      <button
                        type="button"
                        onClick={() => setExpandedPlan(isExpanded ? null : plan.id)}
                        className="flex w-full items-center justify-between p-4 text-left transition-colors hover:bg-gray-50"
                      >
                        <div className="flex items-center gap-3">
                          <ClipboardList className="h-5 w-5 text-blue-600" />
                          <div>
                            <div className="font-medium text-gray-900">{plan.title}</div>
                            <div className="text-xs text-gray-500">{plan.category} · {plan.author}</div>
                          </div>
                        </div>
                        <div className="flex items-center gap-3">
                          <span className={`rounded-full px-2 py-0.5 text-xs font-medium ${STATUS_STYLES[plan.status]}`}>{plan.status}</span>
                          {plan.startDate && <span className="text-xs text-gray-400">{plan.startDate} ? {plan.targetDate}</span>}
                        </div>
                      </button>

                      {isExpanded && (
                        <div className="space-y-4 border-t border-gray-100 p-4">
                          <div>
                            <h4 className="mb-2 flex items-center gap-1.5 text-sm font-medium text-gray-700">
                              <Target className="h-4 w-4 text-amber-600" /> Goals
                            </h4>
                            <div className="space-y-2">
                              {plan.goals.map((goal, index) => (
                                <div key={index} className="flex items-center gap-3">
                                  <span className="w-48 truncate text-sm text-gray-700">{goal.label}</span>
                                  <div className="h-2 flex-1 rounded-full bg-gray-100">
                                    <div className="h-2 rounded-full bg-blue-500 transition-all" style={{ width: `${goal.progress}%` }} />
                                  </div>
                                  <span className="w-10 text-right text-xs text-gray-500">{goal.progress}%</span>
                                </div>
                              ))}
                            </div>
                          </div>

                          <div>
                            <h4 className="mb-2 flex items-center gap-1.5 text-sm font-medium text-gray-700">
                              <CheckCircle2 className="h-4 w-4 text-green-600" /> Interventions
                            </h4>
                            <div className="space-y-1.5">
                              {plan.interventions.map((item, index) => (
                                <div key={index} className="flex items-center gap-2 text-sm">
                                  {item.completed ? (
                                    <CheckCircle2 className="h-4 w-4 text-green-500" />
                                  ) : (
                                    <Circle className="h-4 w-4 text-gray-300" />
                                  )}
                                  <span className={item.completed ? "text-gray-500 line-through" : "text-gray-700"}>{item.label}</span>
                                </div>
                              ))}
                            </div>
                          </div>

                          <div className="flex flex-wrap gap-2 pt-2">
                            <a href={`/ehr/${patientId}/goals`} className="inline-flex items-center gap-1 rounded-lg border border-slate-200 bg-white px-3 py-1.5 text-xs font-medium text-slate-700 hover:bg-slate-50">
                              <Target className="h-3.5 w-3.5" /> Open goals
                            </a>
                            <a href={`/ehr/${patientId}/care-team`} className="inline-flex items-center gap-1 rounded-lg border border-slate-200 bg-white px-3 py-1.5 text-xs font-medium text-slate-700 hover:bg-slate-50">
                              <Users className="h-3.5 w-3.5" /> Confirm ownership
                            </a>
                            <a href={`/ehr/${patientId}/notes`} className="inline-flex items-center gap-1 rounded-lg border border-slate-200 bg-white px-3 py-1.5 text-xs font-medium text-slate-700 hover:bg-slate-50">
                              <FileText className="h-3.5 w-3.5" /> Document progress
                            </a>
                            {plan.targetDate && (
                              <span className="inline-flex items-center gap-1 rounded-lg bg-amber-50 px-3 py-1.5 text-xs font-medium text-amber-700">
                                <Clock className="h-3.5 w-3.5" /> Due {plan.targetDate}
                              </span>
                            )}
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

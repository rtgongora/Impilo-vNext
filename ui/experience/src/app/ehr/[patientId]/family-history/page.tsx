"use client";

/**
 * Family History — Family medical history tree with conditions and onset tracking.
 * Route: /ehr/[patientId]/family-history | pageTitle: "Family History"
 */

import { useState } from "react";
import { useParams } from "next/navigation";
import { Users, Loader2, Plus, X, Heart, AlertTriangle } from "lucide-react";
import { useQuery } from "@tanstack/react-query";
import { EHRLayout } from "@/components/EHRLayout";
import { PageShell } from "@/components/PageShell";
import { apiClient } from "@/lib/api-client";

interface FamilyCondition {
  id: string;
  condition: string;
  onsetAge: number | null;
  status: "Active" | "Resolved" | "Deceased";
  notes: string;
}

interface FamilyMember {
  id: string;
  name: string;
  relationship: string;
  age: number | null;
  deceased: boolean;
  deceasedAge: number | null;
  causeOfDeath: string | null;
  conditions: FamilyCondition[];
}

const MOCK_FAMILY: FamilyMember[] = [
  {
    id: "fm-1", name: "John M.", relationship: "Father", age: 68, deceased: false, deceasedAge: null, causeOfDeath: null,
    conditions: [
      { id: "fc-1", condition: "Type 2 Diabetes Mellitus", onsetAge: 52, status: "Active", notes: "On insulin since 2018" },
      { id: "fc-2", condition: "Hypertension", onsetAge: 45, status: "Active", notes: "Controlled with medication" },
    ],
  },
  {
    id: "fm-2", name: "Mary M.", relationship: "Mother", age: 65, deceased: false, deceasedAge: null, causeOfDeath: null,
    conditions: [
      { id: "fc-3", condition: "Breast Cancer", onsetAge: 58, status: "Resolved", notes: "Stage I, mastectomy 2019, in remission" },
      { id: "fc-4", condition: "Osteoporosis", onsetAge: 60, status: "Active", notes: "On bisphosphonates" },
    ],
  },
  {
    id: "fm-3", name: "Robert M.", relationship: "Paternal Grandfather", age: null, deceased: true, deceasedAge: 72, causeOfDeath: "Myocardial Infarction",
    conditions: [
      { id: "fc-5", condition: "Coronary Artery Disease", onsetAge: 55, status: "Deceased", notes: "Multiple MIs" },
      { id: "fc-6", condition: "Type 2 Diabetes Mellitus", onsetAge: 48, status: "Deceased", notes: "" },
    ],
  },
  {
    id: "fm-4", name: "Sarah M.", relationship: "Sister", age: 35, deceased: false, deceasedAge: null, causeOfDeath: null,
    conditions: [
      { id: "fc-7", condition: "Asthma", onsetAge: 12, status: "Active", notes: "Mild intermittent" },
    ],
  },
  {
    id: "fm-5", name: "Grace M.", relationship: "Maternal Grandmother", age: null, deceased: true, deceasedAge: 81, causeOfDeath: "Stroke",
    conditions: [
      { id: "fc-8", condition: "Cerebrovascular Disease", onsetAge: 75, status: "Deceased", notes: "" },
      { id: "fc-9", condition: "Atrial Fibrillation", onsetAge: 70, status: "Deceased", notes: "" },
    ],
  },
];

const RELATIONSHIPS = ["Father", "Mother", "Brother", "Sister", "Son", "Daughter", "Paternal Grandfather", "Paternal Grandmother", "Maternal Grandfather", "Maternal Grandmother", "Uncle", "Aunt", "Cousin"];

const STATUS_STYLES: Record<string, string> = {
  Active: "bg-yellow-100 text-yellow-700",
  Resolved: "bg-green-100 text-green-700",
  Deceased: "bg-gray-100 text-gray-600",
};

export default function FamilyHistoryPage() {
  const params = useParams<{ patientId: string }>();
  const patientId = params.patientId;

  const { data, isLoading } = useQuery({
    queryKey: ["family-history", patientId],
    queryFn: async () => ({ data: MOCK_FAMILY }),
  });

  const members = data?.data ?? [];
  const [showForm, setShowForm] = useState(false);
  const [newRelationship, setNewRelationship] = useState("");
  const [newName, setNewName] = useState("");
  const [newAge, setNewAge] = useState("");
  const [newCondition, setNewCondition] = useState("");
  const [expandedMember, setExpandedMember] = useState<string | null>(null);

  // Risk factors summary
  const allConditions = members.flatMap((m) => m.conditions.map((c) => c.condition));
  const conditionCounts: Record<string, number> = {};
  allConditions.forEach((c) => { conditionCounts[c] = (conditionCounts[c] || 0) + 1; });
  const riskConditions = Object.entries(conditionCounts).filter(([, count]) => count >= 2).sort((a, b) => b[1] - a[1]);

  return (
    <EHRLayout>
      <PageShell title="Family History" subtitle="Family medical history and hereditary risk factors">
        {isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="w-6 h-6 animate-spin text-gray-400" />
            <span className="ml-2 text-sm text-gray-500">Loading family history...</span>
          </div>
        ) : (
          <div className="space-y-6">
            {/* Header */}
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <Users className="w-5 h-5 text-indigo-600" />
                <h2 className="text-lg font-semibold text-gray-900">Family History</h2>
              </div>
              <button
                type="button"
                onClick={() => setShowForm(true)}
                className="inline-flex items-center gap-1.5 px-4 py-2 bg-blue-600 text-white text-sm font-medium rounded-lg hover:bg-blue-700 transition-colors"
              >
                <Plus className="w-4 h-4" />
                Add Family Member
              </button>
            </div>

            {/* Risk Factor Summary */}
            {riskConditions.length > 0 && (
              <div className="bg-amber-50 border border-amber-200 rounded-lg p-4">
                <div className="flex items-center gap-2 mb-2">
                  <AlertTriangle className="w-4 h-4 text-amber-600" />
                  <h3 className="text-sm font-medium text-amber-800">Hereditary Risk Factors</h3>
                </div>
                <div className="flex flex-wrap gap-2">
                  {riskConditions.map(([condition, count]) => (
                    <span key={condition} className="px-2.5 py-1 bg-amber-100 text-amber-800 rounded-full text-xs font-medium">
                      {condition} ({count} relatives)
                    </span>
                  ))}
                </div>
              </div>
            )}

            {/* Add Member Form */}
            {showForm && (
              <div className="bg-white rounded-lg border border-gray-200 p-5">
                <div className="flex items-center justify-between mb-4">
                  <h3 className="font-medium text-gray-900">Add Family Member</h3>
                  <button onClick={() => setShowForm(false)} className="text-gray-400 hover:text-gray-600"><X className="w-4 h-4" /></button>
                </div>
                <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                  <div>
                    <label className="block text-xs font-medium text-gray-600 mb-1">Relationship</label>
                    <select value={newRelationship} onChange={(e) => setNewRelationship(e.target.value)} className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500">
                      <option value="">Select relationship...</option>
                      {RELATIONSHIPS.map((r) => (<option key={r}>{r}</option>))}
                    </select>
                  </div>
                  <div>
                    <label className="block text-xs font-medium text-gray-600 mb-1">Name / Initials</label>
                    <input type="text" value={newName} onChange={(e) => setNewName(e.target.value)} className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" placeholder="e.g., John M." />
                  </div>
                  <div>
                    <label className="block text-xs font-medium text-gray-600 mb-1">Current Age</label>
                    <input type="number" value={newAge} onChange={(e) => setNewAge(e.target.value)} className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" placeholder="65" />
                  </div>
                </div>
                <div className="mt-4">
                  <label className="block text-xs font-medium text-gray-600 mb-1">Known Condition (search)</label>
                  <input type="text" value={newCondition} onChange={(e) => setNewCondition(e.target.value)} className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" placeholder="Search conditions (e.g., diabetes, hypertension)..." />
                </div>
                <div className="flex items-center gap-3 pt-4">
                  <button className="px-4 py-2 bg-blue-600 text-white text-sm font-medium rounded-lg hover:bg-blue-700 transition-colors">Add Member</button>
                  <button onClick={() => setShowForm(false)} className="px-4 py-2 text-sm font-medium text-gray-700 rounded-lg border border-gray-300 hover:bg-gray-50 transition-colors">Cancel</button>
                </div>
              </div>
            )}

            {/* Family Members */}
            {members.length === 0 ? (
              <div className="bg-white rounded-lg border border-gray-200 p-12 text-center">
                <Users className="w-10 h-10 text-gray-300 mx-auto mb-3" />
                <p className="text-gray-400 text-sm">No family history recorded</p>
              </div>
            ) : (
              <div className="space-y-3">
                {members.map((member) => (
                  <div key={member.id} className="bg-white rounded-lg border border-gray-200 overflow-hidden">
                    <button
                      type="button"
                      onClick={() => setExpandedMember(expandedMember === member.id ? null : member.id)}
                      className="w-full px-5 py-4 flex items-center justify-between hover:bg-gray-50 transition-colors text-left"
                    >
                      <div className="flex items-center gap-3">
                        <div className={`w-10 h-10 rounded-full flex items-center justify-center ${member.deceased ? "bg-gray-100 text-gray-400" : "bg-indigo-100 text-indigo-600"}`}>
                          <Heart className="w-4 h-4" />
                        </div>
                        <div>
                          <h3 className="font-medium text-gray-900 text-sm">{member.name}</h3>
                          <p className="text-xs text-gray-500">
                            {member.relationship}
                            {member.deceased ? ` — Deceased (age ${member.deceasedAge})` : member.age ? ` — Age ${member.age}` : ""}
                          </p>
                        </div>
                      </div>
                      <span className="text-xs text-gray-400">{member.conditions.length} condition{member.conditions.length !== 1 ? "s" : ""}</span>
                    </button>
                    {expandedMember === member.id && (
                      <div className="border-t border-gray-200 px-5 py-4">
                        {member.causeOfDeath && (
                          <p className="text-xs text-gray-500 mb-3">Cause of death: <span className="font-medium text-gray-700">{member.causeOfDeath}</span></p>
                        )}
                        <div className="space-y-2">
                          {member.conditions.map((cond) => (
                            <div key={cond.id} className="flex items-center justify-between bg-gray-50 rounded-lg px-3 py-2">
                              <div>
                                <p className="text-sm text-gray-800">{cond.condition}</p>
                                {cond.notes && <p className="text-xs text-gray-500">{cond.notes}</p>}
                              </div>
                              <div className="flex items-center gap-2">
                                {cond.onsetAge && <span className="text-xs text-gray-500">Onset age {cond.onsetAge}</span>}
                                <span className={`px-2 py-0.5 rounded-full text-xs font-medium ${STATUS_STYLES[cond.status]}`}>{cond.status}</span>
                              </div>
                            </div>
                          ))}
                        </div>
                      </div>
                    )}
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

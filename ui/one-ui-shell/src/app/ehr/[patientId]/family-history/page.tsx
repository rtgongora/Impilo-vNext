"use client";

import { useState } from "react";
import { useParams } from "next/navigation";
import { AlertTriangle, ClipboardList, FileText, Heart, Loader2, Plus, Users, X } from "lucide-react";
import { ClinicalReviewHeader } from "@/components/ehr/ClinicalReviewHeader";
import { EHRLayout } from "@/components/EHRLayout";
import { PageShell } from "@/components/PageShell";
import { useEncounters } from "@/hooks/queries/useEncounters";
import type { FamilyMember } from "@/hooks/queries/useStructuredHistory";
import { useFamilyHistory } from "@/hooks/queries/useStructuredHistory";
import { useFacilityStore } from "@/hooks/useFacilityStore";

const RELATIONSHIPS = ["Father", "Mother", "Brother", "Sister", "Son", "Daughter", "Paternal Grandfather", "Paternal Grandmother", "Maternal Grandfather", "Maternal Grandmother", "Uncle", "Aunt", "Cousin"];

const STATUS_STYLES: Record<string, string> = {
  Active: "bg-yellow-100 text-yellow-700",
  Resolved: "bg-green-100 text-green-700",
  Deceased: "bg-gray-100 text-gray-600",
};

export default function FamilyHistoryPage() {
  const params = useParams<{ patientId: string }>();
  const patientId = params.patientId;
  const facility = useFacilityStore((state) => state.facility);
  const { data: encountersData } = useEncounters(patientId);

  const { data, isLoading, isError, refetch } = useFamilyHistory(patientId);
  const members: FamilyMember[] = data?.data ?? [];
  const activeEncounter = (encountersData?.data ?? []).find(
    (encounter) =>
      encounter.attributes.status === "IN_PROGRESS" || encounter.attributes.status === "ACTIVE"
  );
  const allConditions = members.flatMap((member) => member.conditions.map((condition) => condition.condition));
  const conditionCounts: Record<string, number> = {};
  allConditions.forEach((condition) => { conditionCounts[condition] = (conditionCounts[condition] || 0) + 1; });
  const riskConditions = Object.entries(conditionCounts).filter(([, count]) => count >= 2).sort((a, b) => b[1] - a[1]);
  const activeFamilyConditions = members.flatMap((member) => member.conditions).filter((condition) => condition.status === "Active").length;

  const [showForm, setShowForm] = useState(false);
  const [newRelationship, setNewRelationship] = useState("");
  const [newName, setNewName] = useState("");
  const [newAge, setNewAge] = useState("");
  const [newCondition, setNewCondition] = useState("");
  const [expandedMember, setExpandedMember] = useState<string | null>(null);

  return (
    <EHRLayout>
      <PageShell title="Family History" subtitle="Family medical history and hereditary risk factors">
        {isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="h-6 w-6 animate-spin text-gray-400" />
            <span className="ml-2 text-sm text-gray-500">Loading family history...</span>
          </div>
        ) : isError ? (
          <div className="flex flex-col items-center justify-center gap-3 py-16">
            <p className="text-sm text-gray-600">Unable to load family history for this patient.</p>
            <button
              type="button"
              onClick={() => void refetch()}
              className="rounded-lg bg-impilo-500 px-4 py-2 text-sm font-medium text-white hover:bg-impilo-600"
            >
              Retry
            </button>
          </div>
        ) : (
          <div className="space-y-6">
            <ClinicalReviewHeader
              badge="Family context"
              badgeIcon={Users}
              title="Keep hereditary risk visible so current planning and preventive decisions stay tied to the wider family story"
              description="Family history now sits inside the same continuity layer as conditions, social context, and care planning so inherited risk factors can drive action instead of staying buried as background data."
              facilityName={facility?.name}
              encounterLabel={
                activeEncounter
                  ? `${activeEncounter.attributes.encounterType} since ${new Date(activeEncounter.attributes.startedAt).toLocaleString()}`
                  : null
              }
              actions={[
                { href: `/ehr/${patientId}/conditions`, label: "Conditions", icon: ClipboardList },
                { href: `/ehr/${patientId}/social-history`, label: "Social History", icon: FileText, tone: "secondary" },
                { href: `/ehr/${patientId}/care-plans`, label: "Care Plans", icon: ClipboardList, tone: "secondary" },
                { href: `/ehr/${patientId}/notes`, label: "Notes", icon: FileText, tone: "secondary" },
              ]}
              metrics={[
                {
                  label: "Relatives tracked",
                  value: String(members.length),
                  detail: "Family members contributing to the documented history.",
                },
                {
                  label: "Risk clusters",
                  value: String(riskConditions.length),
                  detail: "Conditions repeated across relatives and likely to affect planning.",
                },
                {
                  label: "Active family conditions",
                  value: String(activeFamilyConditions),
                  detail: "Current family diagnoses still relevant to hereditary risk review.",
                },
              ]}
            />

            <div className="rounded-3xl border border-slate-200 bg-slate-50/70 p-4">
              <p className="text-xs font-medium uppercase tracking-[0.18em] text-slate-500">Family continuity</p>
              <p className="mt-2 text-sm text-slate-800">
                {riskConditions.length > 0
                  ? `${riskConditions.length} repeated risk pattern${riskConditions.length === 1 ? " is" : "s are"} visible in the documented family story, so this review should flow straight into conditions, plans, and preventive notes.`
                  : "No repeated risk clusters are obvious yet; the continuity need is keeping family context available when conditions or prevention plans are updated later."}
              </p>
              <p className="mt-1 text-xs text-slate-500">
                Use Conditions to capture confirmed problems, Social History to add environmental context, and Notes to record how hereditary risk changes the plan.
              </p>
            </div>

            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <Users className="h-5 w-5 text-indigo-600" />
                <h2 className="text-lg font-semibold text-gray-900">Family History</h2>
              </div>
              <button
                type="button"
                onClick={() => setShowForm(true)}
                className="inline-flex items-center gap-1.5 rounded-lg bg-impilo-500 px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-impilo-600"
              >
                <Plus className="h-4 w-4" />
                Add Family Member
              </button>
            </div>

            {riskConditions.length > 0 && (
              <div className="rounded-lg border border-amber-200 bg-amber-50 p-4">
                <div className="mb-2 flex items-center gap-2">
                  <AlertTriangle className="h-4 w-4 text-amber-600" />
                  <h3 className="text-sm font-medium text-amber-800">Hereditary Risk Factors</h3>
                </div>
                <div className="flex flex-wrap gap-2">
                  {riskConditions.map(([condition, count]) => (
                    <span key={condition} className="rounded-full bg-amber-100 px-2.5 py-1 text-xs font-medium text-amber-800">
                      {condition} ({count} relatives)
                    </span>
                  ))}
                </div>
              </div>
            )}

            {showForm && (
              <div className="rounded-lg border border-gray-200 bg-white p-5">
                <div className="mb-4 flex items-center justify-between">
                  <h3 className="font-medium text-gray-900">Add Family Member</h3>
                  <button onClick={() => setShowForm(false)} className="text-gray-400 hover:text-gray-600"><X className="h-4 w-4" /></button>
                </div>
                <div className="grid grid-cols-1 gap-4 md:grid-cols-3">
                  <div>
                    <label className="mb-1 block text-xs font-medium text-gray-600">Relationship</label>
                    <select value={newRelationship} onChange={(e) => setNewRelationship(e.target.value)} className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-impilo-400">
                      <option value="">Select relationship...</option>
                      {RELATIONSHIPS.map((relationship) => (<option key={relationship}>{relationship}</option>))}
                    </select>
                  </div>
                  <div>
                    <label className="mb-1 block text-xs font-medium text-gray-600">Name / Initials</label>
                    <input type="text" value={newName} onChange={(e) => setNewName(e.target.value)} className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-impilo-400" placeholder="e.g., John M." />
                  </div>
                  <div>
                    <label className="mb-1 block text-xs font-medium text-gray-600">Current Age</label>
                    <input type="number" value={newAge} onChange={(e) => setNewAge(e.target.value)} className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-impilo-400" placeholder="65" />
                  </div>
                </div>
                <div className="mt-4">
                  <label className="mb-1 block text-xs font-medium text-gray-600">Known Condition (search)</label>
                  <input type="text" value={newCondition} onChange={(e) => setNewCondition(e.target.value)} className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-impilo-400" placeholder="Search conditions (e.g., diabetes, hypertension)..." />
                </div>
                <div className="flex items-center gap-3 pt-4">
                  <button className="rounded-lg bg-impilo-500 px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-impilo-600">Add Member</button>
                  <button onClick={() => setShowForm(false)} className="rounded-lg border border-gray-300 px-4 py-2 text-sm font-medium text-gray-700 transition-colors hover:bg-gray-50">Cancel</button>
                </div>
              </div>
            )}

            {members.length === 0 ? (
              <div className="rounded-lg border border-gray-200 bg-white p-12 text-center">
                <Users className="mx-auto mb-3 h-10 w-10 text-gray-300" />
                <p className="text-sm text-gray-400">No family history recorded</p>
              </div>
            ) : (
              <div className="space-y-3">
                {members.map((member) => (
                  <div key={member.id} className="overflow-hidden rounded-lg border border-gray-200 bg-white">
                    <button
                      type="button"
                      onClick={() => setExpandedMember(expandedMember === member.id ? null : member.id)}
                      className="flex w-full items-center justify-between px-5 py-4 text-left transition-colors hover:bg-gray-50"
                    >
                      <div className="flex items-center gap-3">
                        <div className={`flex h-10 w-10 items-center justify-center rounded-full ${member.deceased ? "bg-gray-100 text-gray-400" : "bg-indigo-100 text-indigo-600"}`}>
                          <Heart className="h-4 w-4" />
                        </div>
                        <div>
                          <h3 className="text-sm font-medium text-gray-900">{member.name}</h3>
                          <p className="text-xs text-gray-500">
                            {member.relationship}
                            {member.deceased ? ` - Deceased (age ${member.deceasedAge})` : member.age ? ` - Age ${member.age}` : ""}
                          </p>
                        </div>
                      </div>
                      <span className="text-xs text-gray-400">{member.conditions.length} condition{member.conditions.length !== 1 ? "s" : ""}</span>
                    </button>
                    {expandedMember === member.id && (
                      <div className="border-t border-gray-200 px-5 py-4">
                        {member.causeOfDeath && (
                          <p className="mb-3 text-xs text-gray-500">Cause of death: <span className="font-medium text-gray-700">{member.causeOfDeath}</span></p>
                        )}
                        <div className="space-y-2">
                          {member.conditions.map((condition) => (
                            <div key={condition.id} className="flex items-center justify-between rounded-lg bg-gray-50 px-3 py-2">
                              <div>
                                <p className="text-sm text-gray-800">{condition.condition}</p>
                                {condition.notes && <p className="text-xs text-gray-500">{condition.notes}</p>}
                              </div>
                              <div className="flex items-center gap-2">
                                {condition.onsetAge && <span className="text-xs text-gray-500">Onset age {condition.onsetAge}</span>}
                                <span className={`rounded-full px-2 py-0.5 text-xs font-medium ${STATUS_STYLES[condition.status]}`}>{condition.status}</span>
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

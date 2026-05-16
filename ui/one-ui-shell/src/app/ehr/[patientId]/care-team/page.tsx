"use client";

import { useState } from "react";
import { useParams } from "next/navigation";
import { Activity, Loader2, Plus, Trash2, Users, X, FileText, ClipboardList, CheckCircle2, AlertCircle } from "lucide-react";
import { ClinicalReviewHeader } from "@/components/ehr/ClinicalReviewHeader";
import { EHRLayout } from "@/components/EHRLayout";
import { PageShell } from "@/components/PageShell";
import { useEncounters } from "@/hooks/queries/useEncounters";
import { useCareTeam, useAddCareTeamMember, useRemoveCareTeamMember } from "@/hooks/queries/useCareContinuity";
import { useFacilityStore } from "@/hooks/useFacilityStore";

export default function CareTeamPage() {
  const params = useParams<{ patientId: string }>();
  const patientId = params.patientId;
  const facility = useFacilityStore((state) => state.facility);
  const { data: encountersData } = useEncounters(patientId);

  const { data: careTeamData, isLoading, isError } = useCareTeam(patientId);
  const addMember = useAddCareTeamMember();
  const removeMember = useRemoveCareTeamMember();
  const members = careTeamData?.data ?? [];
  const [successMsg, setSuccessMsg] = useState("");
  const activeEncounter = (encountersData?.data ?? []).find(
    (encounter) =>
      encounter.attributes.status === "IN_PROGRESS" || encounter.attributes.status === "ACTIVE"
  );
  const activeMembers = members.filter((member) => member.status === "active");
  const inactiveMembers = members.filter((member) => member.status === "inactive");
  const primaryMembers = activeMembers.length > 0 ? 1 : 0;
  const externalMembers: number = 0;

  const [showForm, setShowForm] = useState(false);
  const [viewMode, setViewMode] = useState<"grid" | "list">("grid");
  const [newName, setNewName] = useState("");
  const [newRole, setNewRole] = useState("");
  const [newSpecialty, setNewSpecialty] = useState("");

  return (
    <EHRLayout>
      <PageShell title="Care Team" subtitle="Assigned providers and care team management">
        {isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="h-6 w-6 animate-spin text-gray-400" />
            <span className="ml-2 text-sm text-gray-500">Loading care team...</span>
          </div>
        ) : isError ? (
          <div className="flex flex-col items-center justify-center gap-3 py-16">
            <AlertCircle className="h-6 w-6 text-red-400" />
            <p className="text-sm text-gray-600">Unable to load care team.</p>
          </div>
        ) : (
          <div className="space-y-6">
            {successMsg && (
              <div className="flex items-center gap-2 rounded-lg border border-green-200 bg-green-50 p-3 text-sm text-green-800">
                <CheckCircle2 className="h-4 w-4 text-green-600" />
                {successMsg}
              </div>
            )}
            {addMember.isError && (
              <div className="flex items-center gap-2 rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-800">
                <AlertCircle className="h-4 w-4 text-red-600" />
                Failed to add team member. Please try again.
              </div>
            )}
            <ClinicalReviewHeader
              badge="Care team"
              badgeIcon={Users}
              title="Keep the accountable team visible so plans, consults, and next actions still have an owner"
              description="Care team now participates in the same orchestration layer as plans, goals, and notes so ownership stays clear when work moves across facilities or specialties."
              facilityName={facility?.name}
              encounterLabel={
                activeEncounter
                  ? `${activeEncounter.attributes.encounterType} since ${new Date(activeEncounter.attributes.startedAt).toLocaleString()}`
                  : null
              }
              actions={[
                { href: `/ehr/${patientId}/summary`, label: "Summary", icon: Activity },
                { href: `/ehr/${patientId}/care-plans`, label: "Care Plans", icon: ClipboardList, tone: "secondary" },
                { href: `/ehr/${patientId}/goals`, label: "Goals", icon: Activity, tone: "secondary" },
                { href: `/ehr/${patientId}/notes`, label: "Notes", icon: FileText, tone: "secondary" },
              ]}
              metrics={[
                {
                  label: "Active members",
                  value: String(activeMembers.length),
                  detail: "People currently assigned to the patient journey.",
                },
                {
                  label: "Primary roles",
                  value: String(primaryMembers),
                  detail: "Core owners for clinical continuity and escalation.",
                },
                {
                  label: "Cross-facility",
                  value: String(externalMembers),
                  detail: externalMembers > 0 ? "Specialists from outside the current facility are involved." : "All active members are within the current facility.",
                },
              ]}
            />

            <div className="rounded-3xl border border-slate-200 bg-slate-50/70 p-4">
              <p className="text-xs font-medium uppercase tracking-[0.18em] text-slate-500">Team continuity</p>
              <p className="mt-2 text-sm text-slate-800">
                {externalMembers > 0
                  ? `${externalMembers} active team member${externalMembers === 1 ? " works" : "s work"} outside the current facility, so ownership and communication need to stay explicit when the plan or consult loop moves.`
                  : "The team is locally aligned; the next continuity step is making sure plans and notes still show who owns the next action."}
              </p>
              <p className="mt-1 text-xs text-slate-500">
                Use Care Plans to assign work, Notes to communicate handoffs, and Summary to keep the patient-facing team picture coherent across surfaces.
              </p>
            </div>

            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <Users className="h-5 w-5 text-impilo-500" />
                <h2 className="text-lg font-semibold text-gray-900">Care Team</h2>
                <span className="text-xs text-gray-400">{activeMembers.length} active members</span>
              </div>
              <div className="flex items-center gap-2">
                <div className="overflow-hidden rounded-lg border border-gray-300">
                  <button onClick={() => setViewMode("grid")} className={`px-3 py-1.5 text-xs ${viewMode === "grid" ? "bg-impilo-500 text-white" : "bg-white text-gray-600 hover:bg-gray-50"}`}>Grid</button>
                  <button onClick={() => setViewMode("list")} className={`px-3 py-1.5 text-xs ${viewMode === "list" ? "bg-impilo-500 text-white" : "bg-white text-gray-600 hover:bg-gray-50"}`}>List</button>
                </div>
                <button type="button" onClick={() => setShowForm(true)} className="inline-flex items-center gap-1.5 rounded-lg bg-impilo-500 px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-impilo-600">
                  <Plus className="h-4 w-4" />
                  Add Member
                </button>
              </div>
            </div>

            {showForm && (
              <div className="rounded-lg border border-gray-200 bg-white p-5">
                <div className="mb-4 flex items-center justify-between">
                  <h3 className="font-medium text-gray-900">Add Team Member</h3>
                  <button onClick={() => setShowForm(false)} className="text-gray-400 hover:text-gray-600"><X className="h-4 w-4" /></button>
                </div>
                <div className="grid grid-cols-1 gap-4 md:grid-cols-3">
                  <div>
                    <label className="mb-1 block text-xs font-medium text-gray-600">Provider Name</label>
                    <input type="text" value={newName} onChange={(e) => setNewName(e.target.value)} className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-impilo-400" placeholder="Search providers..." />
                  </div>
                  <div>
                    <label className="mb-1 block text-xs font-medium text-gray-600">Role</label>
                    <select value={newRole} onChange={(e) => setNewRole(e.target.value)} className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-impilo-400">
                      <option value="">Select role...</option>
                      <option>Primary Physician</option>
                      <option>Specialist</option>
                      <option>Consultant</option>
                      <option>Primary Nurse</option>
                      <option>Physiotherapist</option>
                      <option>Dietitian</option>
                      <option>Social Worker</option>
                    </select>
                  </div>
                  <div>
                    <label className="mb-1 block text-xs font-medium text-gray-600">Specialty</label>
                    <input type="text" value={newSpecialty} onChange={(e) => setNewSpecialty(e.target.value)} className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-impilo-400" placeholder="e.g., Cardiology" />
                  </div>
                </div>
                <div className="flex items-center gap-3 pt-4">
                  <button
                    type="button"
                    disabled={addMember.isPending || !newName || !newRole}
                    onClick={() => {
                      addMember.mutate(
                        { patientId, name: newName, role: newRole, specialty: newSpecialty },
                        {
                          onSuccess: () => {
                            setNewName("");
                            setNewRole("");
                            setNewSpecialty("");
                            setShowForm(false);
                            setSuccessMsg("Team member added successfully.");
                            setTimeout(() => setSuccessMsg(""), 4000);
                          },
                        },
                      );
                    }}
                    className="inline-flex items-center gap-1.5 rounded-lg bg-impilo-500 px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-impilo-600 disabled:opacity-50"
                  >
                    {addMember.isPending && <Loader2 className="h-4 w-4 animate-spin" />}
                    Add to Team
                  </button>
                  <button onClick={() => setShowForm(false)} className="rounded-lg border border-gray-300 px-4 py-2 text-sm font-medium text-gray-700 transition-colors hover:bg-gray-50">Cancel</button>
                </div>
              </div>
            )}

            {members.length === 0 ? (
              <div className="rounded-lg border border-gray-200 bg-white p-12 text-center">
                <Users className="mx-auto mb-3 h-10 w-10 text-gray-300" />
                <p className="text-sm text-gray-400">No care team members assigned</p>
              </div>
            ) : viewMode === "grid" ? (
              <>
                <div className="grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-3">
                  {activeMembers.map((member) => (
                    <div key={member.id} className="rounded-lg border border-gray-200 bg-white p-5 transition-colors hover:border-impilo-200">
                      <div className="mb-3 flex items-start justify-between">
                        <div className="flex items-center gap-3">
                          <div className="flex h-11 w-11 items-center justify-center rounded-full bg-impilo-100 text-sm font-semibold text-impilo-600">
                            {member.name.split(" ").map((n) => n[0]).join("").slice(0, 2).toUpperCase()}
                          </div>
                          <div>
                            <h3 className="flex items-center gap-1 text-sm font-medium text-gray-900">
                              {member.name}
                            </h3>
                            <p className="text-xs text-impilo-500">{member.role}</p>
                          </div>
                        </div>
                        <button
                          type="button"
                          onClick={() => removeMember.mutate({ memberId: member.id, patientId })}
                          disabled={removeMember.isPending}
                          className="p-1 text-gray-400 transition-colors hover:text-red-500 disabled:opacity-50"
                        >
                          <Trash2 className="h-3.5 w-3.5" />
                        </button>
                      </div>
                      <p className="mb-2 text-xs text-gray-500">{member.specialty}</p>
                      <p className="text-xs text-gray-400">Assigned {member.assignedAt ? member.assignedAt.slice(0, 10) : "—"}</p>
                    </div>
                  ))}
                </div>
                {inactiveMembers.length > 0 && (
                  <div>
                    <h3 className="mb-3 text-sm font-medium text-gray-500">Inactive Members</h3>
                    <div className="grid grid-cols-1 gap-4 opacity-60 md:grid-cols-2 lg:grid-cols-3">
                      {inactiveMembers.map((member) => (
                        <div key={member.id} className="rounded-lg border border-gray-200 bg-white p-5">
                          <div className="mb-2 flex items-center gap-3">
                            <div className="flex h-11 w-11 items-center justify-center rounded-full bg-gray-100 text-sm font-semibold text-gray-500">
                              {member.name.split(" ").map((n) => n[0]).join("").slice(0, 2).toUpperCase()}
                            </div>
                            <div>
                              <h3 className="text-sm font-medium text-gray-700">{member.name}</h3>
                              <p className="text-xs text-gray-500">{member.role} &middot; {member.specialty}</p>
                            </div>
                          </div>
                        </div>
                      ))}
                    </div>
                  </div>
                )}
              </>
            ) : (
              <div className="overflow-hidden rounded-lg border border-gray-200 bg-white">
                <table className="w-full text-sm">
                  <thead>
                    <tr className="border-b border-gray-200 bg-gray-50">
                      <th className="px-4 py-3 text-left font-medium text-gray-600">Name</th>
                      <th className="px-4 py-3 text-left font-medium text-gray-600">Role</th>
                      <th className="px-4 py-3 text-left font-medium text-gray-600">Specialty</th>
                      <th className="px-4 py-3 text-left font-medium text-gray-600">Assigned</th>
                      <th className="px-4 py-3 text-left font-medium text-gray-600">Status</th>
                      <th className="px-4 py-3 text-left font-medium text-gray-600"></th>
                    </tr>
                  </thead>
                  <tbody>
                    {members.map((member) => (
                      <tr key={member.id} className="border-b border-gray-100 transition-colors hover:bg-gray-50">
                        <td className="px-4 py-3 text-gray-900">{member.name}</td>
                        <td className="px-4 py-3 text-gray-700">{member.role}</td>
                        <td className="px-4 py-3 text-gray-700">{member.specialty}</td>
                        <td className="px-4 py-3 text-gray-500">{member.assignedAt ? member.assignedAt.slice(0, 10) : "—"}</td>
                        <td className="px-4 py-3 text-xs text-gray-500">—</td>
                        <td className="px-4 py-3">
                          <span className={`rounded-full px-2 py-0.5 text-xs font-medium ${member.status === "active" ? "bg-green-100 text-green-700" : "bg-gray-100 text-gray-600"}`}>{member.status}</span>
                        </td>
                        <td className="px-4 py-3">
                          <button type="button" onClick={() => removeMember.mutate({ memberId: member.id, patientId })} disabled={removeMember.isPending} className="p-1 text-gray-400 hover:text-red-500 disabled:opacity-50">
                            <Trash2 className="h-3.5 w-3.5" />
                          </button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        )}
      </PageShell>
    </EHRLayout>
  );
}

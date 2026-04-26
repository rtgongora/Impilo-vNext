"use client";

import { useState } from "react";
import { useParams } from "next/navigation";
import { ClipboardList, Edit3, Home, Loader2, Save, Target, Users } from "lucide-react";
import { ClinicalReviewHeader } from "@/components/ehr/ClinicalReviewHeader";
import { PageShell } from "@/components/PageShell";
import { useEncounters } from "@/hooks/queries/useEncounters";
import type { SocialHistoryEntry } from "@/hooks/queries/useStructuredHistory";
import { useSocialHistory } from "@/hooks/queries/useStructuredHistory";
import { useFacilityStore } from "@/hooks/useFacilityStore";

const RISK_STYLES: Record<string, string> = {
  Low: "bg-green-100 text-green-700",
  Moderate: "bg-amber-100 text-amber-700",
  High: "bg-red-100 text-red-700",
  Unknown: "bg-gray-100 text-gray-600",
};

export default function SocialHistoryPage() {
  const params = useParams<{ patientId: string }>();
  const patientId = params.patientId;
  const facility = useFacilityStore((state) => state.facility);
  const { data: encountersData } = useEncounters(patientId);

  const { data, isLoading, isError, refetch } = useSocialHistory(patientId);
  const sections: SocialHistoryEntry[] = data?.data ?? [];
  const activeEncounter = (encountersData?.data ?? []).find(
    (encounter) =>
      encounter.attributes.status === "IN_PROGRESS" || encounter.attributes.status === "ACTIVE"
  );
  const [editingId, setEditingId] = useState<string | null>(null);
  const [editStatus, setEditStatus] = useState("");
  const [editDetail, setEditDetail] = useState("");

  function startEdit(section: SocialHistoryEntry) {
    setEditingId(section.id);
    setEditStatus(section.status);
    setEditDetail(section.detail);
  }

  function cancelEdit() {
    setEditingId(null);
    setEditStatus("");
    setEditDetail("");
  }

  const highRiskCount = sections.filter((section) => section.riskLevel?.toLowerCase() === "high").length;
  const moderateRiskCount = sections.filter((section) => {
    const r = section.riskLevel?.toLowerCase();
    return r === "moderate" || r === "medium";
  }).length;

  return (
    <PageShell title="Social History" subtitle="Social determinants of health and lifestyle factors">
        {isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="h-6 w-6 animate-spin text-gray-400" />
            <span className="ml-2 text-sm text-gray-500">Loading social history...</span>
          </div>
        ) : isError ? (
          <div className="flex flex-col items-center justify-center gap-3 py-16">
            <p className="text-sm text-gray-600">Unable to load social history for this patient.</p>
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
              badge="Social context"
              badgeIcon={Home}
              title="Keep practical barriers and supports visible so plans and goals still match real life outside the visit"
              description="Social history now sits in the same continuity layer as care plans, family context, and notes so risk factors can shape the next action instead of staying separate from the clinical plan."
              facilityName={facility?.name}
              encounterLabel={
                activeEncounter
                  ? `${activeEncounter.attributes.encounterType} since ${new Date(activeEncounter.attributes.startedAt).toLocaleString()}`
                  : null
              }
              actions={[
                { href: `/ehr/${patientId}/care-plans`, label: "Care Plans", icon: ClipboardList },
                { href: `/ehr/${patientId}/goals`, label: "Goals", icon: Target, tone: "secondary" },
                { href: `/ehr/${patientId}/family-history`, label: "Family History", icon: Users, tone: "secondary" },
                { href: `/ehr/${patientId}/notes`, label: "Notes", icon: ClipboardList, tone: "secondary" },
              ]}
              metrics={[
                {
                  label: "High risk",
                  value: String(highRiskCount),
                  detail: "Social issues that need immediate planning response.",
                },
                {
                  label: "Moderate risk",
                  value: String(moderateRiskCount),
                  detail: "Context factors likely to affect adherence or follow-through.",
                },
                {
                  label: "Sections tracked",
                  value: String(sections.length),
                  detail: "Structured social history areas kept visible to the team.",
                },
              ]}
            />

            <div className="rounded-3xl border border-slate-200 bg-slate-50/70 p-4">
              <p className="text-xs font-medium uppercase tracking-[0.18em] text-slate-500">Social continuity</p>
              <p className="mt-2 text-sm text-slate-800">
                {highRiskCount > 0 || moderateRiskCount > 0
                  ? `${highRiskCount + moderateRiskCount} social factor${highRiskCount + moderateRiskCount === 1 ? " is" : "s are"} carrying some risk, so this surface should directly inform care plans, goals, and the documented next step.`
                  : "Current social context looks stable; the continuity need is keeping that support picture visible when plans, goals, or team decisions are updated elsewhere."}
              </p>
              <p className="mt-1 text-xs text-slate-500">
                Use Care Plans to assign interventions, Goals to reflect realistic targets, and Notes to explain how social context changes the care path.
              </p>
            </div>

            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <Home className="h-5 w-5 text-teal-600" />
                <h2 className="text-lg font-semibold text-gray-900">Social Determinants of Health</h2>
              </div>
              <div className="flex items-center gap-2 text-xs">
                {highRiskCount > 0 && <span className="rounded-full bg-red-100 px-2.5 py-1 font-medium text-red-700">{highRiskCount} High Risk</span>}
                {moderateRiskCount > 0 && <span className="rounded-full bg-amber-100 px-2.5 py-1 font-medium text-amber-700">{moderateRiskCount} Moderate Risk</span>}
                {highRiskCount === 0 && moderateRiskCount === 0 && <span className="rounded-full bg-green-100 px-2.5 py-1 font-medium text-green-700">All Low Risk</span>}
              </div>
            </div>

            {sections.length === 0 ? (
              <div className="rounded-lg border border-gray-200 bg-white p-12 text-center">
                <Home className="mx-auto mb-3 h-10 w-10 text-gray-300" />
                <p className="text-sm text-gray-400">No social history recorded</p>
              </div>
            ) : (
              <div className="space-y-3">
                {sections.map((section) => {
                  const isEditing = editingId === section.id;

                  return (
                    <div key={section.id} className="rounded-lg border border-gray-200 bg-white p-5">
                      <div className="flex items-start gap-4">
                        <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-teal-100 text-teal-600">
                          <Home className="h-5 w-5" />
                        </div>
                        <div className="min-w-0 flex-1">
                          <div className="mb-1 flex items-center justify-between">
                            <h3 className="text-sm font-medium text-gray-900">{section.category}</h3>
                            <div className="flex items-center gap-2">
                              <span
                                className={`rounded-full px-2 py-0.5 text-xs font-medium ${
                                  RISK_STYLES[section.riskLevel] ?? RISK_STYLES.Unknown
                                }`}
                              >
                                {section.riskLevel} Risk
                              </span>
                              {!isEditing && (
                                <button onClick={() => startEdit(section)} className="p-1 text-gray-400 transition-colors hover:text-impilo-500">
                                  <Edit3 className="h-3.5 w-3.5" />
                                </button>
                              )}
                            </div>
                          </div>

                          {isEditing ? (
                            <div className="mt-2 space-y-3">
                              <div>
                                <label className="mb-1 block text-xs font-medium text-gray-600">Status</label>
                                <input type="text" value={editStatus} onChange={(e) => setEditStatus(e.target.value)} className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-impilo-400" />
                              </div>
                              <div>
                                <label className="mb-1 block text-xs font-medium text-gray-600">Details</label>
                                <textarea value={editDetail} onChange={(e) => setEditDetail(e.target.value)} rows={3} className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-impilo-400" />
                              </div>
                              <div className="flex items-center gap-2">
                                <button className="inline-flex items-center gap-1 rounded-lg bg-impilo-500 px-3 py-1.5 text-xs font-medium text-white transition-colors hover:bg-impilo-600">
                                  <Save className="h-3 w-3" /> Save
                                </button>
                                <button onClick={cancelEdit} className="rounded-lg border border-gray-300 px-3 py-1.5 text-xs font-medium text-gray-600 transition-colors hover:bg-gray-50">Cancel</button>
                              </div>
                            </div>
                          ) : (
                            <>
                              <p className="text-sm font-medium text-gray-700">{section.status}</p>
                              <p className="mt-1 text-xs text-gray-500">{section.detail}</p>
                              <p className="mt-2 text-[10px] text-gray-400">Last updated: {section.lastUpdated}</p>
                            </>
                          )}
                        </div>
                      </div>
                    </div>
                  );
                })}
              </div>
            )}
          </div>
        )}
      </PageShell>
  );
}

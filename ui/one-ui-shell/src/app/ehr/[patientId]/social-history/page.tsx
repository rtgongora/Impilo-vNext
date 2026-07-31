"use client";

import { useMemo, useState } from "react";
import { useParams } from "next/navigation";
import { ClipboardList, Edit3, Home, Loader2, Plus, Save, Target, Users } from "lucide-react";
import { ClinicalReviewHeader } from "@/components/ehr/ClinicalReviewHeader";
import { EHRLayout } from "@/components/EHRLayout";
import { PageShell } from "@/components/PageShell";
import {
  GOVERNED_SOCIAL_HISTORY_CATEGORIES,
  labelForSocialCategory,
} from "@/features/medicine/clerking/socialHistoryCategories";
import { useEncounters } from "@/hooks/queries/useEncounters";
import type { SocialHistoryEntry } from "@/hooks/queries/useStructuredHistory";
import { useRecordSocialHistory, useSocialHistory } from "@/hooks/queries/useStructuredHistory";
import { useFacilityStore } from "@/hooks/useFacilityStore";

const RISK_LEVELS: Array<"LOW" | "MODERATE" | "HIGH"> = ["LOW", "MODERATE", "HIGH"];

const RISK_STYLES: Record<string, string> = {
  Low: "bg-green-100 text-green-700",
  Moderate: "bg-amber-100 text-warning-foreground",
  High: "bg-red-100 text-danger",
  Unknown: "bg-neutral-100 text-muted-foreground",
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
      encounter.attributes.isOpen
  );
  const [editingId, setEditingId] = useState<string | null>(null);
  const [editStatus, setEditStatus] = useState("");
  const [editDetail, setEditDetail] = useState("");
  const [editRiskLevel, setEditRiskLevel] = useState<"LOW" | "MODERATE" | "HIGH">("LOW");
  const [newCategory, setNewCategory] = useState(GOVERNED_SOCIAL_HISTORY_CATEGORIES[0]?.code ?? "TOBACCO");
  const [customCategory, setCustomCategory] = useState("");
  const [newStatus, setNewStatus] = useState("");
  const [newDetail, setNewDetail] = useState("");
  const [newRiskLevel, setNewRiskLevel] = useState<"LOW" | "MODERATE" | "HIGH" | "">("");
  const recordSocialHistory = useRecordSocialHistory(patientId);

  const recordedCodes = useMemo(
    () => new Set(sections.map((s) => s.category?.toUpperCase())),
    [sections]
  );

  function startEdit(section: SocialHistoryEntry) {
    setEditingId(section.id);
    setEditStatus(section.status);
    setEditDetail(section.detail);
    setEditRiskLevel((section.riskLevel?.toUpperCase() as "LOW" | "MODERATE" | "HIGH") || "LOW");
  }

  function cancelEdit() {
    setEditingId(null);
    setEditStatus("");
    setEditDetail("");
    recordSocialHistory.reset();
  }

  function saveEdit(section: SocialHistoryEntry) {
    recordSocialHistory.mutate(
      { category: section.category, status: editStatus, detail: editDetail, riskLevel: editRiskLevel },
      { onSuccess: () => cancelEdit() }
    );
  }

  const highRiskCount = sections.filter((section) => section.riskLevel?.toLowerCase() === "high").length;
  const moderateRiskCount = sections.filter((section) => {
    const r = section.riskLevel?.toLowerCase();
    return r === "moderate" || r === "medium";
  }).length;

  return (
    <EHRLayout>
      <PageShell title="Social History" subtitle="Social determinants of health and lifestyle factors">
        {isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
            <span className="ml-2 text-sm text-muted-foreground">Loading social history...</span>
          </div>
        ) : isError ? (
          <div className="flex flex-col items-center justify-center gap-3 py-16">
            <p className="text-sm text-muted-foreground">Unable to load social history for this patient.</p>
            <button
              type="button"
              onClick={() => void refetch()}
              className="rounded-lg bg-primary px-4 py-2 text-sm font-medium text-white hover:bg-primary-hover"
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

            <div className="rounded-3xl border border-border bg-background/70 p-4">
              <p className="text-xs font-medium uppercase tracking-[0.18em] text-muted-foreground">Social continuity</p>
              <p className="mt-2 text-sm text-foreground">
                {highRiskCount > 0 || moderateRiskCount > 0
                  ? `${highRiskCount + moderateRiskCount} social factor${highRiskCount + moderateRiskCount === 1 ? " is" : "s are"} carrying some risk, so this surface should directly inform care plans, goals, and the documented next step.`
                  : "Current social context looks stable; the continuity need is keeping that support picture visible when plans, goals, or team decisions are updated elsewhere."}
              </p>
              <p className="mt-1 text-xs text-muted-foreground">
                Use Care Plans to assign interventions, Goals to reflect realistic targets, and Notes to explain how social context changes the care path.
              </p>
            </div>

            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <Home className="h-5 w-5 text-teal-600" />
                <h2 className="text-lg font-semibold text-foreground">Social Determinants of Health</h2>
              </div>
              <div className="flex items-center gap-2 text-xs">
                {highRiskCount > 0 && <span className="rounded-full bg-red-100 px-2.5 py-1 font-medium text-danger">{highRiskCount} High Risk</span>}
                {moderateRiskCount > 0 && <span className="rounded-full bg-amber-100 px-2.5 py-1 font-medium text-warning-foreground">{moderateRiskCount} Moderate Risk</span>}
                {highRiskCount === 0 && moderateRiskCount === 0 && <span className="rounded-full bg-green-100 px-2.5 py-1 font-medium text-green-700">All Low Risk</span>}
              </div>
            </div>

            <div className="rounded-lg border border-border bg-card p-4 space-y-3" data-testid="social-history-add">
              <p className="text-sm font-medium text-foreground">Record a social-history category</p>
              <p className="text-xs text-muted-foreground">
                Prefer governed categories (occupation, exposures, tobacco/alcohol/substance, diet/activity/sleep,
                support/housing/food). Unknown categories may still be appended. Clinician-documented lifestyle is
                not Simba wellness truth.
              </p>
              <div className="grid gap-3 md:grid-cols-2">
                <div>
                  <label htmlFor="new-social-category" className="mb-1 block text-xs font-medium text-muted-foreground">
                    Category
                  </label>
                  <select
                    id="new-social-category"
                    value={newCategory}
                    onChange={(e) => setNewCategory(e.target.value)}
                    className="w-full rounded-lg border border-border px-3 py-2 text-sm"
                  >
                    {GOVERNED_SOCIAL_HISTORY_CATEGORIES.map((c) => (
                      <option key={c.code} value={c.code} disabled={recordedCodes.has(c.code)}>
                        {c.label}
                        {recordedCodes.has(c.code) ? " (already recorded)" : ""}
                      </option>
                    ))}
                    <option value="__OTHER__">Other (custom category)</option>
                  </select>
                </div>
                {newCategory === "__OTHER__" && (
                  <div>
                    <label htmlFor="custom-social-category" className="mb-1 block text-xs font-medium text-muted-foreground">
                      Custom category code
                    </label>
                    <input
                      id="custom-social-category"
                      value={customCategory}
                      onChange={(e) => setCustomCategory(e.target.value)}
                      placeholder="e.g. TRANSPORT_BARRIER"
                      className="w-full rounded-lg border border-border px-3 py-2 text-sm"
                    />
                  </div>
                )}
                <div>
                  <label htmlFor="new-social-status" className="mb-1 block text-xs font-medium text-muted-foreground">
                    Status / finding
                  </label>
                  <input
                    id="new-social-status"
                    value={newStatus}
                    onChange={(e) => setNewStatus(e.target.value)}
                    className="w-full rounded-lg border border-border px-3 py-2 text-sm"
                  />
                </div>
                <div>
                  <label htmlFor="new-social-risk" className="mb-1 block text-xs font-medium text-muted-foreground">
                    Risk level (optional — leave blank if not assessed)
                  </label>
                  <select
                    id="new-social-risk"
                    value={newRiskLevel}
                    onChange={(e) => setNewRiskLevel(e.target.value as "LOW" | "MODERATE" | "HIGH" | "")}
                    className="w-full rounded-lg border border-border px-3 py-2 text-sm"
                  >
                    <option value="">Not assessed</option>
                    {RISK_LEVELS.map((level) => (
                      <option key={level} value={level}>
                        {level}
                      </option>
                    ))}
                  </select>
                </div>
                <div className="md:col-span-2">
                  <label htmlFor="new-social-detail" className="mb-1 block text-xs font-medium text-muted-foreground">
                    Detail
                  </label>
                  <textarea
                    id="new-social-detail"
                    value={newDetail}
                    onChange={(e) => setNewDetail(e.target.value)}
                    rows={2}
                    className="w-full rounded-lg border border-border px-3 py-2 text-sm"
                  />
                </div>
              </div>
              {recordSocialHistory.isError && (
                <p className="text-xs text-danger">
                  This entry was not saved. Nothing on the patient&rsquo;s record changed — try again.
                </p>
              )}
              <button
                type="button"
                disabled={
                  recordSocialHistory.isPending ||
                  !newStatus.trim() ||
                  (newCategory === "__OTHER__" && !customCategory.trim())
                }
                onClick={() => {
                  const category =
                    newCategory === "__OTHER__" ? customCategory.trim().toUpperCase() : newCategory;
                  recordSocialHistory.mutate(
                    {
                      category,
                      status: newStatus.trim(),
                      detail: newDetail.trim(),
                      riskLevel: newRiskLevel || undefined,
                    },
                    {
                      onSuccess: () => {
                        setNewStatus("");
                        setNewDetail("");
                        setNewRiskLevel("");
                        setCustomCategory("");
                      },
                    }
                  );
                }}
                className="inline-flex items-center gap-1 rounded-lg bg-primary px-3 py-1.5 text-xs font-medium text-white disabled:opacity-60"
              >
                {recordSocialHistory.isPending ? (
                  <Loader2 className="h-3 w-3 animate-spin" />
                ) : (
                  <Plus className="h-3 w-3" />
                )}
                Add social history
              </button>
            </div>

            {sections.length === 0 ? (
              <div className="rounded-lg border border-border bg-card p-12 text-center">
                <Home className="mx-auto mb-3 h-10 w-10 text-muted-foreground" />
                <p className="text-sm text-muted-foreground">No social history recorded</p>
              </div>
            ) : (
              <div className="space-y-3">
                {sections.map((section) => {
                  const isEditing = editingId === section.id;

                  return (
                    <div key={section.id} className="rounded-lg border border-border bg-card p-5">
                      <div className="flex items-start gap-4">
                        <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-teal-100 text-teal-600">
                          <Home className="h-5 w-5" />
                        </div>
                        <div className="min-w-0 flex-1">
                          <div className="mb-1 flex items-center justify-between">
                            <h3 className="text-sm font-medium text-foreground">
                              {labelForSocialCategory(section.category)}
                            </h3>
                            <div className="flex items-center gap-2">
                              <span
                                className={`rounded-full px-2 py-0.5 text-xs font-medium ${
                                  RISK_STYLES[section.riskLevel] ?? RISK_STYLES.Unknown
                                }`}
                              >
                                {section.riskLevel} Risk
                              </span>
                              {!isEditing && (
                                <button
                                  type="button"
                                  aria-label={`Edit ${section.category}`}
                                  onClick={() => startEdit(section)}
                                  className="p-1 text-muted-foreground transition-colors hover:text-primary"
                                >
                                  <Edit3 className="h-3.5 w-3.5" />
                                </button>
                              )}
                            </div>
                          </div>

                          {isEditing ? (
                            <div className="mt-2 space-y-3">
                              <div>
                                <label htmlFor={`edit-status-${section.id}`} className="mb-1 block text-xs font-medium text-muted-foreground">Status</label>
                                <input id={`edit-status-${section.id}`} type="text" value={editStatus} onChange={(e) => setEditStatus(e.target.value)} className="w-full rounded-lg border border-border px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/40" />
                              </div>
                              <div>
                                <label htmlFor={`edit-detail-${section.id}`} className="mb-1 block text-xs font-medium text-muted-foreground">Details</label>
                                <textarea id={`edit-detail-${section.id}`} value={editDetail} onChange={(e) => setEditDetail(e.target.value)} rows={3} className="w-full rounded-lg border border-border px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/40" />
                              </div>
                              <div>
                                <label htmlFor={`edit-risk-${section.id}`} className="mb-1 block text-xs font-medium text-muted-foreground">Risk Level</label>
                                <select
                                  id={`edit-risk-${section.id}`}
                                  value={editRiskLevel}
                                  onChange={(e) => setEditRiskLevel(e.target.value as "LOW" | "MODERATE" | "HIGH")}
                                  className="w-full rounded-lg border border-border px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/40"
                                >
                                  {RISK_LEVELS.map((level) => (<option key={level} value={level}>{level}</option>))}
                                </select>
                              </div>
                              {recordSocialHistory.isError && (
                                <p className="text-xs text-danger">
                                  This entry was not saved. Nothing on the patient&rsquo;s record changed — try again.
                                </p>
                              )}
                              <div className="flex items-center gap-2">
                                <button
                                  type="button"
                                  onClick={() => saveEdit(section)}
                                  disabled={recordSocialHistory.isPending}
                                  className="inline-flex items-center gap-1 rounded-lg bg-primary px-3 py-1.5 text-xs font-medium text-white transition-colors hover:bg-primary-hover disabled:opacity-60"
                                >
                                  {recordSocialHistory.isPending ? (
                                    <Loader2 className="h-3 w-3 animate-spin" />
                                  ) : (
                                    <Save className="h-3 w-3" />
                                  )}
                                  Save
                                </button>
                                <button onClick={cancelEdit} className="rounded-lg border border-border px-3 py-1.5 text-xs font-medium text-muted-foreground transition-colors hover:bg-background">Cancel</button>
                              </div>
                            </div>
                          ) : (
                            <>
                              <p className="text-sm font-medium text-foreground">{section.status}</p>
                              <p className="mt-1 text-xs text-muted-foreground">{section.detail}</p>
                              <p className="mt-2 text-[10px] text-muted-foreground">Last updated: {section.lastUpdated}</p>
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
    </EHRLayout>
  );
}

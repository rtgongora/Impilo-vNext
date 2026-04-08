"use client";

/**
 * Conditions — View and manage patient conditions / problem list.
 * Route: /ehr/[patientId]/conditions | pageTitle: "Conditions"
 */

import { useState } from "react";
import { useParams } from "next/navigation";
import { ArrowRightLeft, CheckCircle2, HeartPulse, Loader2, Pill, Plus, ShieldAlert } from "lucide-react";
import { ClinicalReviewHeader } from "@/components/ehr/ClinicalReviewHeader";
import { EHRLayout } from "@/components/EHRLayout";
import { PageShell } from "@/components/PageShell";
import {
  useConditions,
  useCreateCondition,
  useResolveCondition,
  type ConditionResource } from "@/hooks/queries/useConditions";
import { useEncounters } from "@/hooks/queries/useEncounters";
import { useFacilityStore } from "@/hooks/useFacilityStore";

/* ------------------------------------------------------------------ */
/*  Badge helpers                                                      */
/* ------------------------------------------------------------------ */

const STATUS_BADGE: Record<string, string> = {
  ACTIVE: "bg-red-100 text-red-700",
  RESOLVED: "bg-green-100 text-green-700",
  INACTIVE: "bg-gray-100 text-gray-600" };

const SEVERITY_BADGE: Record<string, string> = {
  MILD: "bg-green-100 text-green-700",
  MODERATE: "bg-yellow-100 text-yellow-700",
  SEVERE: "bg-red-100 text-red-700" };

/* ------------------------------------------------------------------ */
/*  Empty form state                                                   */
/* ------------------------------------------------------------------ */

const EMPTY_FORM = {
  condition_name: "",
  icd_code: "",
  category: "PROBLEM_LIST",
  clinical_status: "ACTIVE",
  severity: "MODERATE",
  onset_date: "",
  notes: "" };

/* ------------------------------------------------------------------ */
/*  Page component                                                     */
/* ------------------------------------------------------------------ */

export default function ConditionsPage() {
  const params = useParams<{ patientId: string }>();
  const patientId = params.patientId;
  const facility = useFacilityStore((state) => state.facility);
  const { data: encountersData } = useEncounters(patientId);

  const { data: conditionsData, isLoading } = useConditions(patientId);
  const createCondition = useCreateCondition();
  const resolveCondition = useResolveCondition();

  const conditions: ConditionResource[] = conditionsData?.data ?? [];
  const activeEncounter = (encountersData?.data ?? []).find(
    (encounter) =>
      encounter.attributes.status === "IN_PROGRESS" || encounter.attributes.status === "ACTIVE",
  );
  const activeConditions = conditions.filter((condition) => condition.attributes.clinicalStatus === "ACTIVE");
  const resolvedConditions = conditions.filter((condition) => condition.attributes.clinicalStatus === "RESOLVED");

  const [showForm, setShowForm] = useState(false);
  const [form, setForm] = useState({ ...EMPTY_FORM });

  function updateField(field: string, value: string) {
    setForm((prev) => ({ ...prev, [field]: value }));
  }

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    createCondition.mutate(
      {
        patientId,
        conditionName: form.condition_name,
        icdCode: form.icd_code.trim() || null,
        category: form.category,
        severity: form.severity,
        onsetDate: form.onset_date || null,
        notes: form.notes.trim() || null },
      {
        onSuccess: () => {
          setForm({ ...EMPTY_FORM });
          setShowForm(false);
        } },
    );
  }

  function handleResolve(id: string) {
    resolveCondition.mutate({ id });
  }

  return (
    <EHRLayout>
      <PageShell title="Conditions" subtitle="Patient problem list and condition management">

        {isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="w-6 h-6 animate-spin text-gray-400" />
            <span className="ml-2 text-sm text-gray-500">Loading conditions...</span>
          </div>
        ) : (
          <div className="space-y-6">
            <ClinicalReviewHeader
              badge="Problem list"
              badgeIcon={HeartPulse}
              title="Keep the active problem list aligned with the encounter story and medication safety review"
              description="Conditions now sit inside the same longitudinal review loop as history, medications, and allergies so teams can update the problem list without losing the current encounter context."
              facilityName={facility?.name}
              encounterLabel={
                activeEncounter
                  ? `${activeEncounter.attributes.encounterType} since ${new Date(activeEncounter.attributes.startedAt).toLocaleString()}`
                  : null
              }
              actions={[
                { href: `/ehr/${patientId}/history`, label: "History", icon: ArrowRightLeft },
                { href: `/ehr/${patientId}/medications`, label: "Medications", icon: Pill, tone: "secondary" },
                { href: `/ehr/${patientId}/allergies`, label: "Allergies", icon: ShieldAlert, tone: "secondary" },
              ]}
              metrics={[
                {
                  label: "Active",
                  value: String(activeConditions.length),
                  detail: "Current conditions likely to influence this encounter.",
                },
                {
                  label: "Resolved",
                  value: String(resolvedConditions.length),
                  detail: "Historical problems kept visible for longitudinal review.",
                },
                {
                  label: "Encounter context",
                  value: activeEncounter ? "In progress" : "None",
                  detail: activeEncounter
                    ? "Add encounter diagnoses directly while the visit is active."
                    : "Problem list updates will stand alone until a new encounter starts.",
                },
              ]}
            />

            {/* Header row with action button */}
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <HeartPulse className="w-5 h-5 text-red-500" />
                <h2 className="text-lg font-semibold text-gray-900">
                  Conditions ({conditions.length})
                </h2>
              </div>
              <button
                type="button"
                onClick={() => setShowForm((prev) => !prev)}
                className="inline-flex items-center gap-1.5 px-4 py-2 bg-blue-600 text-white text-sm font-medium rounded-lg hover:bg-blue-700 transition-colors"
              >
                <Plus className="w-4 h-4" />
                Add Condition
              </button>
            </div>

            {/* Add Condition form */}
            {showForm && (
              <div className="bg-white rounded-lg border border-gray-200 p-5">
                <h3 className="font-medium text-gray-900 mb-4">New Condition</h3>
                <form onSubmit={handleSubmit} className="space-y-4">
                  <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
                    <div>
                      <label className="block text-xs font-medium text-gray-600 mb-1">
                        Condition Name
                      </label>
                      <input
                        type="text"
                        value={form.condition_name}
                        onChange={(e) => updateField("condition_name", e.target.value)}
                        placeholder="e.g. Type 2 Diabetes Mellitus"
                        required
                        className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
                      />
                    </div>
                    <div>
                      <label className="block text-xs font-medium text-gray-600 mb-1">
                        ICD Code
                      </label>
                      <input
                        type="text"
                        value={form.icd_code}
                        onChange={(e) => updateField("icd_code", e.target.value)}
                        placeholder="e.g. E11.9"
                        className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
                      />
                    </div>
                    <div>
                      <label className="block text-xs font-medium text-gray-600 mb-1">
                        Category
                      </label>
                      <select
                        value={form.category}
                        onChange={(e) => updateField("category", e.target.value)}
                        className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
                      >
                        <option value="ENCOUNTER_DIAGNOSIS">Encounter Diagnosis</option>
                        <option value="PROBLEM_LIST">Problem List</option>
                        <option value="CHRONIC">Chronic</option>
                      </select>
                    </div>
                    <div>
                      <label className="block text-xs font-medium text-gray-600 mb-1">
                        Clinical Status
                      </label>
                      <select
                        value={form.clinical_status}
                        onChange={(e) => updateField("clinical_status", e.target.value)}
                        className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
                      >
                        <option value="ACTIVE">Active</option>
                        <option value="RESOLVED">Resolved</option>
                        <option value="INACTIVE">Inactive</option>
                      </select>
                    </div>
                    <div>
                      <label className="block text-xs font-medium text-gray-600 mb-1">
                        Severity
                      </label>
                      <select
                        value={form.severity}
                        onChange={(e) => updateField("severity", e.target.value)}
                        className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
                      >
                        <option value="MILD">Mild</option>
                        <option value="MODERATE">Moderate</option>
                        <option value="SEVERE">Severe</option>
                      </select>
                    </div>
                    <div>
                      <label className="block text-xs font-medium text-gray-600 mb-1">
                        Onset Date
                      </label>
                      <input
                        type="date"
                        value={form.onset_date}
                        onChange={(e) => updateField("onset_date", e.target.value)}
                        className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
                      />
                    </div>
                    <div className="md:col-span-2 lg:col-span-3">
                      <label className="block text-xs font-medium text-gray-600 mb-1">
                        Notes
                      </label>
                      <textarea
                        value={form.notes}
                        onChange={(e) => updateField("notes", e.target.value)}
                        rows={2}
                        placeholder="Additional clinical notes..."
                        className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
                      />
                    </div>
                  </div>

                  <div className="flex items-center gap-3 pt-2">
                    <button
                      type="submit"
                      disabled={createCondition.isPending}
                      className="inline-flex items-center gap-1.5 px-4 py-2 bg-blue-600 text-white text-sm font-medium rounded-lg hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
                    >
                      {createCondition.isPending && (
                        <Loader2 className="w-4 h-4 animate-spin" />
                      )}
                      Save Condition
                    </button>
                    <button
                      type="button"
                      onClick={() => {
                        setForm({ ...EMPTY_FORM });
                        setShowForm(false);
                      }}
                      className="px-4 py-2 text-sm font-medium text-gray-700 rounded-lg border border-gray-300 hover:bg-gray-50 transition-colors"
                    >
                      Cancel
                    </button>
                  </div>

                  {createCondition.isError && (
                    <p className="text-sm text-red-600">
                      Failed to create condition. Please try again.
                    </p>
                  )}
                </form>
              </div>
            )}

            {/* Conditions table */}
            {conditions.length === 0 ? (
              <div className="bg-white rounded-lg border border-gray-200 p-12 text-center">
                <HeartPulse className="w-10 h-10 text-gray-300 mx-auto mb-3" />
                <p className="text-gray-400 text-sm">No conditions recorded yet</p>
              </div>
            ) : (
              <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
                <div className="overflow-x-auto">
                  <table className="w-full text-sm">
                    <thead>
                      <tr className="border-b border-gray-200 bg-gray-50">
                        <th className="text-left px-4 py-3 font-medium text-gray-600">Condition</th>
                        <th className="text-left px-4 py-3 font-medium text-gray-600">ICD Code</th>
                        <th className="text-left px-4 py-3 font-medium text-gray-600">Category</th>
                        <th className="text-left px-4 py-3 font-medium text-gray-600">Status</th>
                        <th className="text-left px-4 py-3 font-medium text-gray-600">Severity</th>
                        <th className="text-left px-4 py-3 font-medium text-gray-600">Onset Date</th>
                        <th className="text-left px-4 py-3 font-medium text-gray-600">Notes</th>
                        <th className="text-left px-4 py-3 font-medium text-gray-600">Actions</th>
                      </tr>
                    </thead>
                    <tbody>
                      {conditions.map((condition) => {
                        const a = condition.attributes;
                        return (
                          <tr
                            key={condition.id}
                            className="border-b border-gray-100 hover:bg-gray-50 transition-colors"
                          >
                            <td className="px-4 py-3 font-medium text-gray-900">
                              {a.conditionName}
                            </td>
                            <td className="px-4 py-3 text-gray-700 font-mono text-xs">
                              {a.icdCode ?? "—"}
                            </td>
                            <td className="px-4 py-3 text-gray-700 text-xs">
                              {a.category.replace(/_/g, " ")}
                            </td>
                            <td className="px-4 py-3">
                              <span
                                className={`px-2.5 py-0.5 text-xs font-medium rounded-full ${
                                  STATUS_BADGE[a.clinicalStatus] ?? "bg-gray-100 text-gray-600"
                                }`}
                              >
                                {a.clinicalStatus}
                              </span>
                            </td>
                            <td className="px-4 py-3">
                              <span
                                className={`px-2.5 py-0.5 text-xs font-medium rounded-full ${
                                  SEVERITY_BADGE[a.severity] ?? "bg-gray-100 text-gray-600"
                                }`}
                              >
                                {a.severity}
                              </span>
                            </td>
                            <td className="px-4 py-3 text-gray-700">
                              {a.onsetDate
                                ? new Date(a.onsetDate).toLocaleDateString()
                                : "—"}
                            </td>
                            <td className="px-4 py-3 text-gray-700 max-w-[200px] truncate">
                              {a.notes ?? "—"}
                            </td>
                            <td className="px-4 py-3">
                              {a.clinicalStatus === "ACTIVE" && (
                                <button
                                  type="button"
                                  onClick={() => handleResolve(condition.id)}
                                  disabled={resolveCondition.isPending}
                                  className="inline-flex items-center gap-1 px-2.5 py-1 text-xs font-medium text-green-700 bg-green-50 rounded-md hover:bg-green-100 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
                                >
                                  <CheckCircle2 className="w-3.5 h-3.5" />
                                  Resolve
                                </button>
                              )}
                            </td>
                          </tr>
                        );
                      })}
                    </tbody>
                  </table>
                </div>
              </div>
            )}
          </div>
        )}
      </PageShell>
    </EHRLayout>
  );
}

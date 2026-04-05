"use client";

/**
 * Vitals — View past vitals and record new readings.
 * Route: /ehr/[patientId]/vitals | pageTitle: "Vitals"
 */

import { useState } from "react";
import { useParams } from "next/navigation";
import { Activity, Loader2, Plus } from "lucide-react";
import { EHRLayout } from "@/components/EHRLayout";
import { PageShell } from "@/components/PageShell";
import {
  useVitals,
  useRecordVitals,
  type VitalsResource,
} from "@/hooks/queries/useVitals";
import { useAuthStore } from "@/hooks/useAuthStore";
import { useRoleGroup } from "@/hooks/useRoleGroup";
import { useEncounters } from "@/hooks/queries/useEncounters";
import { VitalsTrendPanel } from "@/components/VitalsTrendChart";

export default function VitalsPage() {
  const params = useParams<{ patientId: string }>();
  const patientId = params.patientId;

  const { user } = useAuthStore();
  const { isClinical } = useRoleGroup();
  const { data: encountersData } = useEncounters(patientId);
  const activeEncounter = (encountersData?.data ?? []).find(
    (e) => e.attributes.status === "IN_PROGRESS" || e.attributes.status === "ACTIVE"
  );
  const encounterId = activeEncounter?.id ?? "";
  const { data: vitalsData, isLoading } = useVitals(patientId);
  const recordVitals = useRecordVitals();

  const vitals: VitalsResource[] = vitalsData?.data ?? [];

  const [showForm, setShowForm] = useState(false);
  const [systolic, setSystolic] = useState("");
  const [diastolic, setDiastolic] = useState("");
  const [heartRate, setHeartRate] = useState("");
  const [temperature, setTemperature] = useState("");
  const [respiratoryRate, setRespiratoryRate] = useState("");
  const [oxygenSaturation, setOxygenSaturation] = useState("");
  const [weight, setWeight] = useState("");
  const [height, setHeight] = useState("");
  const [painScore, setPainScore] = useState("");
  const [notes, setNotes] = useState("");

  function resetForm() {
    setSystolic("");
    setDiastolic("");
    setHeartRate("");
    setTemperature("");
    setRespiratoryRate("");
    setOxygenSaturation("");
    setWeight("");
    setHeight("");
    setPainScore("");
    setNotes("");
  }

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();

    const toNum = (v: string) => (v.trim() === "" ? null : Number(v));

    recordVitals.mutate(
      {
        patientId,
        encounterId,
        recorded_by: user?.id ?? "system",
        systolic: toNum(systolic),
        diastolic: toNum(diastolic),
        heartRate: toNum(heartRate),
        temperature: toNum(temperature),
        respiratoryRate: toNum(respiratoryRate),
        oxygenSaturation: toNum(oxygenSaturation),
        weight: toNum(weight),
        height: toNum(height),
        painScore: toNum(painScore),
        notes: notes.trim() || null },
      {
        onSuccess: () => {
          resetForm();
          setShowForm(false);
        } },
    );
  }

  function fmt(v: number | null | undefined) {
    return v != null ? String(v) : "—";
  }

  return (
    <EHRLayout>
      <PageShell title="Vitals" subtitle="Patient vital signs history and recording">

        {isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="w-6 h-6 animate-spin text-gray-400" />
            <span className="ml-2 text-sm text-gray-500">Loading vitals...</span>
          </div>
        ) : (
          <div className="space-y-6">
            {/* Header row with action button */}
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <Activity className="w-5 h-5 text-red-600" />
                <h2 className="text-lg font-semibold text-gray-900">
                  Recorded Vitals
                </h2>
              </div>
              {isClinical && (
              <button
                type="button"
                onClick={() => setShowForm((prev) => !prev)}
                className="inline-flex items-center gap-1.5 px-4 py-2 bg-blue-600 text-white text-sm font-medium rounded-lg hover:bg-blue-700 transition-colors"
              >
                <Plus className="w-4 h-4" />
                Record Vitals
              </button>
              )}
            </div>

            {/* Vitals Trend Charts */}
            {vitals.length >= 2 && (
              <VitalsTrendPanel vitals={vitals.map((v) => ({ attributes: v.attributes as Record<string, unknown> }))} />
            )}

            {/* New vitals form */}
            {showForm && (
              <div className="bg-white rounded-lg border border-gray-200 p-5">
                <h3 className="font-medium text-gray-900 mb-4">New Vitals Entry</h3>
                <form onSubmit={handleSubmit} className="space-y-4">
                  <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-5 gap-4">
                    <div>
                      <label className="block text-xs font-medium text-gray-600 mb-1">
                        Systolic (mmHg)
                      </label>
                      <input
                        type="number"
                        value={systolic}
                        onChange={(e) => setSystolic(e.target.value)}
                        className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
                        placeholder="120"
                      />
                    </div>
                    <div>
                      <label className="block text-xs font-medium text-gray-600 mb-1">
                        Diastolic (mmHg)
                      </label>
                      <input
                        type="number"
                        value={diastolic}
                        onChange={(e) => setDiastolic(e.target.value)}
                        className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
                        placeholder="80"
                      />
                    </div>
                    <div>
                      <label className="block text-xs font-medium text-gray-600 mb-1">
                        Heart Rate (bpm)
                      </label>
                      <input
                        type="number"
                        value={heartRate}
                        onChange={(e) => setHeartRate(e.target.value)}
                        className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
                        placeholder="72"
                      />
                    </div>
                    <div>
                      <label className="block text-xs font-medium text-gray-600 mb-1">
                        Temperature (°C)
                      </label>
                      <input
                        type="number"
                        step="0.1"
                        value={temperature}
                        onChange={(e) => setTemperature(e.target.value)}
                        className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
                        placeholder="36.6"
                      />
                    </div>
                    <div>
                      <label className="block text-xs font-medium text-gray-600 mb-1">
                        Resp. Rate (breaths/min)
                      </label>
                      <input
                        type="number"
                        value={respiratoryRate}
                        onChange={(e) => setRespiratoryRate(e.target.value)}
                        className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
                        placeholder="16"
                      />
                    </div>
                    <div>
                      <label className="block text-xs font-medium text-gray-600 mb-1">
                        SpO2 (%)
                      </label>
                      <input
                        type="number"
                        value={oxygenSaturation}
                        onChange={(e) => setOxygenSaturation(e.target.value)}
                        className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
                        placeholder="98"
                      />
                    </div>
                    <div>
                      <label className="block text-xs font-medium text-gray-600 mb-1">
                        Weight (kg)
                      </label>
                      <input
                        type="number"
                        step="0.1"
                        value={weight}
                        onChange={(e) => setWeight(e.target.value)}
                        className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
                        placeholder="70"
                      />
                    </div>
                    <div>
                      <label className="block text-xs font-medium text-gray-600 mb-1">
                        Height (cm)
                      </label>
                      <input
                        type="number"
                        step="0.1"
                        value={height}
                        onChange={(e) => setHeight(e.target.value)}
                        className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
                        placeholder="170"
                      />
                    </div>
                    <div>
                      <label className="block text-xs font-medium text-gray-600 mb-1">
                        Pain Score (0-10)
                      </label>
                      <input
                        type="number"
                        min="0"
                        max="10"
                        value={painScore}
                        onChange={(e) => setPainScore(e.target.value)}
                        className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
                        placeholder="0"
                      />
                    </div>
                    <div className="col-span-2 md:col-span-3 lg:col-span-5">
                      <label className="block text-xs font-medium text-gray-600 mb-1">
                        Notes
                      </label>
                      <textarea
                        value={notes}
                        onChange={(e) => setNotes(e.target.value)}
                        rows={2}
                        className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
                        placeholder="Additional observations..."
                      />
                    </div>
                  </div>

                  <div className="flex items-center gap-3 pt-2">
                    <button
                      type="submit"
                      disabled={recordVitals.isPending}
                      className="inline-flex items-center gap-1.5 px-4 py-2 bg-blue-600 text-white text-sm font-medium rounded-lg hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
                    >
                      {recordVitals.isPending && (
                        <Loader2 className="w-4 h-4 animate-spin" />
                      )}
                      Save Vitals
                    </button>
                    <button
                      type="button"
                      onClick={() => {
                        resetForm();
                        setShowForm(false);
                      }}
                      className="px-4 py-2 text-sm font-medium text-gray-700 rounded-lg border border-gray-300 hover:bg-gray-50 transition-colors"
                    >
                      Cancel
                    </button>
                  </div>

                  {recordVitals.isError && (
                    <p className="text-sm text-red-600">
                      Failed to record vitals. Please try again.
                    </p>
                  )}
                </form>
              </div>
            )}

            {/* Vitals table */}
            {vitals.length === 0 ? (
              <div className="bg-white rounded-lg border border-gray-200 p-12 text-center">
                <Activity className="w-10 h-10 text-gray-300 mx-auto mb-3" />
                <p className="text-gray-400 text-sm">No vitals recorded yet</p>
              </div>
            ) : (
              <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
                <div className="overflow-x-auto">
                  <table className="w-full text-sm">
                    <thead>
                      <tr className="border-b border-gray-200 bg-gray-50">
                        <th className="text-left px-4 py-3 font-medium text-gray-600">
                          Date
                        </th>
                        <th className="text-left px-4 py-3 font-medium text-gray-600">
                          BP (mmHg)
                        </th>
                        <th className="text-left px-4 py-3 font-medium text-gray-600">
                          HR (bpm)
                        </th>
                        <th className="text-left px-4 py-3 font-medium text-gray-600">
                          Temp (°C)
                        </th>
                        <th className="text-left px-4 py-3 font-medium text-gray-600">
                          RR (breaths/min)
                        </th>
                        <th className="text-left px-4 py-3 font-medium text-gray-600">
                          SpO2 (%)
                        </th>
                        <th className="text-left px-4 py-3 font-medium text-gray-600">
                          Weight (kg)
                        </th>
                      </tr>
                    </thead>
                    <tbody>
                      {vitals.map((v) => {
                        const a = v.attributes;
                        const bp =
                          a.systolic != null && a.diastolic != null
                            ? `${a.systolic}/${a.diastolic}`
                            : "—";
                        return (
                          <tr
                            key={v.id}
                            className="border-b border-gray-100 hover:bg-gray-50 transition-colors"
                          >
                            <td className="px-4 py-3 text-gray-900">
                              {new Date(a.recordedAt).toLocaleString()}
                            </td>
                            <td className="px-4 py-3 text-gray-700">{bp}</td>
                            <td className="px-4 py-3 text-gray-700">
                              {fmt(a.heartRate)}
                            </td>
                            <td className="px-4 py-3 text-gray-700">
                              {fmt(a.temperature)}
                            </td>
                            <td className="px-4 py-3 text-gray-700">
                              {fmt(a.respiratoryRate)}
                            </td>
                            <td className="px-4 py-3 text-gray-700">
                              {fmt(a.oxygenSaturation)}
                            </td>
                            <td className="px-4 py-3 text-gray-700">
                              {fmt(a.weight)}
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

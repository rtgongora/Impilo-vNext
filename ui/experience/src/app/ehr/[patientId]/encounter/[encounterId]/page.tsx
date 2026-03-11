"use client";

/**
 * Encounter — Active encounter page with vitals, notes, and close button.
 * Route: /ehr/[patientId]/encounter/[encounterId] | pageTitle: "Encounter"
 */

import { useState } from "react";
import { useParams, useRouter } from "next/navigation";
import Link from "next/link";
import {
  ArrowLeft,
  Loader2,
  Clock,
  Activity,
  FileText,
  XCircle,
  CheckCircle2,
  AlertCircle,
  User,
} from "lucide-react";
import { EHRLayout } from "@/components/EHRLayout";
import { PageShell } from "@/components/PageShell";
import { useEncounter, useCloseEncounter } from "@/hooks/queries/useEncounters";
import { usePatient } from "@/hooks/queries/usePatients";

export default function EncounterPage() {
  const params = useParams<{ patientId: string; encounterId: string }>();
  const router = useRouter();
  const { patientId, encounterId } = params;

  const { data: encounterData, isLoading: isLoadingEncounter } = useEncounter(encounterId);
  const { data: patientData } = usePatient(patientId);
  const closeEncounter = useCloseEncounter();

  const encounter = encounterData?.data;
  const patient = patientData?.data;

  // Local vitals form state
  const [systolic, setSystolic] = useState("");
  const [diastolic, setDiastolic] = useState("");
  const [heartRate, setHeartRate] = useState("");
  const [temperature, setTemperature] = useState("");
  const [respiratoryRate, setRespiratoryRate] = useState("");
  const [oxygenSat, setOxygenSat] = useState("");

  // Notes state
  const [notes, setNotes] = useState("");

  const [showCloseConfirm, setShowCloseConfirm] = useState(false);

  const isActive =
    encounter?.attributes.status === "ACTIVE" ||
    encounter?.attributes.status === "IN_PROGRESS";

  function handleClose() {
    closeEncounter.mutate(
      { id: encounterId },
      {
        onSuccess: () => {
          router.push(`/ehr/${patientId}`);
        },
      },
    );
  }

  return (
    <EHRLayout>
      <PageShell title="Encounter">
        <div className="mb-4">
          <Link
            href={`/ehr/${patientId}`}
            className="inline-flex items-center gap-1 text-sm text-gray-500 hover:text-gray-700 transition-colors"
          >
            <ArrowLeft className="w-4 h-4" />
            Back to patient chart
          </Link>
        </div>

        {isLoadingEncounter ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="w-6 h-6 animate-spin text-gray-400" />
            <span className="ml-2 text-sm text-gray-500">Loading encounter...</span>
          </div>
        ) : !encounter ? (
          <div className="bg-white rounded-lg border border-gray-200 p-12 text-center">
            <AlertCircle className="w-10 h-10 text-gray-300 mx-auto mb-3" />
            <p className="text-gray-400 text-sm">Encounter not found</p>
          </div>
        ) : (
          <div className="space-y-6">
            {/* Encounter Header */}
            <div className="bg-white rounded-lg border border-gray-200 p-5">
              <div className="flex items-start justify-between">
                <div>
                  <div className="flex items-center gap-3">
                    <h2 className="text-lg font-semibold text-gray-900">
                      {encounter.attributes.encounterType} Encounter
                    </h2>
                    <span
                      className={`px-2.5 py-0.5 text-xs font-medium rounded-full ${
                        isActive
                          ? "bg-green-100 text-green-700"
                          : "bg-gray-100 text-gray-600"
                      }`}
                    >
                      {encounter.attributes.status}
                    </span>
                  </div>
                  {patient && (
                    <div className="flex items-center gap-2 mt-2 text-sm text-gray-500">
                      <User className="w-4 h-4" />
                      <span>{patient.attributes.displayName}</span>
                      <span className="text-gray-300">|</span>
                      <span>CPID: {patient.attributes.cpid}</span>
                    </div>
                  )}
                  <div className="flex items-center gap-2 mt-1 text-sm text-gray-500">
                    <Clock className="w-4 h-4" />
                    <span>
                      Started: {new Date(encounter.attributes.startedAt).toLocaleString()}
                    </span>
                    {encounter.attributes.closedAt && (
                      <>
                        <span className="text-gray-300">|</span>
                        <span>
                          Closed: {new Date(encounter.attributes.closedAt).toLocaleString()}
                        </span>
                      </>
                    )}
                  </div>
                </div>
              </div>
            </div>

            <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
              {/* Vitals Entry */}
              <div className="bg-white rounded-lg border border-gray-200 p-5">
                <div className="flex items-center gap-2 mb-4">
                  <Activity className="w-5 h-5 text-red-500" />
                  <h3 className="font-medium text-gray-900">Vitals</h3>
                </div>
                <div className="grid grid-cols-2 gap-3">
                  <div>
                    <label className="block text-xs font-medium text-gray-600 mb-1">
                      Systolic (mmHg)
                    </label>
                    <input
                      type="number"
                      value={systolic}
                      onChange={(e) => setSystolic(e.target.value)}
                      placeholder="120"
                      disabled={!isActive}
                      className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:bg-gray-50 disabled:text-gray-400"
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
                      placeholder="80"
                      disabled={!isActive}
                      className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:bg-gray-50 disabled:text-gray-400"
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
                      placeholder="72"
                      disabled={!isActive}
                      className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:bg-gray-50 disabled:text-gray-400"
                    />
                  </div>
                  <div>
                    <label className="block text-xs font-medium text-gray-600 mb-1">
                      Temperature (C)
                    </label>
                    <input
                      type="number"
                      step="0.1"
                      value={temperature}
                      onChange={(e) => setTemperature(e.target.value)}
                      placeholder="36.5"
                      disabled={!isActive}
                      className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:bg-gray-50 disabled:text-gray-400"
                    />
                  </div>
                  <div>
                    <label className="block text-xs font-medium text-gray-600 mb-1">
                      Resp. Rate (/min)
                    </label>
                    <input
                      type="number"
                      value={respiratoryRate}
                      onChange={(e) => setRespiratoryRate(e.target.value)}
                      placeholder="16"
                      disabled={!isActive}
                      className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:bg-gray-50 disabled:text-gray-400"
                    />
                  </div>
                  <div>
                    <label className="block text-xs font-medium text-gray-600 mb-1">
                      SpO2 (%)
                    </label>
                    <input
                      type="number"
                      value={oxygenSat}
                      onChange={(e) => setOxygenSat(e.target.value)}
                      placeholder="98"
                      disabled={!isActive}
                      className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:bg-gray-50 disabled:text-gray-400"
                    />
                  </div>
                </div>
              </div>

              {/* Clinical Notes */}
              <div className="bg-white rounded-lg border border-gray-200 p-5">
                <div className="flex items-center gap-2 mb-4">
                  <FileText className="w-5 h-5 text-indigo-500" />
                  <h3 className="font-medium text-gray-900">Clinical Notes</h3>
                </div>
                <textarea
                  value={notes}
                  onChange={(e) => setNotes(e.target.value)}
                  disabled={!isActive}
                  placeholder="Enter clinical notes for this encounter..."
                  rows={8}
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 resize-none disabled:bg-gray-50 disabled:text-gray-400"
                />
              </div>
            </div>

            {/* Close Encounter */}
            {isActive && (
              <div className="bg-white rounded-lg border border-gray-200 p-5">
                {!showCloseConfirm ? (
                  <button
                    onClick={() => setShowCloseConfirm(true)}
                    className="w-full py-2.5 bg-red-600 text-white text-sm font-medium rounded-lg hover:bg-red-700 transition-colors flex items-center justify-center gap-2"
                  >
                    <XCircle className="w-4 h-4" />
                    Close Encounter
                  </button>
                ) : (
                  <div className="space-y-3">
                    <p className="text-sm text-gray-700 text-center">
                      Are you sure you want to close this encounter? This action cannot be
                      undone.
                    </p>
                    <div className="flex gap-3">
                      <button
                        onClick={() => setShowCloseConfirm(false)}
                        className="flex-1 py-2.5 bg-gray-100 text-gray-700 text-sm font-medium rounded-lg hover:bg-gray-200 transition-colors"
                      >
                        Cancel
                      </button>
                      <button
                        onClick={handleClose}
                        disabled={closeEncounter.isPending}
                        className="flex-1 py-2.5 bg-red-600 text-white text-sm font-medium rounded-lg hover:bg-red-700 disabled:opacity-50 disabled:cursor-not-allowed flex items-center justify-center gap-2 transition-colors"
                      >
                        {closeEncounter.isPending ? (
                          <>
                            <Loader2 className="w-4 h-4 animate-spin" />
                            Closing...
                          </>
                        ) : (
                          <>
                            <CheckCircle2 className="w-4 h-4" />
                            Confirm Close
                          </>
                        )}
                      </button>
                    </div>
                    {closeEncounter.isError && (
                      <p className="text-sm text-red-600 text-center">
                        Failed to close encounter. Please try again.
                      </p>
                    )}
                  </div>
                )}
              </div>
            )}
          </div>
        )}
      </PageShell>
    </EHRLayout>
  );
}

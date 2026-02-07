"use client";

import { useState } from "react";
import {
  pctApi,
  type Journey,
  type QueueItem,
  type Queue,
} from "@/lib/pctApi";
import { useSessionStore } from "@/stores/sessionStore";

const ACUITY_LEVELS = [
  { value: 1, label: "1 — Red / Immediate", color: "bg-red-600", badge: "badge-triage-1" },
  { value: 2, label: "2 — Orange / Urgent", color: "bg-orange-500", badge: "badge-triage-2" },
  { value: 3, label: "3 — Yellow / Semi-urgent", color: "bg-yellow-500", badge: "badge-triage-3" },
  { value: 4, label: "4 — Green / Non-urgent", color: "bg-green-500", badge: "badge-triage-4" },
  { value: 5, label: "5 — Blue / Admin", color: "bg-blue-500", badge: "badge-triage-5" },
] as const;

const ACUITY_LABELS: Record<number, string> = {
  1: "Red / Immediate",
  2: "Orange / Urgent",
  3: "Yellow / Semi-urgent",
  4: "Green / Non-urgent",
  5: "Blue / Admin",
};

export default function SortingPage() {
  const { facilityId } = useSessionStore();

  // Search state
  const [searchQuery, setSearchQuery] = useState("");
  const [searching, setSearching] = useState(false);

  // Journey state
  const [activeJourney, setActiveJourney] = useState<Journey | null>(null);

  // Triage form
  const [acuity, setAcuity] = useState<number>(4);
  const [systolicBp, setSystolicBp] = useState("");
  const [diastolicBp, setDiastolicBp] = useState("");
  const [heartRate, setHeartRate] = useState("");
  const [temperature, setTemperature] = useState("");
  const [respiratoryRate, setRespiratoryRate] = useState("");
  const [oxygenSaturation, setOxygenSaturation] = useState("");
  const [triageNotes, setTriageNotes] = useState("");

  // Route to queue
  const [queues, setQueues] = useState<Queue[]>([]);
  const [selectedQueueId, setSelectedQueueId] = useState("");
  const [routePriority, setRoutePriority] = useState<"NORMAL" | "HIGH" | "URGENT">("NORMAL");

  // UI state
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);
  const [step, setStep] = useState<"search" | "triage" | "route">("search");

  const handleStartJourney = async () => {
    if (!searchQuery.trim()) {
      setError("Enter a Patient CPID or name to search.");
      return;
    }

    setLoading(true);
    setError(null);
    setSuccessMessage(null);

    try {
      const journey = await pctApi.startJourney(searchQuery.trim(), facilityId);
      setActiveJourney(journey);
      setStep("triage");

      // Preload queues for routing step
      const queueList = await pctApi.getQueues(facilityId);
      setQueues(queueList);
      if (queueList.length > 0) {
        setSelectedQueueId(queueList[0].id);
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to start journey");
    } finally {
      setLoading(false);
    }
  };

  const handleRecordTriage = async () => {
    if (!activeJourney) return;

    setLoading(true);
    setError(null);

    const vitals: Record<string, number> = {};
    if (systolicBp) vitals.systolicBp = parseFloat(systolicBp);
    if (diastolicBp) vitals.diastolicBp = parseFloat(diastolicBp);
    if (heartRate) vitals.heartRate = parseFloat(heartRate);
    if (temperature) vitals.temperature = parseFloat(temperature);
    if (respiratoryRate) vitals.respiratoryRate = parseFloat(respiratoryRate);
    if (oxygenSaturation) vitals.oxygenSaturation = parseFloat(oxygenSaturation);

    try {
      const updated = await pctApi.recordTriage(
        activeJourney.id,
        acuity,
        Object.keys(vitals).length > 0 ? vitals : undefined,
        triageNotes || undefined,
      );
      setActiveJourney(updated);
      setStep("route");
      setSuccessMessage("Triage recorded successfully.");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to record triage");
    } finally {
      setLoading(false);
    }
  };

  const handleRouteToQueue = async () => {
    if (!activeJourney || !selectedQueueId) return;

    setLoading(true);
    setError(null);

    try {
      await pctApi.routePatient(activeJourney.id, selectedQueueId, routePriority);
      setSuccessMessage(
        `Patient routed to queue. Token: ${activeJourney.tokenNumber || "assigned"}`,
      );
      // Reset for next patient
      resetForm();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to route patient");
    } finally {
      setLoading(false);
    }
  };

  const resetForm = () => {
    setActiveJourney(null);
    setSearchQuery("");
    setAcuity(4);
    setSystolicBp("");
    setDiastolicBp("");
    setHeartRate("");
    setTemperature("");
    setRespiratoryRate("");
    setOxygenSaturation("");
    setTriageNotes("");
    setSelectedQueueId(queues.length > 0 ? queues[0].id : "");
    setRoutePriority("NORMAL");
    setStep("search");
  };

  return (
    <div className="p-8 max-w-3xl">
      <h1 className="text-2xl font-semibold text-neutral-900">Patient Sorting</h1>
      <p className="text-sm text-neutral-500 mt-1 mb-6">
        Search for patients, perform triage assessment, and route to service queues.
      </p>

      {error && (
        <div className="mb-4 p-3 rounded-lg bg-red-50 border border-red-200 text-sm text-red-800">
          {error}
        </div>
      )}

      {successMessage && (
        <div className="mb-4 p-3 rounded-lg bg-emerald-50 border border-emerald-200 text-sm text-emerald-800">
          {successMessage}
        </div>
      )}

      {/* Progress indicator */}
      <div className="flex items-center gap-2 mb-6">
        {["search", "triage", "route"].map((s, idx) => (
          <div key={s} className="flex items-center gap-2">
            <div
              className={`w-8 h-8 rounded-full flex items-center justify-center text-sm font-medium ${
                step === s
                  ? "bg-brand-primary text-white"
                  : idx < ["search", "triage", "route"].indexOf(step)
                    ? "bg-emerald-100 text-emerald-800"
                    : "bg-neutral-100 text-neutral-400"
              }`}
            >
              {idx + 1}
            </div>
            <span
              className={`text-sm ${
                step === s ? "font-medium text-neutral-900" : "text-neutral-400"
              }`}
            >
              {s === "search" ? "Find Patient" : s === "triage" ? "Triage" : "Route"}
            </span>
            {idx < 2 && (
              <div className="w-8 h-px bg-neutral-200 mx-1" />
            )}
          </div>
        ))}
      </div>

      {/* Journey state display */}
      {activeJourney && (
        <div className="card p-4 mb-6">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-sm font-medium text-neutral-900">
                Journey: {activeJourney.id.slice(0, 8)}...
              </p>
              <p className="text-xs text-neutral-500">
                Patient CPID: {activeJourney.patientCpid}
              </p>
            </div>
            <div className="flex items-center gap-2">
              <span className="badge bg-neutral-100 text-neutral-700">
                {activeJourney.state}
              </span>
              {activeJourney.acuity && (
                <span className={ACUITY_LEVELS[activeJourney.acuity - 1]?.badge}>
                  Acuity {activeJourney.acuity}
                </span>
              )}
              {activeJourney.tokenNumber && (
                <span className="badge bg-brand-primary/10 text-brand-primary font-mono">
                  #{activeJourney.tokenNumber}
                </span>
              )}
            </div>
          </div>
        </div>
      )}

      {/* Step 1: Search */}
      {step === "search" && (
        <div className="card p-6 space-y-4">
          <h2 className="text-lg font-semibold text-neutral-900">
            Find Patient
          </h2>
          <div>
            <label
              htmlFor="patientSearch"
              className="block text-sm font-medium text-neutral-700 mb-1"
            >
              Patient CPID or Name
            </label>
            <input
              id="patientSearch"
              type="text"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              onKeyDown={(e) => e.key === "Enter" && handleStartJourney()}
              placeholder="e.g., CPID-00000001 or patient name"
              className="input-field"
            />
          </div>
          <button
            onClick={handleStartJourney}
            disabled={loading || !searchQuery.trim()}
            className="btn-primary"
          >
            {loading ? "Starting journey..." : "Start Journey"}
          </button>
        </div>
      )}

      {/* Step 2: Triage */}
      {step === "triage" && (
        <div className="card p-6 space-y-5">
          <h2 className="text-lg font-semibold text-neutral-900">
            Triage Assessment
          </h2>

          {/* Acuity selection */}
          <div>
            <label className="block text-sm font-medium text-neutral-700 mb-2">
              Acuity Level
            </label>
            <div className="space-y-2">
              {ACUITY_LEVELS.map((level) => (
                <label
                  key={level.value}
                  className={`flex items-center gap-3 p-3 rounded-lg border cursor-pointer transition-colors ${
                    acuity === level.value
                      ? "border-brand-primary bg-brand-primary/5"
                      : "border-neutral-200 hover:bg-neutral-50"
                  }`}
                >
                  <input
                    type="radio"
                    name="acuity"
                    value={level.value}
                    checked={acuity === level.value}
                    onChange={() => setAcuity(level.value)}
                    className="sr-only"
                  />
                  <span className={`w-4 h-4 rounded-full ${level.color}`} />
                  <span className="text-sm font-medium text-neutral-900">
                    {level.label}
                  </span>
                </label>
              ))}
            </div>
          </div>

          {/* Vitals */}
          <div>
            <label className="block text-sm font-medium text-neutral-700 mb-2">
              Vitals (optional)
            </label>
            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="text-xs text-neutral-500">
                  Systolic BP (mmHg)
                </label>
                <input
                  type="number"
                  value={systolicBp}
                  onChange={(e) => setSystolicBp(e.target.value)}
                  placeholder="120"
                  className="input-field mt-0.5"
                />
              </div>
              <div>
                <label className="text-xs text-neutral-500">
                  Diastolic BP (mmHg)
                </label>
                <input
                  type="number"
                  value={diastolicBp}
                  onChange={(e) => setDiastolicBp(e.target.value)}
                  placeholder="80"
                  className="input-field mt-0.5"
                />
              </div>
              <div>
                <label className="text-xs text-neutral-500">
                  Heart Rate (bpm)
                </label>
                <input
                  type="number"
                  value={heartRate}
                  onChange={(e) => setHeartRate(e.target.value)}
                  placeholder="72"
                  className="input-field mt-0.5"
                />
              </div>
              <div>
                <label className="text-xs text-neutral-500">
                  Temperature (C)
                </label>
                <input
                  type="number"
                  step="0.1"
                  value={temperature}
                  onChange={(e) => setTemperature(e.target.value)}
                  placeholder="36.5"
                  className="input-field mt-0.5"
                />
              </div>
              <div>
                <label className="text-xs text-neutral-500">
                  Respiratory Rate (/min)
                </label>
                <input
                  type="number"
                  value={respiratoryRate}
                  onChange={(e) => setRespiratoryRate(e.target.value)}
                  placeholder="16"
                  className="input-field mt-0.5"
                />
              </div>
              <div>
                <label className="text-xs text-neutral-500">
                  SpO2 (%)
                </label>
                <input
                  type="number"
                  value={oxygenSaturation}
                  onChange={(e) => setOxygenSaturation(e.target.value)}
                  placeholder="98"
                  className="input-field mt-0.5"
                />
              </div>
            </div>
          </div>

          {/* Notes */}
          <div>
            <label
              htmlFor="triageNotes"
              className="block text-sm font-medium text-neutral-700 mb-1"
            >
              Triage Notes
            </label>
            <textarea
              id="triageNotes"
              rows={3}
              value={triageNotes}
              onChange={(e) => setTriageNotes(e.target.value)}
              placeholder="Chief complaint, presenting symptoms..."
              className="input-field"
            />
          </div>

          <div className="flex gap-3">
            <button
              onClick={() => {
                setStep("search");
                setActiveJourney(null);
              }}
              className="btn-secondary"
            >
              Back
            </button>
            <button
              onClick={handleRecordTriage}
              disabled={loading}
              className="btn-primary flex-1"
            >
              {loading ? "Recording triage..." : "Record Triage"}
            </button>
          </div>
        </div>
      )}

      {/* Step 3: Route to Queue */}
      {step === "route" && (
        <div className="card p-6 space-y-5">
          <h2 className="text-lg font-semibold text-neutral-900">
            Route to Queue
          </h2>

          {activeJourney?.acuity && (
            <div className="p-3 rounded-lg bg-neutral-50 border border-neutral-200">
              <p className="text-sm">
                <span className="font-medium">Assessed acuity:</span>{" "}
                <span className={ACUITY_LEVELS[activeJourney.acuity - 1]?.badge}>
                  {activeJourney.acuity} &mdash; {ACUITY_LABELS[activeJourney.acuity]}
                </span>
              </p>
            </div>
          )}

          <div>
            <label
              htmlFor="targetQueue"
              className="block text-sm font-medium text-neutral-700 mb-1"
            >
              Target Queue
            </label>
            {queues.length > 0 ? (
              <select
                id="targetQueue"
                value={selectedQueueId}
                onChange={(e) => setSelectedQueueId(e.target.value)}
                className="select-field"
              >
                {queues.map((q) => (
                  <option key={q.id} value={q.id}>
                    {q.name} (Waiting: {q.waitingCount})
                  </option>
                ))}
              </select>
            ) : (
              <p className="text-sm text-neutral-500">
                No queues available for this facility.
              </p>
            )}
          </div>

          <div>
            <label
              htmlFor="routePriority"
              className="block text-sm font-medium text-neutral-700 mb-1"
            >
              Priority
            </label>
            <select
              id="routePriority"
              value={routePriority}
              onChange={(e) =>
                setRoutePriority(e.target.value as "NORMAL" | "HIGH" | "URGENT")
              }
              className="select-field"
            >
              <option value="NORMAL">Normal</option>
              <option value="HIGH">High</option>
              <option value="URGENT">Urgent</option>
            </select>
          </div>

          <div className="flex gap-3">
            <button
              onClick={() => setStep("triage")}
              className="btn-secondary"
            >
              Back
            </button>
            <button
              onClick={handleRouteToQueue}
              disabled={loading || !selectedQueueId}
              className="btn-primary flex-1"
            >
              {loading ? "Routing patient..." : "Route to Queue"}
            </button>
          </div>
        </div>
      )}
    </div>
  );
}

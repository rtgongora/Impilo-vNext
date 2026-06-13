"use client";

import { useState } from "react";
import {
  AlertTriangle, Plus, Eye, Loader2,
} from "lucide-react";
import {
  useIngestPublicHealthEvent,
  useCreatePublicHealthSignal,
  usePublicHealthCases,
  usePublicHealthCounters,
  usePublicHealthOutbreaks,
  usePublicHealthSignals,
  usePublicHealthWeeklyIdsr,
} from "@/hooks/queries/usePublicHealth";
import {
  useInvestigations,
  useOpenInvestigation,
  useUpdateInvestigationStatus,
} from "@/hooks/queries/useSurveillance";

interface NewSignalEventForm {
  disease: string;
  facility: string;
  cases: string;
  detectedOn: string;
  location: string;
  source: string;
  notes: string;
  priority: "LOW" | "MEDIUM" | "HIGH" | "CRITICAL";
}

const EMPTY_SIGNAL_EVENT: NewSignalEventForm = {
  disease: "",
  facility: "",
  cases: "1",
  detectedOn: new Date().toISOString().slice(0, 10),
  location: "",
  source: "",
  notes: "",
  priority: "MEDIUM",
};

interface NewCaseReportForm {
  disease: string;
  patientRef: string;
  facilityId: string;
  dateOnset: string;
  dateNotification: string;
  classification: string;
  outcome: string;
  summary: string;
}

const EMPTY_CASE_REPORT: NewCaseReportForm = {
  disease: "",
  patientRef: "",
  facilityId: "",
  dateOnset: "",
  dateNotification: new Date().toISOString().slice(0, 10),
  classification: "suspected",
  outcome: "admitted",
  summary: "",
};

function normalizeEventType(disease: string): string {
  if (!disease) return "SURVEILLANCE_EVENT";
  return `SURVEILLANCE_${disease.trim().toUpperCase().replace(/[^A-Z0-9]+/g, "_")}`;
}

function isUuid(value: string): boolean {
  return /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(value);
}

const RESPONSE_ACTIONS = [
  "Deploy field investigation team",
  "Initiate contact tracing",
  "Collect laboratory specimens",
  "Issue public health alert",
  "Activate EOC",
  "Request WHO support",
  "Begin case management protocol",
] as const;

interface SignalResponseWorkflowForm {
  verificationStatus: string;
  verifiedBy: string;
  verificationDate: string;
  riskLevel: string;
  spreadPotential: string;
  populationAtRisk: string;
  responseActions: Record<string, boolean>;
  assignToTeam: string;
  escalationLevel: string;
  notes: string;
  linkedOutbreakId: string;
}

const EMPTY_SIGNAL_WORKFLOW: SignalResponseWorkflowForm = {
  verificationStatus: "",
  verifiedBy: "",
  verificationDate: new Date().toISOString().slice(0, 10),
  riskLevel: "",
  spreadPotential: "",
  populationAtRisk: "",
  responseActions: Object.fromEntries(RESPONSE_ACTIONS.map((a) => [a, false])),
  assignToTeam: "",
  escalationLevel: "",
  notes: "",
  linkedOutbreakId: "",
};

function buildInvestigationTitle(
  disease: string,
  facility: string,
  workflow: SignalResponseWorkflowForm,
  draft: boolean,
): string {
  const prefix = draft ? "[Draft] " : "";
  const risk = workflow.riskLevel ? ` — ${workflow.riskLevel} risk` : "";
  return `${prefix}Signal response: ${disease} @ ${facility}${risk}`;
}

function extractInvestigationId(payload: unknown): string | null {
  if (!payload || typeof payload !== "object") return null;
  const root = payload as Record<string, unknown>;
  const data = root.data && typeof root.data === "object" ? (root.data as Record<string, unknown>) : root;
  const id = data.id ?? data.investigationId ?? data.investigation_id;
  return id != null ? String(id) : null;
}

function buildInvestigationFindings(workflow: SignalResponseWorkflowForm): string {
  const selectedActions = RESPONSE_ACTIONS.filter((a) => workflow.responseActions[a]);
  return JSON.stringify({
    verificationStatus: workflow.verificationStatus,
    verifiedBy: workflow.verifiedBy,
    verificationDate: workflow.verificationDate,
    riskLevel: workflow.riskLevel,
    spreadPotential: workflow.spreadPotential,
    populationAtRisk: workflow.populationAtRisk,
    responseActions: selectedActions,
    assignToTeam: workflow.assignToTeam,
    escalationLevel: workflow.escalationLevel,
    notes: workflow.notes,
  });
}

export function SurveillanceTab() {
  const { data: apiSignals = [], isLoading: sigLoading, isError: sigError } = usePublicHealthSignals();
  const { data: apiCases = [], isLoading: caseLoading, isError: caseError } = usePublicHealthCases();
  const { data: counters = [], isLoading: ctrLoading, isError: ctrError } = usePublicHealthCounters();
  const { data: weeklyRows = [], isLoading: weeklyLoading, isError: weeklyError } = usePublicHealthWeeklyIdsr();
  const createSignal = useCreatePublicHealthSignal();
  const ingestEvent = useIngestPublicHealthEvent();
  const { data: outbreaks = [] } = usePublicHealthOutbreaks();
  const { data: investigations = [] } = useInvestigations();
  const openInvestigation = useOpenInvestigation();
  const updateInvestigationStatus = useUpdateInvestigationStatus();

  const [filter, setFilter] = useState("all");
  const [showNewEvent, setShowNewEvent] = useState(false);
  const [showNewCase, setShowNewCase] = useState(false);
  const [selectedSignal, setSelectedSignal] = useState<string | null>(null);
  const [selectedCase, setSelectedCase] = useState<string | null>(null);
  const [activeSubTab, setActiveSubTab] = useState<"signals" | "cases" | "weekly">("signals");
  const [signalForm, setSignalForm] = useState<NewSignalEventForm>(EMPTY_SIGNAL_EVENT);
  const [signalFormError, setSignalFormError] = useState<string | null>(null);
  const [signalFormSuccess, setSignalFormSuccess] = useState<string | null>(null);
  const [caseForm, setCaseForm] = useState<NewCaseReportForm>(EMPTY_CASE_REPORT);
  const [caseFormError, setCaseFormError] = useState<string | null>(null);
  const [caseFormSuccess, setCaseFormSuccess] = useState<string | null>(null);
  const [signalWorkflow, setSignalWorkflow] = useState<SignalResponseWorkflowForm>(EMPTY_SIGNAL_WORKFLOW);
  const [signalWorkflowError, setSignalWorkflowError] = useState<string | null>(null);
  const [signalWorkflowSuccess, setSignalWorkflowSuccess] = useState<string | null>(null);
  const [showOutbreakLink, setShowOutbreakLink] = useState(false);

  function resetSignalWorkflow() {
    setSignalWorkflow(EMPTY_SIGNAL_WORKFLOW);
    setSignalWorkflowError(null);
    setSignalWorkflowSuccess(null);
    setShowOutbreakLink(false);
  }

  function updateSignalWorkflow<K extends keyof SignalResponseWorkflowForm>(
    field: K,
    value: SignalResponseWorkflowForm[K],
  ) {
    setSignalWorkflow((prev) => ({ ...prev, [field]: value }));
  }

  function toggleResponseAction(action: string, checked: boolean) {
    setSignalWorkflow((prev) => ({
      ...prev,
      responseActions: { ...prev.responseActions, [action]: checked },
    }));
  }

  async function submitSignalInvestigation(sig: { id: string; disease: string; facility: string }, initiate: boolean) {
    if (!signalWorkflow.verificationStatus) {
      setSignalWorkflowError("Select a verification status before saving the investigation.");
      return;
    }
    if (initiate && !signalWorkflow.riskLevel) {
      setSignalWorkflowError("Select a risk level before initiating a field response.");
      return;
    }
    setSignalWorkflowError(null);
    setSignalWorkflowSuccess(null);
    try {
      const body: Record<string, unknown> = {
        title: buildInvestigationTitle(sig.disease, sig.facility, signalWorkflow, !initiate),
        assignedTo: signalWorkflow.assignToTeam || signalWorkflow.verifiedBy || undefined,
      };
      if (signalWorkflow.linkedOutbreakId) {
        const outbreakNum = Number(signalWorkflow.linkedOutbreakId);
        if (Number.isFinite(outbreakNum)) body.outbreakId = outbreakNum;
      }
      const created = await openInvestigation.mutateAsync(body);
      const createdId = extractInvestigationId(created);
      if (initiate && createdId) {
        await updateInvestigationStatus.mutateAsync({
          investigationId: createdId,
          status: "IN_PROGRESS",
          findings: buildInvestigationFindings(signalWorkflow),
        });
      }
      setSignalWorkflowSuccess(
        initiate
          ? `Investigation opened and response initiated for signal ${sig.id}.`
          : `Investigation draft saved for signal ${sig.id}.`,
      );
      if (initiate) resetSignalWorkflow();
    } catch (err) {
      setSignalWorkflowError(err instanceof Error ? err.message : "Failed to open investigation.");
    }
  }

  const filteredSignals = apiSignals.filter((s) => {
    if (filter === "all") return true;
    return s.status.toUpperCase() === filter.toUpperCase();
  });

  function updateSignalForm(field: keyof NewSignalEventForm, value: string) {
    setSignalForm((prev) => ({ ...prev, [field]: value }));
  }

  async function submitSignalEvent() {
    if (!signalForm.disease.trim()) {
      setSignalFormError("Disease/condition is required.");
      return;
    }
    const threshold = Number(signalForm.cases);
    if (Number.isNaN(threshold) || threshold < 1) {
      setSignalFormError("Number of cases must be 1 or higher.");
      return;
    }
    setSignalFormError(null);
    setSignalFormSuccess(null);
    try {
      await createSignal.mutateAsync({
        name: `${signalForm.disease} signal`,
        description: [
          signalForm.notes.trim(),
          signalForm.facility.trim() ? `Facility: ${signalForm.facility.trim()}` : "",
          signalForm.location.trim() ? `Location: ${signalForm.location.trim()}` : "",
          signalForm.source.trim() ? `Source: ${signalForm.source.trim()}` : "",
          signalForm.detectedOn ? `Detected: ${signalForm.detectedOn}` : "",
        ]
          .filter(Boolean)
          .join(" | "),
        eventType: normalizeEventType(signalForm.disease),
        conditionField: "syndrome_code",
        threshold,
        windowHours: 24,
        severity: signalForm.priority,
      });
      setSignalFormSuccess("Surveillance event submitted successfully.");
      setSignalForm(EMPTY_SIGNAL_EVENT);
      setShowNewEvent(false);
    } catch {
      setSignalFormError("Unable to submit event right now. Please verify surveillance connectivity.");
    }
  }

  function updateCaseForm(field: keyof NewCaseReportForm, value: string) {
    setCaseForm((prev) => ({ ...prev, [field]: value }));
  }

  async function submitCaseReport() {
    if (!caseForm.disease.trim()) {
      setCaseFormError("Disease is required.");
      return;
    }
    setCaseFormError(null);
    setCaseFormSuccess(null);
    const payload = JSON.stringify({
      disease: caseForm.disease,
      patientRef: caseForm.patientRef,
      dateOnset: caseForm.dateOnset,
      dateNotification: caseForm.dateNotification,
      classification: caseForm.classification,
      outcome: caseForm.outcome,
      summary: caseForm.summary,
    });
    try {
      await ingestEvent.mutateAsync({
        eventType: `CASE_REPORT_${normalizeEventType(caseForm.disease)}`,
        payload,
        facilityId: isUuid(caseForm.facilityId) ? caseForm.facilityId : null,
      });
      setCaseFormSuccess("Case-based report submitted successfully.");
      setCaseForm(EMPTY_CASE_REPORT);
      setShowNewCase(false);
    } catch {
      setCaseFormError("Unable to submit case report. Check surveillance/BFF availability.");
    }
  }

  return (
    <div className="space-y-4">
      <div className="rounded-lg border border-primary/25 bg-primary-soft/80 p-3 text-xs text-impilo-800">
        <strong>Live data:</strong> threshold signals, case-based reports, and counter snapshots load from the Experience BFF
        → surveillance-service (empty if the service is down or unseeded). <strong>Weekly IDSR / eIDSR</strong> uses the
        &quot;Weekly Aggregate&quot; sub-tab from{" "}
        <code className="text-[10px]">GET /internal/v1/public-health/weekly-idsr</code>.
      </div>

      {/* KPI Strip */}
      <div className="grid grid-cols-5 gap-3">
        {[
          {
            label: "Signal definitions (tenant)",
            value: sigLoading ? "…" : String(apiSignals.length),
            color: "text-danger",
            sub: sigError ? "Could not reach surveillance" : "From /public-health/signals",
          },
          {
            label: "Surveillance cases (page)",
            value: caseLoading ? "…" : String(apiCases.length),
            color: "text-sky-700",
            sub: caseError ? "Could not reach surveillance" : "From /public-health/cases",
          },
          {
            label: "Counter snapshots",
            value: ctrLoading ? "…" : String(counters.length),
            color: "text-primary",
            sub: ctrError ? "Could not reach counters" : "From /public-health/counters",
          },
          {
            label: "Weekly IDSR",
            value: weeklyLoading ? "…" : String(weeklyRows.length),
            color: "text-primary-hover",
            sub: weeklyError ? "Could not reach weekly aggregate" : "From /public-health/weekly-idsr",
          },
          { label: "Reporting completeness", value: "Live", color: "text-primary-hover", sub: "Derived from weekly aggregate feed" },
        ].map((kpi, i) => (
          <div key={i} className="bg-card rounded-lg border border-border p-3 text-center">
            <p className={`text-2xl font-bold ${kpi.color}`}>{kpi.value}</p>
            <p className="text-xs font-medium text-foreground">{kpi.label}</p>
            <p className="text-[10px] text-muted-foreground">{kpi.sub}</p>
          </div>
        ))}
      </div>

      {/* Sub-tabs */}
      <div className="flex gap-1 border-b border-border">
        {[
          { key: "signals" as const, label: "Threshold Signals" },
          { key: "cases" as const, label: "Case-Based Reports" },
          { key: "weekly" as const, label: "Weekly Aggregate" },
        ].map((tab) => (
          <button key={tab.key} onClick={() => setActiveSubTab(tab.key)}
            className={`px-3 py-2 text-sm font-medium border-b-2 transition-colors ${
              activeSubTab === tab.key ? "border-amber-600 text-amber-600" : "border-transparent text-muted-foreground hover:text-foreground"
            }`}>
            {tab.label}
          </button>
        ))}
      </div>

      {/* Signals Tab */}
      {activeSubTab === "signals" && (
        <div className="bg-card rounded-lg border border-border">
          <div className="px-4 py-3 border-b flex items-center justify-between">
            <div>
              <h4 className="text-sm font-semibold text-foreground flex items-center gap-2">
                <AlertTriangle className="h-4 w-4" /> Signal Triage Queue
              </h4>
              <p className="text-xs text-muted-foreground">Automated threshold alerts from facility-level reporting</p>
            </div>
            <div className="flex gap-2">
              <select value={filter} onChange={(e) => setFilter(e.target.value)}
                className="h-8 px-2 text-xs border border-border rounded-lg">
                <option value="all">All</option>
                <option value="ACTIVE">Active</option>
                <option value="INACTIVE">Inactive</option>
              </select>
              <button
                type="button"
                onClick={() => {
                  setShowNewEvent((prev) => !prev);
                  setSignalFormError(null);
                  setSignalFormSuccess(null);
                }}
                className="inline-flex items-center gap-1 px-3 py-1.5 bg-primary text-white text-xs font-medium rounded-lg hover:bg-primary-hover"
              >
                <Plus className="h-3.5 w-3.5" /> {showNewEvent ? "Close form" : "Report Event"}
              </button>
            </div>
          </div>

          <div className="p-4 space-y-4">
            {signalFormSuccess && (
              <div className="rounded-lg border border-green-200 bg-green-50 px-3 py-2 text-xs text-green-800">
                {signalFormSuccess}
              </div>
            )}
            {/* New Event Form */}
            {showNewEvent && (
              <div className="p-4 border border-primary/25 bg-primary-soft rounded-lg">
                <h4 className="font-semibold text-sm mb-3">Report New Surveillance Event</h4>
                {signalFormError && (
                  <div className="mb-3 rounded-lg border border-danger/28 bg-danger-soft px-3 py-2 text-xs text-red-800">
                    {signalFormError}
                  </div>
                )}
                <div className="grid grid-cols-3 gap-3">
                  <div>
                    <label className="text-xs font-medium text-muted-foreground">Disease / Condition</label>
                    <select
                      value={signalForm.disease}
                      onChange={(e) => updateSignalForm("disease", e.target.value)}
                      className="w-full h-8 px-2 text-xs border border-border rounded-lg mt-1"
                    >
                      <option value="">Select disease</option>
                      <option value="cholera">Cholera</option>
                      <option value="typhoid">Typhoid</option>
                      <option value="measles">Measles</option>
                      <option value="malaria">Malaria</option>
                      <option value="afp">AFP (Acute Flaccid Paralysis)</option>
                      <option value="awd">Acute Watery Diarrhoea</option>
                      <option value="anthrax">Anthrax</option>
                      <option value="rabies">Rabies</option>
                      <option value="other">Other (specify)</option>
                    </select>
                  </div>
                  <div>
                    <label className="text-xs font-medium text-muted-foreground">Reporting Facility</label>
                    <input
                      value={signalForm.facility}
                      onChange={(e) => updateSignalForm("facility", e.target.value)}
                      placeholder="Search facility..."
                      className="w-full h-8 px-2 text-xs border border-border rounded-lg mt-1"
                    />
                  </div>
                  <div>
                    <label className="text-xs font-medium text-muted-foreground">Number of Cases</label>
                    <input
                      type="number"
                      min={1}
                      value={signalForm.cases}
                      onChange={(e) => updateSignalForm("cases", e.target.value)}
                      className="w-full h-8 px-2 text-xs border border-border rounded-lg mt-1"
                    />
                  </div>
                  <div>
                    <label className="text-xs font-medium text-muted-foreground">Date of Detection</label>
                    <input
                      type="date"
                      value={signalForm.detectedOn}
                      onChange={(e) => updateSignalForm("detectedOn", e.target.value)}
                      className="w-full h-8 px-2 text-xs border border-border rounded-lg mt-1"
                    />
                  </div>
                  <div>
                    <label className="text-xs font-medium text-muted-foreground">Location / Ward</label>
                    <input
                      value={signalForm.location}
                      onChange={(e) => updateSignalForm("location", e.target.value)}
                      placeholder="e.g. Ward 22, Budiriro"
                      className="w-full h-8 px-2 text-xs border border-border rounded-lg mt-1"
                    />
                  </div>
                  <div>
                    <label className="text-xs font-medium text-muted-foreground">Source</label>
                    <select
                      value={signalForm.source}
                      onChange={(e) => updateSignalForm("source", e.target.value)}
                      className="w-full h-8 px-2 text-xs border border-border rounded-lg mt-1"
                    >
                      <option value="">Source</option>
                      <option value="facility">Facility Report</option>
                      <option value="community">Community Alert</option>
                      <option value="lab">Laboratory Notification</option>
                      <option value="citizen">Citizen Report</option>
                      <option value="media">Media Monitoring</option>
                    </select>
                  </div>
                </div>
                <div className="mt-3">
                  <label className="text-xs font-medium text-muted-foreground">Priority</label>
                  <select
                    value={signalForm.priority}
                    onChange={(e) => updateSignalForm("priority", e.target.value)}
                    className="w-full h-8 px-2 text-xs border border-border rounded-lg mt-1"
                  >
                    <option value="LOW">Low</option>
                    <option value="MEDIUM">Moderate</option>
                    <option value="HIGH">High</option>
                    <option value="CRITICAL">Critical</option>
                  </select>
                </div>
                <div className="mt-3">
                  <label className="text-xs font-medium text-muted-foreground">Clinical Details / Notes</label>
                  <textarea
                    value={signalForm.notes}
                    onChange={(e) => updateSignalForm("notes", e.target.value)}
                    placeholder="Describe symptoms, clinical presentation, epidemiological context..."
                    className="w-full text-xs border border-border rounded-lg mt-1 p-2 min-h-[60px]"
                  />
                </div>
                <div className="flex gap-2 mt-3">
                  <button
                    type="button"
                    onClick={submitSignalEvent}
                    disabled={createSignal.isPending}
                    className="px-3 py-1.5 bg-primary text-white text-xs font-medium rounded-lg hover:bg-primary-hover disabled:opacity-60"
                  >
                    {createSignal.isPending ? "Submitting..." : "Submit Event"}
                  </button>
                  <button
                    type="button"
                    onClick={() => setShowNewEvent(false)}
                    className="px-3 py-1.5 bg-neutral-100 text-foreground text-xs font-medium rounded-lg"
                  >
                    Cancel
                  </button>
                </div>
              </div>
            )}

            {/* Signals Table */}
            <div className="overflow-x-auto">
              {sigLoading && (
                <div className="flex items-center justify-center gap-2 py-8 text-sm text-muted-foreground">
                  <Loader2 className="h-4 w-4 animate-spin" /> Loading signals…
                </div>
              )}
              {sigError && (
                <p className="px-3 py-4 text-sm text-danger">Unable to load signals from surveillance-service via BFF.</p>
              )}
              {!sigLoading && !sigError && filteredSignals.length === 0 && (
                <p className="px-3 py-4 text-sm text-muted-foreground">No signal definitions returned for this tenant.</p>
              )}
              {!sigLoading && !sigError && filteredSignals.length > 0 && (
              <table className="w-full text-xs">
                <thead>
                  <tr className="border-b bg-background">
                    <th className="text-left px-3 py-2 font-medium text-muted-foreground">Signal ID</th>
                    <th className="text-left px-3 py-2 font-medium text-muted-foreground">Disease</th>
                    <th className="text-left px-3 py-2 font-medium text-muted-foreground">Facility</th>
                    <th className="text-left px-3 py-2 font-medium text-muted-foreground">Cases</th>
                    <th className="text-left px-3 py-2 font-medium text-muted-foreground">Threshold</th>
                    <th className="text-left px-3 py-2 font-medium text-muted-foreground">Status</th>
                    <th className="text-left px-3 py-2 font-medium text-muted-foreground">Date</th>
                    <th className="text-left px-3 py-2 font-medium text-muted-foreground">Action</th>
                  </tr>
                </thead>
                <tbody>
                  {filteredSignals.map((sig) => (
                    <tr key={sig.id}>
                      <td className={`px-3 py-2 font-mono ${selectedSignal === sig.id ? "bg-primary-soft" : ""}`}>{sig.id}</td>
                      <td className="px-3 py-2 font-medium text-foreground">{sig.disease}</td>
                      <td className="px-3 py-2 text-muted-foreground">{sig.facility}</td>
                      <td className="px-3 py-2 font-bold">{sig.cases || "—"}</td>
                      <td className="px-3 py-2 text-muted-foreground">{sig.threshold}</td>
                      <td className="px-3 py-2">
                        <span className={`px-2 py-0.5 rounded-full text-[10px] font-medium ${
                          sig.status.toUpperCase() === "ACTIVE" ? "bg-green-100 text-green-800" : "bg-neutral-100 text-foreground"
                        }`}>{sig.status}</span>
                      </td>
                      <td className="px-3 py-2 text-muted-foreground">{sig.detectedAt || "—"}</td>
                      <td className="px-3 py-2">
                        <button
                          type="button"
                          onClick={() => {
                            if (selectedSignal === sig.id) {
                              setSelectedSignal(null);
                              resetSignalWorkflow();
                            } else {
                              setSelectedSignal(sig.id);
                              resetSignalWorkflow();
                            }
                          }}
                          className="inline-flex items-center gap-1 px-2 py-1 border border-border rounded text-[10px] font-medium hover:bg-background">
                          {selectedSignal === sig.id ? "Close" : (
                            <>
                              <Eye className="h-3 w-3" /> Review
                            </>
                          )}
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
              )}
            </div>

            {/* Expanded Signal Response Workflow */}
            {selectedSignal && (() => {
              const sig = apiSignals.find((s) => s.id === selectedSignal);
              if (!sig) return null;
              return (
                <div className="p-4 bg-background rounded-lg border border-border space-y-4">
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <h4 className="text-sm font-semibold">Signal Response Workflow — {sig.disease} ({sig.facility})</h4>
                    <span className="text-[10px] text-muted-foreground">
                      {investigations.length} open investigation{investigations.length === 1 ? "" : "s"} via BFF
                    </span>
                  </div>
                  {signalWorkflowError ? (
                    <p className="text-xs text-danger bg-danger-soft border border-danger/28 rounded px-3 py-2">{signalWorkflowError}</p>
                  ) : null}
                  {signalWorkflowSuccess ? (
                    <p className="text-xs text-primary-hover bg-success-soft border border-success/25 rounded px-3 py-2">{signalWorkflowSuccess}</p>
                  ) : null}

                  {/* Step 1: Verification */}
                  <div className="p-3 border border-border rounded-lg bg-card space-y-2">
                    <div className="flex items-center gap-2">
                      <span className="px-2 py-0.5 text-[10px] border border-border rounded">Step 1</span>
                      <span className="text-sm font-medium">Signal Verification</span>
                    </div>
                    <div className="grid grid-cols-3 gap-3">
                      <div>
                        <label className="text-xs font-medium text-muted-foreground">Verification Status</label>
                        <select
                          value={signalWorkflow.verificationStatus}
                          onChange={(e) => updateSignalWorkflow("verificationStatus", e.target.value)}
                          className="w-full h-8 px-2 text-xs border border-border rounded-lg mt-1"
                        >
                          <option value="">Select</option>
                          <option value="confirmed">Confirmed - True Signal</option>
                          <option value="false_alarm">False Alarm - Data Error</option>
                          <option value="duplicate">Duplicate of Existing</option>
                          <option value="needs_more_info">Needs More Information</option>
                        </select>
                      </div>
                      <div>
                        <label className="text-xs font-medium text-muted-foreground">Verified By</label>
                        <input
                          value={signalWorkflow.verifiedBy}
                          onChange={(e) => updateSignalWorkflow("verifiedBy", e.target.value)}
                          placeholder="Officer name"
                          className="w-full h-8 px-2 text-xs border border-border rounded-lg mt-1"
                        />
                      </div>
                      <div>
                        <label className="text-xs font-medium text-muted-foreground">Verification Date</label>
                        <input
                          type="date"
                          value={signalWorkflow.verificationDate}
                          onChange={(e) => updateSignalWorkflow("verificationDate", e.target.value)}
                          className="w-full h-8 px-2 text-xs border border-border rounded-lg mt-1"
                        />
                      </div>
                    </div>
                  </div>

                  {/* Step 2: Risk Assessment */}
                  <div className="p-3 border border-border rounded-lg bg-card space-y-2">
                    <div className="flex items-center gap-2">
                      <span className="px-2 py-0.5 text-[10px] border border-border rounded">Step 2</span>
                      <span className="text-sm font-medium">Risk Assessment</span>
                    </div>
                    <div className="grid grid-cols-3 gap-3">
                      <div>
                        <label className="text-xs font-medium text-muted-foreground">Risk Level</label>
                        <select
                          value={signalWorkflow.riskLevel}
                          onChange={(e) => updateSignalWorkflow("riskLevel", e.target.value)}
                          className="w-full h-8 px-2 text-xs border border-border rounded-lg mt-1"
                        >
                          <option value="">Assess risk</option>
                          <option value="low">Low - Monitor Only</option>
                          <option value="moderate">Moderate - Enhanced Surveillance</option>
                          <option value="high">High - Field Investigation Required</option>
                          <option value="critical">Critical - Immediate Response</option>
                        </select>
                      </div>
                      <div>
                        <label className="text-xs font-medium text-muted-foreground">Spread Potential</label>
                        <select
                          value={signalWorkflow.spreadPotential}
                          onChange={(e) => updateSignalWorkflow("spreadPotential", e.target.value)}
                          className="w-full h-8 px-2 text-xs border border-border rounded-lg mt-1"
                        >
                          <option value="">Select</option>
                          <option value="contained">Contained</option>
                          <option value="local">Local Spread Likely</option>
                          <option value="regional">Regional Spread Risk</option>
                        </select>
                      </div>
                      <div>
                        <label className="text-xs font-medium text-muted-foreground">Population at Risk</label>
                        <input
                          value={signalWorkflow.populationAtRisk}
                          onChange={(e) => updateSignalWorkflow("populationAtRisk", e.target.value)}
                          placeholder="Estimated number"
                          className="w-full h-8 px-2 text-xs border border-border rounded-lg mt-1"
                        />
                      </div>
                    </div>
                  </div>

                  {/* Step 3: Response Actions */}
                  <div className="p-3 border border-border rounded-lg bg-card space-y-2">
                    <div className="flex items-center gap-2">
                      <span className="px-2 py-0.5 text-[10px] border border-border rounded">Step 3</span>
                      <span className="text-sm font-medium">Response Actions</span>
                    </div>
                    <div className="grid grid-cols-2 gap-3">
                      <div>
                        <label className="text-xs font-medium text-muted-foreground">Response Actions (select all applicable)</label>
                        <div className="space-y-1 mt-1">
                          {RESPONSE_ACTIONS.map((action) => (
                            <label key={action} className="flex items-center gap-2 text-xs">
                              <input
                                type="checkbox"
                                className="rounded"
                                checked={Boolean(signalWorkflow.responseActions[action])}
                                onChange={(e) => toggleResponseAction(action, e.target.checked)}
                              />
                              {action}
                            </label>
                          ))}
                        </div>
                      </div>
                      <div className="space-y-3">
                        <div>
                          <label className="text-xs font-medium text-muted-foreground">Assign To Team</label>
                          <input
                            value={signalWorkflow.assignToTeam}
                            onChange={(e) => updateSignalWorkflow("assignToTeam", e.target.value)}
                            placeholder="Team or officer ref"
                            className="w-full h-8 px-2 text-xs border border-border rounded-lg mt-1"
                          />
                        </div>
                        <div>
                          <label className="text-xs font-medium text-muted-foreground">Escalation Level</label>
                          <select
                            value={signalWorkflow.escalationLevel}
                            onChange={(e) => updateSignalWorkflow("escalationLevel", e.target.value)}
                            className="w-full h-8 px-2 text-xs border border-border rounded-lg mt-1"
                          >
                            <option value="">Select</option>
                            <option value="district">District Level</option>
                            <option value="provincial">Provincial Level</option>
                            <option value="national">National Level</option>
                            <option value="international">International (IHR)</option>
                          </select>
                        </div>
                        <div>
                          <label className="text-xs font-medium text-muted-foreground">Notes</label>
                          <textarea
                            value={signalWorkflow.notes}
                            onChange={(e) => updateSignalWorkflow("notes", e.target.value)}
                            placeholder="Additional instructions..."
                            className="w-full text-xs border border-border rounded-lg mt-1 p-2 min-h-[60px]"
                          />
                        </div>
                        {showOutbreakLink ? (
                          <div>
                            <label className="text-xs font-medium text-muted-foreground">Linked outbreak</label>
                            <select
                              value={signalWorkflow.linkedOutbreakId}
                              onChange={(e) => updateSignalWorkflow("linkedOutbreakId", e.target.value)}
                              className="w-full h-8 px-2 text-xs border border-border rounded-lg mt-1"
                            >
                              <option value="">Select outbreak</option>
                              {outbreaks.map((o) => (
                                <option key={o.id} value={o.id}>
                                  {o.name} ({o.lifecycleStatus})
                                </option>
                              ))}
                            </select>
                          </div>
                        ) : null}
                      </div>
                    </div>
                  </div>

                  <div className="flex flex-wrap gap-2">
                    <button
                      type="button"
                      disabled={openInvestigation.isPending || updateInvestigationStatus.isPending}
                      onClick={() => void submitSignalInvestigation(sig, true)}
                      className="inline-flex items-center gap-1 px-3 py-1.5 bg-primary text-white text-xs font-medium rounded-lg hover:bg-primary-hover disabled:opacity-50"
                    >
                      {(openInvestigation.isPending || updateInvestigationStatus.isPending) ? (
                        <Loader2 className="h-3.5 w-3.5 animate-spin" />
                      ) : null}
                      Save & Initiate Response
                    </button>
                    <button
                      type="button"
                      disabled={openInvestigation.isPending}
                      onClick={() => void submitSignalInvestigation(sig, false)}
                      className="px-3 py-1.5 bg-neutral-100 text-foreground text-xs font-medium rounded-lg disabled:opacity-50"
                    >
                      Save as Draft
                    </button>
                    <button
                      type="button"
                      onClick={() => setShowOutbreakLink((v) => !v)}
                      className="px-3 py-1.5 bg-neutral-100 text-foreground text-xs font-medium rounded-lg"
                    >
                      {showOutbreakLink ? "Hide outbreak link" : "Link to Outbreak"}
                    </button>
                    <button
                      type="button"
                      onClick={() => {
                        setSelectedSignal(null);
                        resetSignalWorkflow();
                      }}
                      className="px-3 py-1.5 bg-neutral-100 text-foreground text-xs font-medium rounded-lg"
                    >
                      Cancel
                    </button>
                  </div>
                </div>
              );
            })()}
          </div>
        </div>
      )}

      {/* Cases Tab */}
      {activeSubTab === "cases" && (
        <div className="bg-card rounded-lg border border-border">
          <div className="px-4 py-3 border-b flex items-center justify-between">
            <div>
              <h4 className="text-sm font-semibold text-foreground">Case-Based Surveillance Reports</h4>
              <p className="text-xs text-muted-foreground">Individual case investigations for notifiable diseases</p>
            </div>
            <button
              type="button"
              onClick={() => {
                setShowNewCase((prev) => !prev);
                setCaseFormError(null);
                setCaseFormSuccess(null);
              }}
              className="inline-flex items-center gap-1 px-3 py-1.5 bg-primary text-white text-xs font-medium rounded-lg hover:bg-primary-hover"
            >
              <Plus className="h-3.5 w-3.5" /> {showNewCase ? "Close form" : "New Case Report"}
            </button>
          </div>

          <div className="p-4 space-y-4">
            {caseFormSuccess && (
              <div className="rounded-lg border border-green-200 bg-green-50 px-3 py-2 text-xs text-green-800">
                {caseFormSuccess}
              </div>
            )}
            {showNewCase && (
              <div className="p-4 border border-primary/25 bg-primary-soft rounded-lg">
                <h4 className="font-semibold text-sm mb-3">New Case-Based Report (CBR)</h4>
                {caseFormError && (
                  <div className="mb-3 rounded-lg border border-danger/28 bg-danger-soft px-3 py-2 text-xs text-red-800">
                    {caseFormError}
                  </div>
                )}
                <div className="grid grid-cols-3 gap-3">
                  <div>
                    <label className="text-xs font-medium text-muted-foreground">Disease</label>
                    <input
                      value={caseForm.disease}
                      onChange={(e) => updateCaseForm("disease", e.target.value)}
                      placeholder="e.g. cholera"
                      className="w-full h-8 px-2 text-xs border border-border rounded-lg mt-1"
                    />
                  </div>
                  <div>
                    <label className="text-xs font-medium text-muted-foreground">Patient (CPID)</label>
                    <input
                      value={caseForm.patientRef}
                      onChange={(e) => updateCaseForm("patientRef", e.target.value)}
                      placeholder="Search or enter CPID"
                      className="w-full h-8 px-2 text-xs border border-border rounded-lg mt-1"
                    />
                  </div>
                  <div>
                    <label className="text-xs font-medium text-muted-foreground">Reporting Facility UUID</label>
                    <input
                      value={caseForm.facilityId}
                      onChange={(e) => updateCaseForm("facilityId", e.target.value)}
                      placeholder="optional UUID"
                      className="w-full h-8 px-2 text-xs border border-border rounded-lg mt-1"
                    />
                  </div>
                  <div>
                    <label className="text-xs font-medium text-muted-foreground">Date of Onset</label>
                    <input
                      type="date"
                      value={caseForm.dateOnset}
                      onChange={(e) => updateCaseForm("dateOnset", e.target.value)}
                      className="w-full h-8 px-2 text-xs border border-border rounded-lg mt-1"
                    />
                  </div>
                  <div>
                    <label className="text-xs font-medium text-muted-foreground">Date of Notification</label>
                    <input
                      type="date"
                      value={caseForm.dateNotification}
                      onChange={(e) => updateCaseForm("dateNotification", e.target.value)}
                      className="w-full h-8 px-2 text-xs border border-border rounded-lg mt-1"
                    />
                  </div>
                  <div>
                    <label className="text-xs font-medium text-muted-foreground">Initial Classification</label>
                    <select
                      value={caseForm.classification}
                      onChange={(e) => updateCaseForm("classification", e.target.value)}
                      className="w-full h-8 px-2 text-xs border border-border rounded-lg mt-1"
                    >
                      <option value="suspected">Suspected</option>
                      <option value="probable">Probable</option>
                      <option value="confirmed">Confirmed</option>
                    </select>
                  </div>
                  <div>
                    <label className="text-xs font-medium text-muted-foreground">Outcome</label>
                    <select
                      value={caseForm.outcome}
                      onChange={(e) => updateCaseForm("outcome", e.target.value)}
                      className="w-full h-8 px-2 text-xs border border-border rounded-lg mt-1"
                    >
                      <option value="admitted">Admitted</option>
                      <option value="outpatient">Outpatient</option>
                      <option value="recovering">Recovering</option>
                      <option value="deceased">Deceased</option>
                    </select>
                  </div>
                </div>
                <div className="mt-3">
                  <label className="text-xs font-medium text-muted-foreground">Clinical Summary</label>
                  <textarea
                    value={caseForm.summary}
                    onChange={(e) => updateCaseForm("summary", e.target.value)}
                    placeholder="Symptoms, signs, treatment given..."
                    className="w-full text-xs border border-border rounded-lg mt-1 p-2 min-h-[60px]"
                  />
                </div>
                <div className="flex gap-2 mt-3">
                  <button
                    type="button"
                    onClick={submitCaseReport}
                    disabled={ingestEvent.isPending}
                    className="px-3 py-1.5 bg-primary text-white text-xs font-medium rounded-lg hover:bg-primary-hover disabled:opacity-60"
                  >
                    {ingestEvent.isPending ? "Submitting..." : "Submit CBR"}
                  </button>
                  <button
                    type="button"
                    onClick={() => setShowNewCase(false)}
                    className="px-3 py-1.5 bg-neutral-100 text-foreground text-xs font-medium rounded-lg"
                  >
                    Cancel
                  </button>
                </div>
              </div>
            )}

            <div className="overflow-x-auto">
              {caseLoading && (
                <div className="flex items-center justify-center gap-2 py-8 text-sm text-muted-foreground">
                  <Loader2 className="h-4 w-4 animate-spin" /> Loading cases…
                </div>
              )}
              {caseError && (
                <p className="px-3 py-4 text-sm text-danger">Unable to load cases from surveillance-service via BFF.</p>
              )}
              {!caseLoading && !caseError && apiCases.length === 0 && (
                <p className="px-3 py-4 text-sm text-muted-foreground">No surveillance cases returned for this tenant.</p>
              )}
              {!caseLoading && !caseError && apiCases.length > 0 && (
              <table className="w-full text-xs">
                <thead>
                  <tr className="border-b bg-background">
                    <th className="text-left px-3 py-2 font-medium text-muted-foreground">CBR ID</th>
                    <th className="text-left px-3 py-2 font-medium text-muted-foreground">Disease</th>
                    <th className="text-left px-3 py-2 font-medium text-muted-foreground">Patient (CPID)</th>
                    <th className="text-left px-3 py-2 font-medium text-muted-foreground">Age/Sex</th>
                    <th className="text-left px-3 py-2 font-medium text-muted-foreground">Facility</th>
                    <th className="text-left px-3 py-2 font-medium text-muted-foreground">Date</th>
                    <th className="text-left px-3 py-2 font-medium text-muted-foreground">Classification</th>
                    <th className="text-left px-3 py-2 font-medium text-muted-foreground">Outcome</th>
                    <th className="text-left px-3 py-2 font-medium text-muted-foreground"></th>
                  </tr>
                </thead>
                <tbody>
                  {apiCases.map((c) => (
                    <tr key={c.id} className={`border-b hover:bg-background ${selectedCase === c.id ? "bg-primary-soft" : ""}`}>
                      <td className="px-3 py-2 font-mono">{c.id}</td>
                      <td className="px-3 py-2 font-medium text-foreground">{c.disease}</td>
                      <td className="px-3 py-2 font-mono">{c.patientRef}</td>
                      <td className="px-3 py-2 text-muted-foreground">—</td>
                      <td className="px-3 py-2 text-muted-foreground">{c.facility}</td>
                      <td className="px-3 py-2 text-muted-foreground">{c.reportedAt || "—"}</td>
                      <td className="px-3 py-2">
                        <span className="px-2 py-0.5 border border-border rounded text-[10px] capitalize">{c.status.replace(/_/g, " ")}</span>
                      </td>
                      <td className="px-3 py-2 capitalize">{c.outcome}</td>
                      <td className="px-3 py-2">
                        <button type="button" onClick={() => setSelectedCase(selectedCase === c.id ? null : c.id)}
                          className="px-2 py-1 border border-border rounded text-[10px] font-medium hover:bg-background">
                          {selectedCase === c.id ? "Close" : "Update"}
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
              )}
            </div>

            {/* Expanded Case Update */}
            {selectedCase && (() => {
              const c = apiCases.find((r) => r.id === selectedCase);
              if (!c) return null;
              return (
                <div className="p-3 bg-background rounded-lg border border-border space-y-3">
                  <h4 className="text-sm font-semibold">Case Update - {c.id}</h4>
                  <div className="grid grid-cols-3 gap-3">
                    <div>
                      <label className="text-xs font-medium text-muted-foreground">Update Classification</label>
                      <select className="w-full h-8 px-2 text-xs border border-border rounded-lg mt-1">
                        <option value="suspected">Suspected</option>
                        <option value="probable">Probable</option>
                        <option value="confirmed">Confirmed</option>
                        <option value="discarded">Discarded</option>
                      </select>
                    </div>
                    <div>
                      <label className="text-xs font-medium text-muted-foreground">Update Outcome</label>
                      <select className="w-full h-8 px-2 text-xs border border-border rounded-lg mt-1">
                        <option value="admitted">Admitted</option>
                        <option value="recovering">Recovering</option>
                        <option value="discharged">Discharged</option>
                        <option value="deceased">Deceased</option>
                      </select>
                    </div>
                    <div>
                      <label className="text-xs font-medium text-muted-foreground">Lab Result</label>
                      <select className="w-full h-8 px-2 text-xs border border-border rounded-lg mt-1">
                        <option value="">Select</option>
                        <option value="pending">Pending</option>
                        <option value="positive">Positive</option>
                        <option value="negative">Negative</option>
                        <option value="inconclusive">Inconclusive</option>
                      </select>
                    </div>
                  </div>
                  <div>
                    <label className="text-xs font-medium text-muted-foreground">Investigation Notes</label>
                    <textarea placeholder="Progress notes..." className="w-full text-xs border border-border rounded-lg mt-1 p-2 min-h-[40px]" />
                  </div>
                  <div className="flex gap-2">
                    <button className="px-3 py-1.5 bg-primary text-white text-xs font-medium rounded-lg hover:bg-primary-hover">Save Update</button>
                    <button className="px-3 py-1.5 bg-neutral-100 text-foreground text-xs font-medium rounded-lg">Link Contacts</button>
                    <button className="px-3 py-1.5 bg-neutral-100 text-foreground text-xs font-medium rounded-lg">Generate Line List</button>
                  </div>
                </div>
              );
            })()}
          </div>
        </div>
      )}

      {/* Weekly IDSR / eIDSR — live feed from /public-health/weekly-idsr */}
      {activeSubTab === "weekly" && (
        <div className="space-y-4">
          <div className="rounded-lg border border-success/25 bg-success-soft px-4 py-3 text-sm text-emerald-950">
            <p className="font-medium">Weekly aggregate (IDSR / eIDSR)</p>
            <p className="mt-2 text-xs leading-relaxed text-primary-hover/90">
              Data is loaded from <code className="text-[11px]">GET /internal/v1/public-health/weekly-idsr</code>.
              This feed is currently derived from surveillance counters while richer weekly aggregates are productized.
            </p>
          </div>

          <div className="rounded-lg border border-border bg-card">
            <div className="border-b px-4 py-3">
              <h4 className="text-sm font-semibold text-foreground">Facility reporting completeness (demo W14-2026)</h4>
              <p className="text-xs text-muted-foreground">Zero reporting vs positive counts per notifiable week</p>
            </div>
            <div className="overflow-x-auto p-2">
              <table className="w-full min-w-[640px] text-xs">
                <thead>
                  <tr className="border-b bg-background text-left">
                    <th className="px-3 py-2 font-medium text-muted-foreground">Facility</th>
                    <th className="px-3 py-2 font-medium text-muted-foreground">Week</th>
                    <th className="px-3 py-2 font-medium text-muted-foreground">Submitted</th>
                    <th className="px-3 py-2 font-medium text-muted-foreground">On time</th>
                    <th className="px-3 py-2 font-medium text-muted-foreground">Diseases</th>
                    <th className="px-3 py-2 font-medium text-muted-foreground">Zero reports</th>
                    <th className="px-3 py-2 font-medium text-muted-foreground">Positive</th>
                  </tr>
                </thead>
                <tbody>
                  {weeklyRows.map((w) => (
                    <tr key={w.id} className="border-b hover:bg-background">
                      <td className="px-3 py-2 font-medium text-foreground">{w.detail || "Unknown facility"}</td>
                      <td className="px-3 py-2 font-mono text-muted-foreground">Current</td>
                      <td className="px-3 py-2">Yes</td>
                      <td className="px-3 py-2">Yes</td>
                      <td className="px-3 py-2 tabular-nums">1</td>
                      <td className="px-3 py-2 tabular-nums">0</td>
                      <td className="px-3 py-2 tabular-nums font-medium text-foreground">{w.value}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
              {!weeklyLoading && !weeklyError && weeklyRows.length === 0 && (
                <p className="px-3 py-4 text-sm text-muted-foreground">No weekly aggregate rows returned.</p>
              )}
              {weeklyLoading && (
                <p className="px-3 py-4 text-sm text-muted-foreground">Loading weekly aggregate feed…</p>
              )}
              {weeklyError && (
                <p className="px-3 py-4 text-sm text-danger">Unable to load weekly aggregate feed from the BFF.</p>
              )}
            </div>
          </div>

          <div className="grid gap-4 md:grid-cols-2">
            <div className="rounded-lg border border-border bg-card p-4">
              <h5 className="text-sm font-semibold text-foreground">Aggregate disease counts (demo)</h5>
              <p className="mb-3 text-xs text-muted-foreground">Cross-facility roll-up for the same reporting week</p>
              <ul className="space-y-2 text-xs">
                {[
                  { disease: "Weekly rows received", n: weeklyRows.length, trend: "from weekly feed" },
                  { disease: "Counter snapshots", n: counters.length, trend: "from surveillance counters" },
                  { disease: "Signal definitions", n: apiSignals.length, trend: "from signals" },
                  { disease: "Case reports", n: apiCases.length, trend: "from cases" },
                ].map((d) => (
                  <li key={d.disease} className="flex justify-between rounded border border-border px-2 py-1.5">
                    <span className="font-medium text-foreground">{d.disease}</span>
                    <span className="tabular-nums text-foreground">
                      {d.n} <span className="text-[10px] text-muted-foreground">({d.trend})</span>
                    </span>
                  </li>
                ))}
              </ul>
            </div>
            <div className="rounded-lg border border-border bg-card p-4">
              <h5 className="text-sm font-semibold text-foreground">Timeliness &amp; completeness (demo KPIs)</h5>
              <p className="mb-3 text-xs text-muted-foreground">Aligns with MoHCC IDSR targets — wire to analytics when ready</p>
              <ul className="space-y-3 text-xs">
                {[
                  { label: "Weekly feed availability", value: weeklyError ? 0 : 100, target: 100 },
                  { label: "Weekly rows present", value: weeklyRows.length > 0 ? 100 : 0, target: 1 },
                  { label: "Counter feed availability", value: ctrError ? 0 : 100, target: 100 },
                  { label: "Case feed availability", value: caseError ? 0 : 100, target: 100 },
                ].map((m) => (
                  <li key={m.label}>
                    <div className="mb-1 flex justify-between">
                      <span className="font-medium text-foreground">{m.label}</span>
                      <span className={m.value >= m.target ? "text-primary-hover" : "text-warning-foreground"}>
                        {m.value}% (target {m.target}%)
                      </span>
                    </div>
                    <div className="h-2 overflow-hidden rounded-full bg-neutral-100">
                      <div
                        className={`h-full rounded-full ${m.value >= m.target ? "bg-emerald-500" : "bg-amber-500"}`}
                        style={{ width: `${Math.min(100, m.value)}%` }}
                      />
                    </div>
                  </li>
                ))}
              </ul>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

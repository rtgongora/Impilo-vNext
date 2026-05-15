"use client";

import { useState } from "react";
import {
  AlertTriangle, Plus, Eye, Loader2,
} from "lucide-react";
import {
  usePublicHealthCases,
  usePublicHealthCounters,
  usePublicHealthSignals,
  usePublicHealthWeeklyIdsr,
} from "@/hooks/queries/usePublicHealth";

export function SurveillanceTab() {
  const { data: apiSignals = [], isLoading: sigLoading, isError: sigError } = usePublicHealthSignals();
  const { data: apiCases = [], isLoading: caseLoading, isError: caseError } = usePublicHealthCases();
  const { data: counters = [], isLoading: ctrLoading, isError: ctrError } = usePublicHealthCounters();
  const { data: weeklyRows = [], isLoading: weeklyLoading, isError: weeklyError } = usePublicHealthWeeklyIdsr();

  const [filter, setFilter] = useState("all");
  const [showNewEvent, setShowNewEvent] = useState(false);
  const [showNewCase, setShowNewCase] = useState(false);
  const [selectedSignal, setSelectedSignal] = useState<string | null>(null);
  const [selectedCase, setSelectedCase] = useState<string | null>(null);
  const [activeSubTab, setActiveSubTab] = useState<"signals" | "cases" | "weekly">("signals");

  const filteredSignals = apiSignals.filter((s) => {
    if (filter === "all") return true;
    return s.status.toUpperCase() === filter.toUpperCase();
  });

  return (
    <div className="space-y-4">
      <div className="rounded-lg border border-impilo-200 bg-impilo-50/80 p-3 text-xs text-impilo-800">
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
            color: "text-red-700",
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
            color: "text-impilo-600",
            sub: ctrError ? "Could not reach counters" : "From /public-health/counters",
          },
          {
            label: "Weekly IDSR",
            value: weeklyLoading ? "…" : String(weeklyRows.length),
            color: "text-impilo-700",
            sub: weeklyError ? "Could not reach weekly aggregate" : "From /public-health/weekly-idsr",
          },
          { label: "Reporting completeness", value: "Live", color: "text-emerald-700", sub: "Derived from weekly aggregate feed" },
        ].map((kpi, i) => (
          <div key={i} className="bg-white rounded-lg border border-gray-200 p-3 text-center">
            <p className={`text-2xl font-bold ${kpi.color}`}>{kpi.value}</p>
            <p className="text-xs font-medium text-gray-900">{kpi.label}</p>
            <p className="text-[10px] text-gray-500">{kpi.sub}</p>
          </div>
        ))}
      </div>

      {/* Sub-tabs */}
      <div className="flex gap-1 border-b border-gray-200">
        {[
          { key: "signals" as const, label: "Threshold Signals" },
          { key: "cases" as const, label: "Case-Based Reports" },
          { key: "weekly" as const, label: "Weekly Aggregate" },
        ].map((tab) => (
          <button key={tab.key} onClick={() => setActiveSubTab(tab.key)}
            className={`px-3 py-2 text-sm font-medium border-b-2 transition-colors ${
              activeSubTab === tab.key ? "border-amber-600 text-amber-600" : "border-transparent text-gray-500 hover:text-gray-700"
            }`}>
            {tab.label}
          </button>
        ))}
      </div>

      {/* Signals Tab */}
      {activeSubTab === "signals" && (
        <div className="bg-white rounded-lg border border-gray-200">
          <div className="px-4 py-3 border-b flex items-center justify-between">
            <div>
              <h4 className="text-sm font-semibold text-gray-900 flex items-center gap-2">
                <AlertTriangle className="h-4 w-4" /> Signal Triage Queue
              </h4>
              <p className="text-xs text-gray-500">Automated threshold alerts from facility-level reporting</p>
            </div>
            <div className="flex gap-2">
              <select value={filter} onChange={(e) => setFilter(e.target.value)}
                className="h-8 px-2 text-xs border border-gray-300 rounded-lg">
                <option value="all">All</option>
                <option value="ACTIVE">Active</option>
                <option value="INACTIVE">Inactive</option>
              </select>
              <button
                type="button"
                disabled
                className="inline-flex items-center gap-1 px-3 py-1.5 bg-gray-200 text-gray-500 text-xs font-medium rounded-lg cursor-not-allowed"
              >
                <Plus className="h-3.5 w-3.5" /> Report Event (pending)
              </button>
            </div>
          </div>

          <div className="p-4 space-y-4">
            {/* New Event Form */}
            {showNewEvent && (
              <div className="p-4 border border-impilo-200 bg-impilo-50 rounded-lg">
                <h4 className="font-semibold text-sm mb-3">Report New Surveillance Event</h4>
                <div className="grid grid-cols-3 gap-3">
                  <div>
                    <label className="text-xs font-medium text-gray-600">Disease / Condition</label>
                    <select className="w-full h-8 px-2 text-xs border border-gray-300 rounded-lg mt-1">
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
                    <label className="text-xs font-medium text-gray-600">Reporting Facility</label>
                    <input placeholder="Search facility..." className="w-full h-8 px-2 text-xs border border-gray-300 rounded-lg mt-1" />
                  </div>
                  <div>
                    <label className="text-xs font-medium text-gray-600">Number of Cases</label>
                    <input type="number" defaultValue={1} className="w-full h-8 px-2 text-xs border border-gray-300 rounded-lg mt-1" />
                  </div>
                  <div>
                    <label className="text-xs font-medium text-gray-600">Date of Detection</label>
                    <input type="date" defaultValue="2026-04-06" className="w-full h-8 px-2 text-xs border border-gray-300 rounded-lg mt-1" />
                  </div>
                  <div>
                    <label className="text-xs font-medium text-gray-600">Location / Ward</label>
                    <input placeholder="e.g. Ward 22, Budiriro" className="w-full h-8 px-2 text-xs border border-gray-300 rounded-lg mt-1" />
                  </div>
                  <div>
                    <label className="text-xs font-medium text-gray-600">Source</label>
                    <select className="w-full h-8 px-2 text-xs border border-gray-300 rounded-lg mt-1">
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
                  <label className="text-xs font-medium text-gray-600">Clinical Details / Notes</label>
                  <textarea placeholder="Describe symptoms, clinical presentation, epidemiological context..." className="w-full text-xs border border-gray-300 rounded-lg mt-1 p-2 min-h-[60px]" />
                </div>
                <div className="flex gap-2 mt-3">
                  <button className="px-3 py-1.5 bg-impilo-500 text-white text-xs font-medium rounded-lg hover:bg-impilo-600">Submit Event</button>
                  <button onClick={() => setShowNewEvent(false)} className="px-3 py-1.5 bg-gray-100 text-gray-700 text-xs font-medium rounded-lg">Cancel</button>
                </div>
              </div>
            )}

            {/* Signals Table */}
            <div className="overflow-x-auto">
              {sigLoading && (
                <div className="flex items-center justify-center gap-2 py-8 text-sm text-gray-500">
                  <Loader2 className="h-4 w-4 animate-spin" /> Loading signals…
                </div>
              )}
              {sigError && (
                <p className="px-3 py-4 text-sm text-red-700">Unable to load signals from surveillance-service via BFF.</p>
              )}
              {!sigLoading && !sigError && filteredSignals.length === 0 && (
                <p className="px-3 py-4 text-sm text-gray-600">No signal definitions returned for this tenant.</p>
              )}
              {!sigLoading && !sigError && filteredSignals.length > 0 && (
              <table className="w-full text-xs">
                <thead>
                  <tr className="border-b bg-gray-50">
                    <th className="text-left px-3 py-2 font-medium text-gray-600">Signal ID</th>
                    <th className="text-left px-3 py-2 font-medium text-gray-600">Disease</th>
                    <th className="text-left px-3 py-2 font-medium text-gray-600">Facility</th>
                    <th className="text-left px-3 py-2 font-medium text-gray-600">Cases</th>
                    <th className="text-left px-3 py-2 font-medium text-gray-600">Threshold</th>
                    <th className="text-left px-3 py-2 font-medium text-gray-600">Status</th>
                    <th className="text-left px-3 py-2 font-medium text-gray-600">Date</th>
                    <th className="text-left px-3 py-2 font-medium text-gray-600">Action</th>
                  </tr>
                </thead>
                <tbody>
                  {filteredSignals.map((sig) => (
                    <tr key={sig.id}>
                      <td className={`px-3 py-2 font-mono ${selectedSignal === sig.id ? "bg-impilo-50" : ""}`}>{sig.id}</td>
                      <td className="px-3 py-2 font-medium text-gray-900">{sig.disease}</td>
                      <td className="px-3 py-2 text-gray-600">{sig.facility}</td>
                      <td className="px-3 py-2 font-bold">{sig.cases || "—"}</td>
                      <td className="px-3 py-2 text-gray-500">{sig.threshold}</td>
                      <td className="px-3 py-2">
                        <span className={`px-2 py-0.5 rounded-full text-[10px] font-medium ${
                          sig.status.toUpperCase() === "ACTIVE" ? "bg-green-100 text-green-800" : "bg-gray-100 text-gray-700"
                        }`}>{sig.status}</span>
                      </td>
                      <td className="px-3 py-2 text-gray-500">{sig.detectedAt || "—"}</td>
                      <td className="px-3 py-2">
                        <button type="button" onClick={() => setSelectedSignal(selectedSignal === sig.id ? null : sig.id)}
                          className="inline-flex items-center gap-1 px-2 py-1 border border-gray-300 rounded text-[10px] font-medium hover:bg-gray-50">
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
                <div className="p-4 bg-gray-50 rounded-lg border border-gray-200 space-y-4">
                  <h4 className="text-sm font-semibold">Signal Response Workflow — {sig.disease} ({sig.facility})</h4>

                  {/* Step 1: Verification */}
                  <div className="p-3 border border-gray-200 rounded-lg bg-white space-y-2">
                    <div className="flex items-center gap-2">
                      <span className="px-2 py-0.5 text-[10px] border border-gray-300 rounded">Step 1</span>
                      <span className="text-sm font-medium">Signal Verification</span>
                    </div>
                    <div className="grid grid-cols-3 gap-3">
                      <div>
                        <label className="text-xs font-medium text-gray-600">Verification Status</label>
                        <select className="w-full h-8 px-2 text-xs border border-gray-300 rounded-lg mt-1">
                          <option value="">Select</option>
                          <option value="confirmed">Confirmed - True Signal</option>
                          <option value="false_alarm">False Alarm - Data Error</option>
                          <option value="duplicate">Duplicate of Existing</option>
                          <option value="needs_more_info">Needs More Information</option>
                        </select>
                      </div>
                      <div>
                        <label className="text-xs font-medium text-gray-600">Verified By</label>
                        <input placeholder="Officer name" className="w-full h-8 px-2 text-xs border border-gray-300 rounded-lg mt-1" />
                      </div>
                      <div>
                        <label className="text-xs font-medium text-gray-600">Verification Date</label>
                        <input type="date" className="w-full h-8 px-2 text-xs border border-gray-300 rounded-lg mt-1" />
                      </div>
                    </div>
                  </div>

                  {/* Step 2: Risk Assessment */}
                  <div className="p-3 border border-gray-200 rounded-lg bg-white space-y-2">
                    <div className="flex items-center gap-2">
                      <span className="px-2 py-0.5 text-[10px] border border-gray-300 rounded">Step 2</span>
                      <span className="text-sm font-medium">Risk Assessment</span>
                    </div>
                    <div className="grid grid-cols-3 gap-3">
                      <div>
                        <label className="text-xs font-medium text-gray-600">Risk Level</label>
                        <select className="w-full h-8 px-2 text-xs border border-gray-300 rounded-lg mt-1">
                          <option value="">Assess risk</option>
                          <option value="low">Low - Monitor Only</option>
                          <option value="moderate">Moderate - Enhanced Surveillance</option>
                          <option value="high">High - Field Investigation Required</option>
                          <option value="critical">Critical - Immediate Response</option>
                        </select>
                      </div>
                      <div>
                        <label className="text-xs font-medium text-gray-600">Spread Potential</label>
                        <select className="w-full h-8 px-2 text-xs border border-gray-300 rounded-lg mt-1">
                          <option value="">Select</option>
                          <option value="contained">Contained</option>
                          <option value="local">Local Spread Likely</option>
                          <option value="regional">Regional Spread Risk</option>
                        </select>
                      </div>
                      <div>
                        <label className="text-xs font-medium text-gray-600">Population at Risk</label>
                        <input placeholder="Estimated number" className="w-full h-8 px-2 text-xs border border-gray-300 rounded-lg mt-1" />
                      </div>
                    </div>
                  </div>

                  {/* Step 3: Response Actions */}
                  <div className="p-3 border border-gray-200 rounded-lg bg-white space-y-2">
                    <div className="flex items-center gap-2">
                      <span className="px-2 py-0.5 text-[10px] border border-gray-300 rounded">Step 3</span>
                      <span className="text-sm font-medium">Response Actions</span>
                    </div>
                    <div className="grid grid-cols-2 gap-3">
                      <div>
                        <label className="text-xs font-medium text-gray-600">Response Actions (select all applicable)</label>
                        <div className="space-y-1 mt-1">
                          {["Deploy field investigation team", "Initiate contact tracing", "Collect laboratory specimens", "Issue public health alert", "Activate EOC", "Request WHO support", "Begin case management protocol"].map((action, i) => (
                            <label key={i} className="flex items-center gap-2 text-xs">
                              <input type="checkbox" className="rounded" />
                              {action}
                            </label>
                          ))}
                        </div>
                      </div>
                      <div className="space-y-3">
                        <div>
                          <label className="text-xs font-medium text-gray-600">Assign To Team</label>
                          <select className="w-full h-8 px-2 text-xs border border-gray-300 rounded-lg mt-1">
                            <option value="">Select team</option>
                            <option value="">Assign when workforce / field-ops API is connected</option>
                            <option value="new">Create New Team (backlog)</option>
                          </select>
                        </div>
                        <div>
                          <label className="text-xs font-medium text-gray-600">Escalation Level</label>
                          <select className="w-full h-8 px-2 text-xs border border-gray-300 rounded-lg mt-1">
                            <option value="">Select</option>
                            <option value="district">District Level</option>
                            <option value="provincial">Provincial Level</option>
                            <option value="national">National Level</option>
                            <option value="international">International (IHR)</option>
                          </select>
                        </div>
                        <div>
                          <label className="text-xs font-medium text-gray-600">Notes</label>
                          <textarea placeholder="Additional instructions..." className="w-full text-xs border border-gray-300 rounded-lg mt-1 p-2 min-h-[60px]" />
                        </div>
                      </div>
                    </div>
                  </div>

                  <div className="flex gap-2">
                    <button className="px-3 py-1.5 bg-impilo-500 text-white text-xs font-medium rounded-lg hover:bg-impilo-600">Save & Initiate Response</button>
                    <button className="px-3 py-1.5 bg-gray-100 text-gray-700 text-xs font-medium rounded-lg">Save as Draft</button>
                    <button className="px-3 py-1.5 bg-gray-100 text-gray-700 text-xs font-medium rounded-lg">Link to Outbreak</button>
                    <button onClick={() => setSelectedSignal(null)} className="px-3 py-1.5 bg-gray-100 text-gray-700 text-xs font-medium rounded-lg">Cancel</button>
                  </div>
                </div>
              );
            })()}
          </div>
        </div>
      )}

      {/* Cases Tab */}
      {activeSubTab === "cases" && (
        <div className="bg-white rounded-lg border border-gray-200">
          <div className="px-4 py-3 border-b flex items-center justify-between">
            <div>
              <h4 className="text-sm font-semibold text-gray-900">Case-Based Surveillance Reports</h4>
              <p className="text-xs text-gray-500">Individual case investigations for notifiable diseases</p>
            </div>
            <button
              type="button"
              disabled
              className="inline-flex items-center gap-1 px-3 py-1.5 bg-gray-200 text-gray-500 text-xs font-medium rounded-lg cursor-not-allowed"
            >
              <Plus className="h-3.5 w-3.5" /> New Case Report (pending)
            </button>
          </div>

          <div className="p-4 space-y-4">
            {showNewCase && (
              <div className="p-4 border border-impilo-200 bg-impilo-50 rounded-lg">
                <h4 className="font-semibold text-sm mb-3">New Case-Based Report (CBR)</h4>
                <div className="grid grid-cols-3 gap-3">
                  <div>
                    <label className="text-xs font-medium text-gray-600">Disease</label>
                    <select className="w-full h-8 px-2 text-xs border border-gray-300 rounded-lg mt-1">
                      <option value="">Select</option>
                      <option value="cholera">Cholera</option>
                      <option value="typhoid">Typhoid</option>
                      <option value="measles">Measles</option>
                      <option value="afp">AFP</option>
                      <option value="neonatal_tetanus">Neonatal Tetanus</option>
                      <option value="meningitis">Meningitis</option>
                      <option value="yellow_fever">Yellow Fever</option>
                    </select>
                  </div>
                  <div>
                    <label className="text-xs font-medium text-gray-600">Patient (CPID)</label>
                    <input placeholder="Search or enter CPID" className="w-full h-8 px-2 text-xs border border-gray-300 rounded-lg mt-1" />
                  </div>
                  <div>
                    <label className="text-xs font-medium text-gray-600">Age</label>
                    <input type="number" className="w-full h-8 px-2 text-xs border border-gray-300 rounded-lg mt-1" />
                  </div>
                  <div>
                    <label className="text-xs font-medium text-gray-600">Sex</label>
                    <select className="w-full h-8 px-2 text-xs border border-gray-300 rounded-lg mt-1">
                      <option value="">Select</option>
                      <option value="M">Male</option>
                      <option value="F">Female</option>
                    </select>
                  </div>
                  <div>
                    <label className="text-xs font-medium text-gray-600">Reporting Facility</label>
                    <input placeholder="Search facility" className="w-full h-8 px-2 text-xs border border-gray-300 rounded-lg mt-1" />
                  </div>
                  <div>
                    <label className="text-xs font-medium text-gray-600">Date of Onset</label>
                    <input type="date" className="w-full h-8 px-2 text-xs border border-gray-300 rounded-lg mt-1" />
                  </div>
                  <div>
                    <label className="text-xs font-medium text-gray-600">Date of Notification</label>
                    <input type="date" defaultValue="2026-04-06" className="w-full h-8 px-2 text-xs border border-gray-300 rounded-lg mt-1" />
                  </div>
                  <div>
                    <label className="text-xs font-medium text-gray-600">Initial Classification</label>
                    <select className="w-full h-8 px-2 text-xs border border-gray-300 rounded-lg mt-1">
                      <option value="">Select</option>
                      <option value="suspected">Suspected</option>
                      <option value="probable">Probable</option>
                      <option value="confirmed">Confirmed</option>
                    </select>
                  </div>
                  <div>
                    <label className="text-xs font-medium text-gray-600">Outcome</label>
                    <select className="w-full h-8 px-2 text-xs border border-gray-300 rounded-lg mt-1">
                      <option value="">Select</option>
                      <option value="admitted">Admitted</option>
                      <option value="outpatient">Outpatient</option>
                      <option value="recovering">Recovering</option>
                      <option value="deceased">Deceased</option>
                    </select>
                  </div>
                </div>
                <div className="mt-3">
                  <label className="text-xs font-medium text-gray-600">Clinical Summary</label>
                  <textarea placeholder="Symptoms, signs, treatment given..." className="w-full text-xs border border-gray-300 rounded-lg mt-1 p-2 min-h-[60px]" />
                </div>
                <div className="flex gap-2 mt-3">
                  <button className="px-3 py-1.5 bg-impilo-500 text-white text-xs font-medium rounded-lg hover:bg-impilo-600">Submit CBR</button>
                  <button onClick={() => setShowNewCase(false)} className="px-3 py-1.5 bg-gray-100 text-gray-700 text-xs font-medium rounded-lg">Cancel</button>
                </div>
              </div>
            )}

            <div className="overflow-x-auto">
              {caseLoading && (
                <div className="flex items-center justify-center gap-2 py-8 text-sm text-gray-500">
                  <Loader2 className="h-4 w-4 animate-spin" /> Loading cases…
                </div>
              )}
              {caseError && (
                <p className="px-3 py-4 text-sm text-red-700">Unable to load cases from surveillance-service via BFF.</p>
              )}
              {!caseLoading && !caseError && apiCases.length === 0 && (
                <p className="px-3 py-4 text-sm text-gray-600">No surveillance cases returned for this tenant.</p>
              )}
              {!caseLoading && !caseError && apiCases.length > 0 && (
              <table className="w-full text-xs">
                <thead>
                  <tr className="border-b bg-gray-50">
                    <th className="text-left px-3 py-2 font-medium text-gray-600">CBR ID</th>
                    <th className="text-left px-3 py-2 font-medium text-gray-600">Disease</th>
                    <th className="text-left px-3 py-2 font-medium text-gray-600">Patient (CPID)</th>
                    <th className="text-left px-3 py-2 font-medium text-gray-600">Age/Sex</th>
                    <th className="text-left px-3 py-2 font-medium text-gray-600">Facility</th>
                    <th className="text-left px-3 py-2 font-medium text-gray-600">Date</th>
                    <th className="text-left px-3 py-2 font-medium text-gray-600">Classification</th>
                    <th className="text-left px-3 py-2 font-medium text-gray-600">Outcome</th>
                    <th className="text-left px-3 py-2 font-medium text-gray-600"></th>
                  </tr>
                </thead>
                <tbody>
                  {apiCases.map((c) => (
                    <tr key={c.id} className={`border-b hover:bg-gray-50 ${selectedCase === c.id ? "bg-impilo-50" : ""}`}>
                      <td className="px-3 py-2 font-mono">{c.id}</td>
                      <td className="px-3 py-2 font-medium text-gray-900">{c.disease}</td>
                      <td className="px-3 py-2 font-mono">{c.patientRef}</td>
                      <td className="px-3 py-2 text-gray-400">—</td>
                      <td className="px-3 py-2 text-gray-600">{c.facility}</td>
                      <td className="px-3 py-2 text-gray-500">{c.reportedAt || "—"}</td>
                      <td className="px-3 py-2">
                        <span className="px-2 py-0.5 border border-gray-300 rounded text-[10px] capitalize">{c.status.replace(/_/g, " ")}</span>
                      </td>
                      <td className="px-3 py-2 capitalize">{c.outcome}</td>
                      <td className="px-3 py-2">
                        <button type="button" onClick={() => setSelectedCase(selectedCase === c.id ? null : c.id)}
                          className="px-2 py-1 border border-gray-300 rounded text-[10px] font-medium hover:bg-gray-50">
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
                <div className="p-3 bg-gray-50 rounded-lg border border-gray-200 space-y-3">
                  <h4 className="text-sm font-semibold">Case Update - {c.id}</h4>
                  <div className="grid grid-cols-3 gap-3">
                    <div>
                      <label className="text-xs font-medium text-gray-600">Update Classification</label>
                      <select className="w-full h-8 px-2 text-xs border border-gray-300 rounded-lg mt-1">
                        <option value="suspected">Suspected</option>
                        <option value="probable">Probable</option>
                        <option value="confirmed">Confirmed</option>
                        <option value="discarded">Discarded</option>
                      </select>
                    </div>
                    <div>
                      <label className="text-xs font-medium text-gray-600">Update Outcome</label>
                      <select className="w-full h-8 px-2 text-xs border border-gray-300 rounded-lg mt-1">
                        <option value="admitted">Admitted</option>
                        <option value="recovering">Recovering</option>
                        <option value="discharged">Discharged</option>
                        <option value="deceased">Deceased</option>
                      </select>
                    </div>
                    <div>
                      <label className="text-xs font-medium text-gray-600">Lab Result</label>
                      <select className="w-full h-8 px-2 text-xs border border-gray-300 rounded-lg mt-1">
                        <option value="">Select</option>
                        <option value="pending">Pending</option>
                        <option value="positive">Positive</option>
                        <option value="negative">Negative</option>
                        <option value="inconclusive">Inconclusive</option>
                      </select>
                    </div>
                  </div>
                  <div>
                    <label className="text-xs font-medium text-gray-600">Investigation Notes</label>
                    <textarea placeholder="Progress notes..." className="w-full text-xs border border-gray-300 rounded-lg mt-1 p-2 min-h-[40px]" />
                  </div>
                  <div className="flex gap-2">
                    <button className="px-3 py-1.5 bg-impilo-500 text-white text-xs font-medium rounded-lg hover:bg-impilo-600">Save Update</button>
                    <button className="px-3 py-1.5 bg-gray-100 text-gray-700 text-xs font-medium rounded-lg">Link Contacts</button>
                    <button className="px-3 py-1.5 bg-gray-100 text-gray-700 text-xs font-medium rounded-lg">Generate Line List</button>
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
          <div className="rounded-lg border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-950">
            <p className="font-medium">Weekly aggregate (IDSR / eIDSR)</p>
            <p className="mt-2 text-xs leading-relaxed text-emerald-900/90">
              Data is loaded from <code className="text-[11px]">GET /internal/v1/public-health/weekly-idsr</code>.
              This feed is currently derived from surveillance counters while richer weekly aggregates are productized.
            </p>
          </div>

          <div className="rounded-lg border border-gray-200 bg-white">
            <div className="border-b px-4 py-3">
              <h4 className="text-sm font-semibold text-gray-900">Facility reporting completeness (demo W14-2026)</h4>
              <p className="text-xs text-gray-500">Zero reporting vs positive counts per notifiable week</p>
            </div>
            <div className="overflow-x-auto p-2">
              <table className="w-full min-w-[640px] text-xs">
                <thead>
                  <tr className="border-b bg-gray-50 text-left">
                    <th className="px-3 py-2 font-medium text-gray-600">Facility</th>
                    <th className="px-3 py-2 font-medium text-gray-600">Week</th>
                    <th className="px-3 py-2 font-medium text-gray-600">Submitted</th>
                    <th className="px-3 py-2 font-medium text-gray-600">On time</th>
                    <th className="px-3 py-2 font-medium text-gray-600">Diseases</th>
                    <th className="px-3 py-2 font-medium text-gray-600">Zero reports</th>
                    <th className="px-3 py-2 font-medium text-gray-600">Positive</th>
                  </tr>
                </thead>
                <tbody>
                  {weeklyRows.map((w) => (
                    <tr key={w.id} className="border-b hover:bg-gray-50">
                      <td className="px-3 py-2 font-medium text-gray-900">{w.detail || "Unknown facility"}</td>
                      <td className="px-3 py-2 font-mono text-gray-600">Current</td>
                      <td className="px-3 py-2">Yes</td>
                      <td className="px-3 py-2">Yes</td>
                      <td className="px-3 py-2 tabular-nums">1</td>
                      <td className="px-3 py-2 tabular-nums">0</td>
                      <td className="px-3 py-2 tabular-nums font-medium text-gray-900">{w.value}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
              {!weeklyLoading && !weeklyError && weeklyRows.length === 0 && (
                <p className="px-3 py-4 text-sm text-gray-600">No weekly aggregate rows returned.</p>
              )}
              {weeklyLoading && (
                <p className="px-3 py-4 text-sm text-gray-600">Loading weekly aggregate feed…</p>
              )}
              {weeklyError && (
                <p className="px-3 py-4 text-sm text-red-700">Unable to load weekly aggregate feed from the BFF.</p>
              )}
            </div>
          </div>

          <div className="grid gap-4 md:grid-cols-2">
            <div className="rounded-lg border border-gray-200 bg-white p-4">
              <h5 className="text-sm font-semibold text-gray-900">Aggregate disease counts (demo)</h5>
              <p className="mb-3 text-xs text-gray-500">Cross-facility roll-up for the same reporting week</p>
              <ul className="space-y-2 text-xs">
                {[
                  { disease: "Weekly rows received", n: weeklyRows.length, trend: "from weekly feed" },
                  { disease: "Counter snapshots", n: counters.length, trend: "from surveillance counters" },
                  { disease: "Signal definitions", n: apiSignals.length, trend: "from signals" },
                  { disease: "Case reports", n: apiCases.length, trend: "from cases" },
                ].map((d) => (
                  <li key={d.disease} className="flex justify-between rounded border border-gray-100 px-2 py-1.5">
                    <span className="font-medium text-gray-800">{d.disease}</span>
                    <span className="tabular-nums text-gray-700">
                      {d.n} <span className="text-[10px] text-gray-500">({d.trend})</span>
                    </span>
                  </li>
                ))}
              </ul>
            </div>
            <div className="rounded-lg border border-gray-200 bg-white p-4">
              <h5 className="text-sm font-semibold text-gray-900">Timeliness &amp; completeness (demo KPIs)</h5>
              <p className="mb-3 text-xs text-gray-500">Aligns with MoHCC IDSR targets — wire to analytics when ready</p>
              <ul className="space-y-3 text-xs">
                {[
                  { label: "Weekly feed availability", value: weeklyError ? 0 : 100, target: 100 },
                  { label: "Weekly rows present", value: weeklyRows.length > 0 ? 100 : 0, target: 1 },
                  { label: "Counter feed availability", value: ctrError ? 0 : 100, target: 100 },
                  { label: "Case feed availability", value: caseError ? 0 : 100, target: 100 },
                ].map((m) => (
                  <li key={m.label}>
                    <div className="mb-1 flex justify-between">
                      <span className="font-medium text-gray-800">{m.label}</span>
                      <span className={m.value >= m.target ? "text-emerald-700" : "text-amber-800"}>
                        {m.value}% (target {m.target}%)
                      </span>
                    </div>
                    <div className="h-2 overflow-hidden rounded-full bg-gray-100">
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

"use client";

import { useState } from "react";
import {
  AlertTriangle, Plus, Eye, Clock, CheckCircle, Send, Loader2,
} from "lucide-react";
import { usePublicHealthCases, usePublicHealthSignals } from "@/hooks/queries/usePublicHealth";

const WEEKLY_REPORTS = [
  { facility: "Parirenyatwa Hospital", week: "W14-2026", submitted: true, onTime: true, diseases: 12, zero: 8, positive: 4 },
  { facility: "Harare Central Hospital", week: "W14-2026", submitted: true, onTime: false, diseases: 12, zero: 10, positive: 2 },
  { facility: "Chitungwiza Central", week: "W14-2026", submitted: false, onTime: false, diseases: 12, zero: 0, positive: 0 },
  { facility: "Mpilo Hospital", week: "W14-2026", submitted: true, onTime: true, diseases: 12, zero: 11, positive: 1 },
  { facility: "Sally Mugabe Hospital", week: "W14-2026", submitted: true, onTime: true, diseases: 12, zero: 9, positive: 3 },
];

export function SurveillanceTab() {
  const { data: apiSignals = [], isLoading: sigLoading, isError: sigError } = usePublicHealthSignals();
  const { data: apiCases = [], isLoading: caseLoading, isError: caseError } = usePublicHealthCases();

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
      <div className="rounded-lg border border-blue-200 bg-blue-50/80 p-3 text-xs text-blue-900">
        <strong>Live data:</strong> Threshold signal definitions and surveillance cases are loaded from Experience BFF →
        surveillance-service (empty if the service is down or unseeded). Weekly IDSR aggregates below are not backed by a
        repository endpoint yet.
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
          { label: "Reporting Completeness", value: "—", color: "text-blue-700", sub: "Not wired to backend" },
          { label: "Timeliness", value: "—", color: "text-amber-700", sub: "Not wired to backend" },
          { label: "Legacy demo KPIs", value: "—", color: "text-gray-500", sub: "Removed in Wave 1" },
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
          { key: "weekly" as const, label: "Weekly Aggregate (IDSR)" },
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
              <button onClick={() => setShowNewEvent(!showNewEvent)}
                className="inline-flex items-center gap-1 px-3 py-1.5 bg-blue-600 text-white text-xs font-medium rounded-lg hover:bg-blue-700">
                <Plus className="h-3.5 w-3.5" /> Report Event
              </button>
            </div>
          </div>

          <div className="p-4 space-y-4">
            {/* New Event Form */}
            {showNewEvent && (
              <div className="p-4 border border-blue-200 bg-blue-50 rounded-lg">
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
                  <button className="px-3 py-1.5 bg-blue-600 text-white text-xs font-medium rounded-lg hover:bg-blue-700">Submit Event</button>
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
                      <td className={`px-3 py-2 font-mono ${selectedSignal === sig.id ? "bg-blue-50" : ""}`}>{sig.id}</td>
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
                            <option value="ft01">FT-01 Harare South Response</option>
                            <option value="ft02">FT-02 Chitungwiza Investigation</option>
                            <option value="ft03">FT-03 Manicaland Vector Control</option>
                            <option value="new">Create New Team</option>
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
                    <button className="px-3 py-1.5 bg-blue-600 text-white text-xs font-medium rounded-lg hover:bg-blue-700">Save & Initiate Response</button>
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
            <button onClick={() => setShowNewCase(!showNewCase)}
              className="inline-flex items-center gap-1 px-3 py-1.5 bg-blue-600 text-white text-xs font-medium rounded-lg hover:bg-blue-700">
              <Plus className="h-3.5 w-3.5" /> New Case Report
            </button>
          </div>

          <div className="p-4 space-y-4">
            {showNewCase && (
              <div className="p-4 border border-blue-200 bg-blue-50 rounded-lg">
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
                  <button className="px-3 py-1.5 bg-blue-600 text-white text-xs font-medium rounded-lg hover:bg-blue-700">Submit CBR</button>
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
                    <tr key={c.id} className={`border-b hover:bg-gray-50 ${selectedCase === c.id ? "bg-blue-50" : ""}`}>
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
                    <button className="px-3 py-1.5 bg-blue-600 text-white text-xs font-medium rounded-lg hover:bg-blue-700">Save Update</button>
                    <button className="px-3 py-1.5 bg-gray-100 text-gray-700 text-xs font-medium rounded-lg">Link Contacts</button>
                    <button className="px-3 py-1.5 bg-gray-100 text-gray-700 text-xs font-medium rounded-lg">Generate Line List</button>
                  </div>
                </div>
              );
            })()}
          </div>
        </div>
      )}

      {/* Weekly IDSR Tab */}
      {activeSubTab === "weekly" && (
        <div className="bg-white rounded-lg border border-gray-200">
          <div className="border-b border-amber-200 bg-amber-50 px-4 py-2 text-xs text-amber-900">
            <strong>Not connected:</strong> Weekly IDSR facility completeness is still illustrative. There is no matching
            Experience BFF or surveillance-service list endpoint wired for this grid in Wave 1.
          </div>
          <div className="px-4 py-3 border-b flex items-center justify-between">
            <div>
              <h4 className="text-sm font-semibold text-gray-900">Weekly IDSR Aggregate Reporting</h4>
              <p className="text-xs text-gray-500">Facility reporting completeness and timeliness for current epidemiological week</p>
            </div>
            <div className="flex gap-2 items-center">
              <span className="px-2 py-0.5 border border-gray-300 rounded text-[10px]">Week 14, 2026</span>
              <button className="inline-flex items-center gap-1 px-3 py-1.5 bg-gray-100 text-gray-700 text-xs font-medium rounded-lg hover:bg-gray-200">
                <Send className="h-3.5 w-3.5" /> Bulk Reminder
              </button>
            </div>
          </div>
          <div className="overflow-x-auto">
            <table className="w-full text-xs">
              <thead>
                <tr className="border-b bg-gray-50">
                  <th className="text-left px-3 py-2 font-medium text-gray-600">Facility</th>
                  <th className="text-left px-3 py-2 font-medium text-gray-600">Week</th>
                  <th className="text-left px-3 py-2 font-medium text-gray-600">Submitted</th>
                  <th className="text-left px-3 py-2 font-medium text-gray-600">On Time</th>
                  <th className="text-left px-3 py-2 font-medium text-gray-600">Diseases Reported</th>
                  <th className="text-left px-3 py-2 font-medium text-gray-600">Zero Reports</th>
                  <th className="text-left px-3 py-2 font-medium text-gray-600">Positive</th>
                  <th className="text-left px-3 py-2 font-medium text-gray-600"></th>
                </tr>
              </thead>
              <tbody>
                {WEEKLY_REPORTS.map((r, i) => (
                  <tr key={i} className="border-b hover:bg-gray-50">
                    <td className="px-3 py-2 font-medium text-gray-900">{r.facility}</td>
                    <td className="px-3 py-2">{r.week}</td>
                    <td className="px-3 py-2">
                      {r.submitted ? <CheckCircle className="h-4 w-4 text-green-600" /> : <Clock className="h-4 w-4 text-amber-500" />}
                    </td>
                    <td className="px-3 py-2">
                      {r.submitted ? (
                        r.onTime
                          ? <span className="px-2 py-0.5 bg-green-100 text-green-700 rounded-full text-[10px]">On time</span>
                          : <span className="px-2 py-0.5 bg-amber-100 text-amber-700 rounded-full text-[10px]">Late</span>
                      ) : "-"}
                    </td>
                    <td className="px-3 py-2">{r.submitted ? r.diseases : "-"}</td>
                    <td className="px-3 py-2">{r.submitted ? r.zero : "-"}</td>
                    <td className="px-3 py-2">{r.submitted ? r.positive : "-"}</td>
                    <td className="px-3 py-2">
                      {!r.submitted && (
                        <button className="px-2 py-1 border border-gray-300 rounded text-[10px] font-medium hover:bg-gray-50">Send Reminder</button>
                      )}
                      {r.submitted && (
                        <button className="px-2 py-1 text-gray-500 text-[10px] font-medium hover:text-gray-700">View Report</button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </div>
  );
}

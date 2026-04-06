"use client";

import { useState } from "react";
import { ClipboardCheck, Plus, Calendar, AlertTriangle, Star, FileText } from "lucide-react";

const INSPECTIONS = [
  { id: "INS-001", site: "Mbare Musika Market", siteType: "Market", type: "Routine Food Safety", inspector: "J. Moyo", date: "2026-04-08", status: "completed", score: 72, findings: 3, critical: 0, followUp: "2026-05-08" },
  { id: "INS-002", site: "Eastlea Swimming Pool", siteType: "Recreation", type: "Water Quality", inspector: "T. Ndlovu", date: "2026-04-09", status: "scheduled", score: null, findings: 0, critical: 0, followUp: null },
  { id: "INS-003", site: "Southerton Abattoir", siteType: "Abattoir", type: "Meat Safety", inspector: "P. Chirwa", date: "2026-04-07", status: "completed", score: 45, findings: 7, critical: 2, followUp: "2026-04-14" },
  { id: "INS-004", site: "Corner Bakery - Avondale", siteType: "Food Premises", type: "Food Premises", inspector: "S. Makoni", date: "2026-04-10", status: "in_progress", score: null, findings: 1, critical: 0, followUp: null },
  { id: "INS-005", site: "Glen Norah Borehole #3", siteType: "Water Point", type: "Water Source", inspector: "R. Dube", date: "2026-04-06", status: "completed", score: 88, findings: 1, critical: 0, followUp: null },
];

const ENFORCEMENT_ACTIONS = [
  { id: "ENF-001", site: "Southerton Abattoir", violation: "Unsanitary slaughter conditions", severity: "critical", action: "Closure Notice", issued: "2026-04-07", deadline: "2026-04-14", status: "pending_compliance" },
  { id: "ENF-002", site: "Happy Foods Takeaway", violation: "Expired food storage", severity: "high", action: "Improvement Notice", issued: "2026-03-28", deadline: "2026-04-11", status: "complied" },
];

export function InspectionsTab() {
  const [showScheduleForm, setShowScheduleForm] = useState(false);
  const [selectedInspection, setSelectedInspection] = useState<string | null>(null);
  const [showEnforcementForm, setShowEnforcementForm] = useState(false);
  const [activeSubTab, setActiveSubTab] = useState<"inspections" | "enforcement" | "compliance">("inspections");

  return (
    <div className="space-y-4">
      {/* KPIs */}
      <div className="grid grid-cols-5 gap-3">
        {[
          { label: "Inspections This Month", value: "142", Icon: ClipboardCheck },
          { label: "Scheduled (Upcoming)", value: "23", Icon: Calendar },
          { label: "Critical Findings", value: "4", Icon: AlertTriangle },
          { label: "Avg Compliance Score", value: "78%", Icon: Star },
          { label: "Active Enforcement", value: "6", Icon: FileText },
        ].map((kpi, i) => (
          <div key={i} className="bg-white rounded-lg border border-gray-200 p-3 flex items-center gap-3">
            <div className="p-2 rounded-lg bg-blue-50">
              <kpi.Icon className="h-4 w-4 text-blue-600" />
            </div>
            <div>
              <p className="text-lg font-bold text-gray-900">{kpi.value}</p>
              <p className="text-[10px] text-gray-500">{kpi.label}</p>
            </div>
          </div>
        ))}
      </div>

      {/* Sub-tabs */}
      <div className="flex gap-1 border-b border-gray-200">
        {[
          { key: "inspections" as const, label: "Inspection Register" },
          { key: "enforcement" as const, label: "Enforcement Actions" },
          { key: "compliance" as const, label: "Compliance Overview" },
        ].map((tab) => (
          <button key={tab.key} onClick={() => setActiveSubTab(tab.key)}
            className={`px-3 py-2 text-sm font-medium border-b-2 transition-colors ${
              activeSubTab === tab.key ? "border-amber-600 text-amber-600" : "border-transparent text-gray-500 hover:text-gray-700"
            }`}>
            {tab.label}
          </button>
        ))}
      </div>

      {/* Inspection Register */}
      {activeSubTab === "inspections" && (
        <div className="bg-white rounded-lg border border-gray-200">
          <div className="px-4 py-3 border-b flex items-center justify-between">
            <div>
              <h4 className="text-sm font-semibold text-gray-900">Inspection Register</h4>
              <p className="text-xs text-gray-500">Linked to INDAWO sites - routine, ad-hoc, and follow-up inspections</p>
            </div>
            <button onClick={() => setShowScheduleForm(!showScheduleForm)}
              className="inline-flex items-center gap-1 px-3 py-1.5 bg-blue-600 text-white text-xs font-medium rounded-lg hover:bg-blue-700">
              <Plus className="h-3.5 w-3.5" /> Schedule Inspection
            </button>
          </div>
          <div className="p-4 space-y-4">
            {showScheduleForm && (
              <div className="p-4 border border-blue-200 bg-blue-50 rounded-lg">
                <h4 className="font-semibold text-sm mb-3">Schedule New Inspection</h4>
                <div className="grid grid-cols-3 gap-3">
                  <div>
                    <label className="text-xs font-medium text-gray-600">Site / Premises</label>
                    <input placeholder="Search site (INDAWO)..." className="w-full h-8 px-2 text-xs border border-gray-300 rounded-lg mt-1" />
                  </div>
                  <div>
                    <label className="text-xs font-medium text-gray-600">Site Type</label>
                    <select className="w-full h-8 px-2 text-xs border border-gray-300 rounded-lg mt-1">
                      <option value="">Select</option>
                      <option value="food">Food Premises</option>
                      <option value="market">Market</option>
                      <option value="abattoir">Abattoir</option>
                      <option value="water">Water Point</option>
                      <option value="pool">Swimming Pool</option>
                      <option value="school">School</option>
                      <option value="public">Public Facility</option>
                    </select>
                  </div>
                  <div>
                    <label className="text-xs font-medium text-gray-600">Inspection Type</label>
                    <select className="w-full h-8 px-2 text-xs border border-gray-300 rounded-lg mt-1">
                      <option value="">Select</option>
                      <option value="routine">Routine</option>
                      <option value="adhoc">Ad-hoc / Complaint-driven</option>
                      <option value="followup">Follow-up</option>
                      <option value="licensing">Licensing Assessment</option>
                      <option value="outbreak">Outbreak Investigation</option>
                    </select>
                  </div>
                  <div>
                    <label className="text-xs font-medium text-gray-600">Scheduled Date</label>
                    <input type="date" className="w-full h-8 px-2 text-xs border border-gray-300 rounded-lg mt-1" />
                  </div>
                  <div>
                    <label className="text-xs font-medium text-gray-600">Assigned Inspector</label>
                    <select className="w-full h-8 px-2 text-xs border border-gray-300 rounded-lg mt-1">
                      <option value="">Select</option>
                      <option value="jmoyo">J. Moyo</option>
                      <option value="tndlovu">T. Ndlovu</option>
                      <option value="pchirwa">P. Chirwa</option>
                      <option value="smakoni">S. Makoni</option>
                      <option value="rdube">R. Dube</option>
                    </select>
                  </div>
                  <div>
                    <label className="text-xs font-medium text-gray-600">Notes</label>
                    <textarea placeholder="Specific areas to inspect..." className="w-full text-xs border border-gray-300 rounded-lg mt-1 p-2 min-h-[32px]" />
                  </div>
                </div>
                <div className="flex gap-2 mt-3">
                  <button className="px-3 py-1.5 bg-blue-600 text-white text-xs font-medium rounded-lg hover:bg-blue-700">Schedule</button>
                  <button onClick={() => setShowScheduleForm(false)} className="px-3 py-1.5 bg-gray-100 text-gray-700 text-xs font-medium rounded-lg">Cancel</button>
                </div>
              </div>
            )}

            <div className="overflow-x-auto">
              <table className="w-full text-xs">
                <thead>
                  <tr className="border-b bg-gray-50">
                    <th className="text-left px-3 py-2 font-medium text-gray-600">ID</th>
                    <th className="text-left px-3 py-2 font-medium text-gray-600">Site</th>
                    <th className="text-left px-3 py-2 font-medium text-gray-600">Type</th>
                    <th className="text-left px-3 py-2 font-medium text-gray-600">Inspector</th>
                    <th className="text-left px-3 py-2 font-medium text-gray-600">Date</th>
                    <th className="text-left px-3 py-2 font-medium text-gray-600">Status</th>
                    <th className="text-left px-3 py-2 font-medium text-gray-600">Score</th>
                    <th className="text-left px-3 py-2 font-medium text-gray-600">Findings</th>
                    <th className="text-left px-3 py-2 font-medium text-gray-600"></th>
                  </tr>
                </thead>
                <tbody>
                  {INSPECTIONS.map(ins => (
                    <tr key={ins.id} className={`border-b hover:bg-gray-50 ${selectedInspection === ins.id ? "bg-blue-50" : ""}`}>
                      <td className="px-3 py-2 font-mono">{ins.id}</td>
                      <td className="px-3 py-2 font-medium text-gray-900">{ins.site}</td>
                      <td className="px-3 py-2">{ins.type}</td>
                      <td className="px-3 py-2">{ins.inspector}</td>
                      <td className="px-3 py-2 text-gray-500">{ins.date}</td>
                      <td className="px-3 py-2">
                        <span className={`px-2 py-0.5 rounded-full text-[10px] font-medium ${
                          ins.status === "completed" ? "bg-green-100 text-green-700" :
                          ins.status === "in_progress" ? "bg-blue-100 text-blue-700" : "bg-gray-100 text-gray-600"
                        }`}>{ins.status.replace(/_/g, " ")}</span>
                      </td>
                      <td className="px-3 py-2">
                        {ins.score !== null ? (
                          <span className={`font-bold ${ins.score >= 80 ? "text-green-700" : ins.score >= 60 ? "text-amber-700" : "text-red-700"}`}>{ins.score}%</span>
                        ) : "-"}
                      </td>
                      <td className="px-3 py-2">
                        {ins.findings > 0 ? (
                          <span>{ins.findings} {ins.critical > 0 && <span className="px-1.5 py-0.5 bg-red-100 text-red-700 rounded text-[10px] ml-1">{ins.critical} critical</span>}</span>
                        ) : "-"}
                      </td>
                      <td className="px-3 py-2">
                        <button onClick={() => setSelectedInspection(selectedInspection === ins.id ? null : ins.id)}
                          className="px-2 py-1 border border-gray-300 rounded text-[10px] font-medium hover:bg-gray-50">
                          {selectedInspection === ins.id ? "Close" : ins.status === "scheduled" ? "Start" : ins.status === "in_progress" ? "Continue" : "Review"}
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            {/* Expanded Inspection Form */}
            {selectedInspection && (() => {
              const ins = INSPECTIONS.find(i => i.id === selectedInspection);
              if (!ins) return null;
              return (
                <div className="p-4 bg-gray-50 rounded-lg border border-gray-200 space-y-3">
                  <h4 className="text-sm font-semibold">Inspection Form - {ins.site}</h4>
                  <div className="p-3 border border-gray-200 rounded-lg bg-white space-y-2">
                    <span className="text-sm font-medium">Inspection Checklist</span>
                    {["General cleanliness and hygiene", "Food storage temperatures", "Pest control measures", "Staff hygiene practices", "Waste disposal systems", "Water supply quality", "Sanitary facilities", "Documentation & licenses"].map((item, i) => (
                      <label key={i} className="flex items-center gap-2 text-xs">
                        <input type="checkbox" className="rounded" />
                        <span className="flex-1">{item}</span>
                        <select className="h-7 w-[100px] px-1 text-xs border border-gray-300 rounded">
                          <option value="">Score</option>
                          <option value="compliant">Compliant</option>
                          <option value="minor">Minor Issue</option>
                          <option value="major">Major Issue</option>
                          <option value="critical">Critical</option>
                          <option value="na">N/A</option>
                        </select>
                      </label>
                    ))}
                  </div>
                  <div className="grid grid-cols-2 gap-3">
                    <div>
                      <label className="text-xs font-medium text-gray-600">Overall Compliance Score (%)</label>
                      <input type="number" min={0} max={100} className="w-full h-8 px-2 text-xs border border-gray-300 rounded-lg mt-1" />
                    </div>
                    <div>
                      <label className="text-xs font-medium text-gray-600">Recommended Action</label>
                      <select className="w-full h-8 px-2 text-xs border border-gray-300 rounded-lg mt-1">
                        <option value="">Select</option>
                        <option value="pass">Pass - No Action</option>
                        <option value="warning">Warning Letter</option>
                        <option value="improvement">Improvement Notice</option>
                        <option value="closure">Closure Notice</option>
                        <option value="prosecution">Recommend Prosecution</option>
                      </select>
                    </div>
                  </div>
                  <div>
                    <label className="text-xs font-medium text-gray-600">Findings & Observations</label>
                    <textarea placeholder="Detailed findings..." className="w-full text-xs border border-gray-300 rounded-lg mt-1 p-2 min-h-[60px]" />
                  </div>
                  <div className="flex gap-2">
                    <button className="px-3 py-1.5 bg-blue-600 text-white text-xs font-medium rounded-lg hover:bg-blue-700">Complete Inspection</button>
                    <button className="px-3 py-1.5 bg-gray-100 text-gray-700 text-xs font-medium rounded-lg">Save Draft</button>
                    <button className="px-3 py-1.5 bg-gray-100 text-gray-700 text-xs font-medium rounded-lg">Schedule Follow-up</button>
                    <button className="px-3 py-1.5 bg-gray-100 text-gray-700 text-xs font-medium rounded-lg">Issue Enforcement</button>
                  </div>
                </div>
              );
            })()}
          </div>
        </div>
      )}

      {/* Enforcement Actions */}
      {activeSubTab === "enforcement" && (
        <div className="bg-white rounded-lg border border-gray-200">
          <div className="px-4 py-3 border-b flex items-center justify-between">
            <h4 className="text-sm font-semibold text-gray-900">Enforcement Actions</h4>
            <button onClick={() => setShowEnforcementForm(!showEnforcementForm)}
              className="inline-flex items-center gap-1 px-3 py-1.5 bg-blue-600 text-white text-xs font-medium rounded-lg hover:bg-blue-700">
              <Plus className="h-3.5 w-3.5" /> Issue Notice
            </button>
          </div>
          <div className="p-4 space-y-4">
            {showEnforcementForm && (
              <div className="p-4 border border-red-200 bg-red-50 rounded-lg">
                <h4 className="font-semibold text-sm mb-3">Issue Enforcement Notice</h4>
                <div className="grid grid-cols-3 gap-3">
                  <div>
                    <label className="text-xs font-medium text-gray-600">Site / Premises</label>
                    <input placeholder="Search site..." className="w-full h-8 px-2 text-xs border border-gray-300 rounded-lg mt-1" />
                  </div>
                  <div>
                    <label className="text-xs font-medium text-gray-600">Notice Type</label>
                    <select className="w-full h-8 px-2 text-xs border border-gray-300 rounded-lg mt-1">
                      <option value="">Select</option>
                      <option value="warning">Warning Letter</option>
                      <option value="improvement">Improvement Notice</option>
                      <option value="prohibition">Prohibition Notice</option>
                      <option value="closure">Closure Notice</option>
                      <option value="fine">Fixed Penalty / Fine</option>
                    </select>
                  </div>
                  <div>
                    <label className="text-xs font-medium text-gray-600">Compliance Deadline</label>
                    <input type="date" className="w-full h-8 px-2 text-xs border border-gray-300 rounded-lg mt-1" />
                  </div>
                  <div className="col-span-2">
                    <label className="text-xs font-medium text-gray-600">Violation Description</label>
                    <textarea placeholder="Describe the violation..." className="w-full text-xs border border-gray-300 rounded-lg mt-1 p-2 min-h-[40px]" />
                  </div>
                  <div>
                    <label className="text-xs font-medium text-gray-600">Fine Amount (if applicable)</label>
                    <input placeholder="$0.00" className="w-full h-8 px-2 text-xs border border-gray-300 rounded-lg mt-1" />
                  </div>
                </div>
                <div className="flex gap-2 mt-3">
                  <button className="px-3 py-1.5 bg-red-600 text-white text-xs font-medium rounded-lg hover:bg-red-700">Issue Notice</button>
                  <button onClick={() => setShowEnforcementForm(false)} className="px-3 py-1.5 bg-gray-100 text-gray-700 text-xs font-medium rounded-lg">Cancel</button>
                </div>
              </div>
            )}

            <div className="overflow-x-auto">
              <table className="w-full text-xs">
                <thead>
                  <tr className="border-b bg-gray-50">
                    <th className="text-left px-3 py-2 font-medium text-gray-600">ID</th>
                    <th className="text-left px-3 py-2 font-medium text-gray-600">Site</th>
                    <th className="text-left px-3 py-2 font-medium text-gray-600">Violation</th>
                    <th className="text-left px-3 py-2 font-medium text-gray-600">Severity</th>
                    <th className="text-left px-3 py-2 font-medium text-gray-600">Action</th>
                    <th className="text-left px-3 py-2 font-medium text-gray-600">Deadline</th>
                    <th className="text-left px-3 py-2 font-medium text-gray-600">Status</th>
                    <th className="text-left px-3 py-2 font-medium text-gray-600"></th>
                  </tr>
                </thead>
                <tbody>
                  {ENFORCEMENT_ACTIONS.map(e => (
                    <tr key={e.id} className="border-b hover:bg-gray-50">
                      <td className="px-3 py-2 font-mono">{e.id}</td>
                      <td className="px-3 py-2 font-medium text-gray-900">{e.site}</td>
                      <td className="px-3 py-2 max-w-[200px] truncate">{e.violation}</td>
                      <td className="px-3 py-2">
                        <span className={`px-2 py-0.5 rounded-full text-[10px] font-medium ${
                          e.severity === "critical" ? "bg-red-100 text-red-700" : "bg-amber-100 text-amber-700"
                        }`}>{e.severity}</span>
                      </td>
                      <td className="px-3 py-2">{e.action}</td>
                      <td className="px-3 py-2 text-gray-500">{e.deadline}</td>
                      <td className="px-3 py-2">
                        <span className={`px-2 py-0.5 rounded-full text-[10px] font-medium ${
                          e.status === "complied" ? "bg-green-100 text-green-700" : "bg-amber-100 text-amber-700"
                        }`}>{e.status.replace(/_/g, " ")}</span>
                      </td>
                      <td className="px-3 py-2">
                        <div className="flex gap-1">
                          <button className="px-2 py-1 border border-gray-300 rounded text-[10px] font-medium hover:bg-gray-50">Mark Complied</button>
                          <button className="px-2 py-1 text-gray-500 text-[10px] font-medium hover:text-gray-700">Escalate</button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      )}

      {/* Compliance Overview */}
      {activeSubTab === "compliance" && (
        <div className="bg-white rounded-lg border border-gray-200 p-5">
          <h4 className="text-sm font-semibold text-gray-900 mb-4">Compliance Overview by Category</h4>
          <div className="space-y-3">
            {[
              { category: "Food Premises", total: 342, compliant: 289, rate: 84 },
              { category: "Water Points", total: 156, compliant: 142, rate: 91 },
              { category: "Markets", total: 28, compliant: 19, rate: 68 },
              { category: "Abattoirs", total: 12, compliant: 8, rate: 67 },
              { category: "Schools", total: 89, compliant: 78, rate: 88 },
              { category: "Public Facilities", total: 45, compliant: 38, rate: 84 },
            ].map((c, i) => (
              <div key={i} className="flex items-center gap-4">
                <span className="text-sm font-medium w-36">{c.category}</span>
                <div className="flex-1 bg-gray-200 rounded-full h-3">
                  <div className={`h-3 rounded-full ${c.rate >= 80 ? "bg-green-500" : c.rate >= 60 ? "bg-amber-500" : "bg-red-500"}`} style={{ width: `${c.rate}%` }} />
                </div>
                <span className={`text-sm font-bold w-12 text-right ${c.rate >= 80 ? "text-green-700" : c.rate >= 60 ? "text-amber-700" : "text-red-700"}`}>{c.rate}%</span>
                <span className="text-xs text-gray-500 w-24">{c.compliant}/{c.total} compliant</span>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}

"use client";

import { useState } from "react";
import { AlertTriangle, Plus } from "lucide-react";

const COMPLAINTS = [
  { id: "CMP-001", type: "Noise Nuisance", category: "Environmental", location: "Borrowdale", status: "investigating", reported: "2026-04-08", reporter: "Citizen", priority: "medium", assignedTo: "T. Moyo", daysOpen: 2 },
  { id: "CMP-002", type: "Illegal Dumping", category: "Waste", location: "Highfield", status: "enforcement_pending", reported: "2026-04-05", reporter: "Ward Councillor", priority: "high", assignedTo: "J. Moyo", daysOpen: 5 },
  { id: "CMP-003", type: "Expired Food Products", category: "Food Safety", location: "CBD Shop", status: "resolved", reported: "2026-04-01", reporter: "Anonymous", priority: "high", assignedTo: "S. Makoni", daysOpen: 0 },
  { id: "CMP-004", type: "Water Contamination", category: "Water", location: "Glen Norah Borehole", status: "investigating", reported: "2026-04-09", reporter: "Community Leader", priority: "critical", assignedTo: "R. Dube", daysOpen: 1 },
  { id: "CMP-005", type: "Stray Animals", category: "Animal Control", location: "Mbare", status: "assigned", reported: "2026-04-06", reporter: "Citizen", priority: "low", assignedTo: "P. Chirwa", daysOpen: 4 },
  { id: "CMP-006", type: "Sewage Overflow", category: "Sanitation", location: "Chitungwiza Unit L", status: "investigating", reported: "2026-04-07", reporter: "Citizen", priority: "critical", assignedTo: "T. Ndlovu", daysOpen: 3 },
  { id: "CMP-007", type: "Unlicensed Food Vendor", category: "Food Safety", location: "Harare CBD", status: "assigned", reported: "2026-04-09", reporter: "EHO", priority: "medium", assignedTo: "J. Moyo", daysOpen: 1 },
];

export function ComplaintsTab() {
  const [statusFilter, setStatusFilter] = useState("all");
  const [showLogForm, setShowLogForm] = useState(false);
  const [selectedComplaint, setSelectedComplaint] = useState<string | null>(null);

  const filtered = COMPLAINTS.filter(c => statusFilter === "all" || c.status === statusFilter);

  return (
    <div className="space-y-4">
      {/* KPIs */}
      <div className="grid grid-cols-5 gap-3">
        {[
          { label: "Open Complaints", value: "18", color: "text-amber-700" },
          { label: "Critical Priority", value: "3", color: "text-red-700" },
          { label: "Avg Resolution (days)", value: "4.2", color: "text-blue-700" },
          { label: "Resolved This Month", value: "34", color: "text-green-700" },
          { label: "Satisfaction Score", value: "4.1/5", color: "text-sky-700" },
        ].map((kpi, i) => (
          <div key={i} className="bg-white rounded-lg border border-gray-200 p-3 text-center">
            <p className={`text-2xl font-bold ${kpi.color}`}>{kpi.value}</p>
            <p className="text-xs font-medium text-gray-900">{kpi.label}</p>
          </div>
        ))}
      </div>

      {/* Complaints Table */}
      <div className="bg-white rounded-lg border border-gray-200">
        <div className="px-4 py-3 border-b flex items-center justify-between">
          <div>
            <h4 className="text-sm font-semibold text-gray-900 flex items-center gap-2">
              <AlertTriangle className="h-4 w-4" /> Complaints, Alerts & Nuisance Management
            </h4>
            <p className="text-xs text-gray-500">Intake, investigation, enforcement, and resolution tracking</p>
          </div>
          <div className="flex gap-2">
            <select value={statusFilter} onChange={(e) => setStatusFilter(e.target.value)}
              className="h-8 px-2 text-xs border border-gray-300 rounded-lg">
              <option value="all">All Status</option>
              <option value="assigned">Assigned</option>
              <option value="investigating">Investigating</option>
              <option value="enforcement_pending">Enforcement</option>
              <option value="resolved">Resolved</option>
            </select>
            <button onClick={() => setShowLogForm(!showLogForm)}
              className="inline-flex items-center gap-1 px-3 py-1.5 bg-blue-600 text-white text-xs font-medium rounded-lg hover:bg-blue-700">
              <Plus className="h-3.5 w-3.5" /> Log Complaint
            </button>
          </div>
        </div>

        <div className="p-4 space-y-4">
          {showLogForm && (
            <div className="p-4 border border-blue-200 bg-blue-50 rounded-lg">
              <h4 className="font-semibold text-sm mb-3">Log New Complaint / Alert</h4>
              <div className="grid grid-cols-3 gap-3">
                <div>
                  <label className="text-xs font-medium text-gray-600">Category</label>
                  <select className="w-full h-8 px-2 text-xs border border-gray-300 rounded-lg mt-1">
                    <option value="">Select</option>
                    <option value="environmental">Environmental</option>
                    <option value="food_safety">Food Safety</option>
                    <option value="water">Water Quality</option>
                    <option value="sanitation">Sanitation</option>
                    <option value="animal">Animal Control</option>
                    <option value="waste">Waste Management</option>
                    <option value="noise">Noise Nuisance</option>
                    <option value="disease">Disease / Health Alert</option>
                  </select>
                </div>
                <div>
                  <label className="text-xs font-medium text-gray-600">Complaint Type</label>
                  <input placeholder="e.g. Sewage Overflow" className="w-full h-8 px-2 text-xs border border-gray-300 rounded-lg mt-1" />
                </div>
                <div>
                  <label className="text-xs font-medium text-gray-600">Location</label>
                  <input placeholder="Area, ward, address" className="w-full h-8 px-2 text-xs border border-gray-300 rounded-lg mt-1" />
                </div>
                <div>
                  <label className="text-xs font-medium text-gray-600">Reporter</label>
                  <select className="w-full h-8 px-2 text-xs border border-gray-300 rounded-lg mt-1">
                    <option value="">Source</option>
                    <option value="citizen">Citizen</option>
                    <option value="councillor">Ward Councillor</option>
                    <option value="community">Community Leader</option>
                    <option value="eho">EHO (Internal)</option>
                    <option value="portal">Citizen Portal</option>
                    <option value="anonymous">Anonymous</option>
                  </select>
                </div>
                <div>
                  <label className="text-xs font-medium text-gray-600">Priority</label>
                  <select className="w-full h-8 px-2 text-xs border border-gray-300 rounded-lg mt-1">
                    <option value="">Assess</option>
                    <option value="low">Low</option>
                    <option value="medium">Medium</option>
                    <option value="high">High</option>
                    <option value="critical">Critical</option>
                  </select>
                </div>
                <div>
                  <label className="text-xs font-medium text-gray-600">Assign To</label>
                  <select className="w-full h-8 px-2 text-xs border border-gray-300 rounded-lg mt-1">
                    <option value="">Select officer</option>
                    <option value="tmoyo">T. Moyo</option>
                    <option value="jmoyo">J. Moyo</option>
                    <option value="rdube">R. Dube</option>
                    <option value="tndlovu">T. Ndlovu</option>
                    <option value="pchirwa">P. Chirwa</option>
                  </select>
                </div>
              </div>
              <div className="mt-3">
                <label className="text-xs font-medium text-gray-600">Description</label>
                <textarea placeholder="Detailed description of the complaint..." className="w-full text-xs border border-gray-300 rounded-lg mt-1 p-2 min-h-[60px]" />
              </div>
              <div className="flex gap-2 mt-3">
                <button className="px-3 py-1.5 bg-blue-600 text-white text-xs font-medium rounded-lg hover:bg-blue-700">Log Complaint</button>
                <button onClick={() => setShowLogForm(false)} className="px-3 py-1.5 bg-gray-100 text-gray-700 text-xs font-medium rounded-lg">Cancel</button>
              </div>
            </div>
          )}

          <div className="overflow-x-auto">
            <table className="w-full text-xs">
              <thead>
                <tr className="border-b bg-gray-50">
                  <th className="text-left px-3 py-2 font-medium text-gray-600">ID</th>
                  <th className="text-left px-3 py-2 font-medium text-gray-600">Type</th>
                  <th className="text-left px-3 py-2 font-medium text-gray-600">Category</th>
                  <th className="text-left px-3 py-2 font-medium text-gray-600">Location</th>
                  <th className="text-left px-3 py-2 font-medium text-gray-600">Priority</th>
                  <th className="text-left px-3 py-2 font-medium text-gray-600">Status</th>
                  <th className="text-left px-3 py-2 font-medium text-gray-600">Assigned To</th>
                  <th className="text-left px-3 py-2 font-medium text-gray-600">Days Open</th>
                  <th className="text-left px-3 py-2 font-medium text-gray-600"></th>
                </tr>
              </thead>
              <tbody>
                {filtered.map(c => (
                  <tr key={c.id} className={`border-b hover:bg-gray-50 ${selectedComplaint === c.id ? "bg-blue-50" : ""}`}>
                    <td className="px-3 py-2 font-mono">{c.id}</td>
                    <td className="px-3 py-2 font-medium text-gray-900">{c.type}</td>
                    <td className="px-3 py-2">
                      <span className="px-2 py-0.5 border border-gray-300 rounded text-[10px]">{c.category}</span>
                    </td>
                    <td className="px-3 py-2">{c.location}</td>
                    <td className="px-3 py-2">
                      <span className={`px-2 py-0.5 rounded-full text-[10px] font-medium ${
                        c.priority === "critical" ? "bg-red-100 text-red-700" :
                        c.priority === "high" ? "bg-amber-100 text-amber-700" :
                        c.priority === "low" ? "bg-gray-100 text-gray-600" : "bg-blue-100 text-blue-700"
                      }`}>{c.priority}</span>
                    </td>
                    <td className="px-3 py-2">
                      <span className="px-2 py-0.5 border border-gray-300 rounded text-[10px] capitalize">{c.status.replace(/_/g, " ")}</span>
                    </td>
                    <td className="px-3 py-2">{c.assignedTo}</td>
                    <td className={`px-3 py-2 ${c.daysOpen > 3 ? "text-amber-700 font-bold" : ""}`}>{c.daysOpen}</td>
                    <td className="px-3 py-2">
                      <button onClick={() => setSelectedComplaint(selectedComplaint === c.id ? null : c.id)}
                        className="px-2 py-1 border border-gray-300 rounded text-[10px] font-medium hover:bg-gray-50">
                        {selectedComplaint === c.id ? "Close" : "Act"}
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {/* Expanded Action Panel */}
          {selectedComplaint && (() => {
            const c = COMPLAINTS.find(comp => comp.id === selectedComplaint);
            if (!c) return null;
            return (
              <div className="p-3 bg-gray-50 rounded-lg border border-gray-200 space-y-3">
                <h4 className="text-sm font-semibold">Action on Complaint - {c.type}</h4>
                <div className="grid grid-cols-3 gap-3">
                  <div>
                    <label className="text-xs font-medium text-gray-600">Action</label>
                    <select className="w-full h-8 px-2 text-xs border border-gray-300 rounded-lg mt-1">
                      <option value="">Select action</option>
                      <option value="investigate">Start Investigation</option>
                      <option value="site_visit">Schedule Site Visit</option>
                      <option value="enforce">Issue Enforcement Notice</option>
                      <option value="reassign">Reassign Officer</option>
                      <option value="escalate">Escalate Priority</option>
                      <option value="resolve">Mark Resolved</option>
                      <option value="close">Close (No Action)</option>
                    </select>
                  </div>
                  <div>
                    <label className="text-xs font-medium text-gray-600">Update Status</label>
                    <select className="w-full h-8 px-2 text-xs border border-gray-300 rounded-lg mt-1">
                      <option value="">New status</option>
                      <option value="assigned">Assigned</option>
                      <option value="investigating">Investigating</option>
                      <option value="enforcement_pending">Enforcement Pending</option>
                      <option value="awaiting_compliance">Awaiting Compliance</option>
                      <option value="resolved">Resolved</option>
                    </select>
                  </div>
                  <div>
                    <label className="text-xs font-medium text-gray-600">Site Visit Date</label>
                    <input type="date" className="w-full h-8 px-2 text-xs border border-gray-300 rounded-lg mt-1" />
                  </div>
                </div>
                <div>
                  <label className="text-xs font-medium text-gray-600">Action Notes / Findings</label>
                  <textarea placeholder="Document actions taken, findings, evidence..." className="w-full text-xs border border-gray-300 rounded-lg mt-1 p-2 min-h-[60px]" />
                </div>
                <div className="flex gap-2">
                  <button className="px-3 py-1.5 bg-blue-600 text-white text-xs font-medium rounded-lg hover:bg-blue-700">Save & Update</button>
                  <button className="px-3 py-1.5 bg-gray-100 text-gray-700 text-xs font-medium rounded-lg">Link to Inspection</button>
                  <button className="px-3 py-1.5 bg-gray-100 text-gray-700 text-xs font-medium rounded-lg">Notify Reporter</button>
                </div>
              </div>
            );
          })()}
        </div>
      </div>
    </div>
  );
}

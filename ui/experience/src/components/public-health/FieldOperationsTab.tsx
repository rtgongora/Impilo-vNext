"use client";

import { useState } from "react";
import { Users, ClipboardList, Target, Navigation, Radio, MapPin, Plus } from "lucide-react";

const FIELD_TEAMS = [
  { id: "FT-01", name: "Harare South Response Team", lead: "J. Moyo", members: 6, status: "deployed", location: "Budiriro", activeSince: "2026-04-02", tasksComplete: 12, tasksPending: 4 },
  { id: "FT-02", name: "Chitungwiza Investigation Team", lead: "T. Ndlovu", members: 4, status: "deployed", location: "Chitungwiza", activeSince: "2026-04-04", tasksComplete: 5, tasksPending: 8 },
  { id: "FT-03", name: "Manicaland Vector Control Unit", lead: "P. Chirwa", members: 8, status: "deployed", location: "Chipinge", activeSince: "2026-03-28", tasksComplete: 45, tasksPending: 15 },
  { id: "FT-04", name: "Masvingo Measles Response", lead: "S. Makoni", members: 5, status: "standby", location: "Base", activeSince: null, tasksComplete: 0, tasksPending: 0 },
  { id: "FT-05", name: "Port Health Inspection Team", lead: "R. Dube", members: 3, status: "deployed", location: "Beitbridge", activeSince: "2026-04-01", tasksComplete: 22, tasksPending: 6 },
];

const FIELD_TASKS = [
  { id: "TSK-001", task: "Contact tracing - Cholera cluster", team: "FT-01", priority: "critical", status: "in_progress", due: "2026-04-06", contacts: 23, traced: 18 },
  { id: "TSK-002", task: "Water source sampling - Budiriro", team: "FT-01", priority: "high", status: "in_progress", due: "2026-04-06", contacts: null, traced: null },
  { id: "TSK-003", task: "Household decontamination", team: "FT-01", priority: "high", status: "pending", due: "2026-04-07", contacts: null, traced: null },
  { id: "TSK-004", task: "Case investigation - Typhoid", team: "FT-02", priority: "high", status: "in_progress", due: "2026-04-06", contacts: 8, traced: 3 },
  { id: "TSK-005", task: "IRS - Ward 12 Chipinge", team: "FT-03", priority: "medium", status: "in_progress", due: "2026-04-10", contacts: null, traced: null },
  { id: "TSK-006", task: "Post-campaign mop-up vaccination", team: "FT-03", priority: "medium", status: "pending", due: "2026-04-12", contacts: null, traced: null },
  { id: "TSK-007", task: "Traveller screening - Beitbridge", team: "FT-05", priority: "medium", status: "in_progress", due: "Ongoing", contacts: null, traced: null },
];

const GPS_CHECKINS = [
  { team: "FT-01", member: "J. Moyo", time: "08:15", location: "Budiriro 5, -17.8341, 30.9671", activity: "Contact tracing" },
  { team: "FT-01", member: "A. Chikwanha", time: "08:22", location: "Budiriro 5, -17.8345, 30.9668", activity: "Water sampling" },
  { team: "FT-02", member: "T. Ndlovu", time: "09:01", location: "Zengeza 4, -18.0124, 31.0755", activity: "Case investigation" },
  { team: "FT-03", member: "P. Chirwa", time: "07:45", location: "Chipinge Ward 12, -20.1876, 32.6234", activity: "IRS spraying" },
  { team: "FT-05", member: "R. Dube", time: "06:30", location: "Beitbridge Border, -22.2175, 30.0001", activity: "Traveller screening" },
];

export function FieldOperationsTab() {
  const [activeSubTab, setActiveSubTab] = useState<"teams" | "tasks" | "tracking">("teams");

  return (
    <div className="space-y-4">
      {/* KPIs */}
      <div className="grid grid-cols-5 gap-3">
        {[
          { label: "Teams Deployed", value: "4/5", Icon: Users },
          { label: "Active Tasks", value: "7", Icon: ClipboardList },
          { label: "Contacts Traced Today", value: "21", Icon: Target },
          { label: "GPS Check-ins Today", value: "38", Icon: Navigation },
          { label: "Data Forms Submitted", value: "14", Icon: Radio },
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
          { key: "teams" as const, label: "Field Teams" },
          { key: "tasks" as const, label: "Task Board" },
          { key: "tracking" as const, label: "GPS Tracking" },
        ].map((tab) => (
          <button key={tab.key} onClick={() => setActiveSubTab(tab.key)}
            className={`px-3 py-2 text-sm font-medium border-b-2 transition-colors ${
              activeSubTab === tab.key ? "border-amber-600 text-amber-600" : "border-transparent text-gray-500 hover:text-gray-700"
            }`}>
            {tab.label}
          </button>
        ))}
      </div>

      {/* Field Teams */}
      {activeSubTab === "teams" && (
        <div className="bg-white rounded-lg border border-gray-200">
          <div className="px-4 py-3 border-b flex items-center justify-between">
            <h4 className="text-sm font-semibold text-gray-900">Field Team Roster & Deployment</h4>
            <button className="inline-flex items-center gap-1 px-3 py-1.5 bg-blue-600 text-white text-xs font-medium rounded-lg hover:bg-blue-700">
              <Plus className="h-3.5 w-3.5" /> Deploy Team
            </button>
          </div>
          <div className="overflow-x-auto">
            <table className="w-full text-xs">
              <thead>
                <tr className="border-b bg-gray-50">
                  <th className="text-left px-3 py-2 font-medium text-gray-600">Team ID</th>
                  <th className="text-left px-3 py-2 font-medium text-gray-600">Team Name</th>
                  <th className="text-left px-3 py-2 font-medium text-gray-600">Lead</th>
                  <th className="text-left px-3 py-2 font-medium text-gray-600">Members</th>
                  <th className="text-left px-3 py-2 font-medium text-gray-600">Status</th>
                  <th className="text-left px-3 py-2 font-medium text-gray-600">Location</th>
                  <th className="text-left px-3 py-2 font-medium text-gray-600">Progress</th>
                  <th className="text-left px-3 py-2 font-medium text-gray-600"></th>
                </tr>
              </thead>
              <tbody>
                {FIELD_TEAMS.map(t => {
                  const total = t.tasksComplete + t.tasksPending;
                  const pct = total > 0 ? Math.round((t.tasksComplete / total) * 100) : 0;
                  return (
                    <tr key={t.id} className="border-b hover:bg-gray-50">
                      <td className="px-3 py-2 font-mono">{t.id}</td>
                      <td className="px-3 py-2 font-medium text-gray-900">{t.name}</td>
                      <td className="px-3 py-2">{t.lead}</td>
                      <td className="px-3 py-2">{t.members}</td>
                      <td className="px-3 py-2">
                        <span className={`px-2 py-0.5 rounded-full text-[10px] font-medium ${
                          t.status === "deployed" ? "bg-green-100 text-green-700" : "bg-gray-100 text-gray-600"
                        }`}>{t.status}</span>
                      </td>
                      <td className="px-3 py-2 flex items-center gap-1">
                        <MapPin className="h-3 w-3 text-gray-400" />{t.location}
                      </td>
                      <td className="px-3 py-2">
                        {t.status === "deployed" ? (
                          <div className="flex items-center gap-2">
                            <div className="w-16 bg-gray-200 rounded-full h-2">
                              <div className="h-2 rounded-full bg-blue-500" style={{ width: `${pct}%` }} />
                            </div>
                            <span className="text-xs text-gray-500">{t.tasksComplete}/{total}</span>
                          </div>
                        ) : "-"}
                      </td>
                      <td className="px-3 py-2">
                        <button className="px-2 py-1 border border-gray-300 rounded text-[10px] font-medium hover:bg-gray-50">Manage</button>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* Task Board */}
      {activeSubTab === "tasks" && (
        <div className="bg-white rounded-lg border border-gray-200">
          <div className="px-4 py-3 border-b flex items-center justify-between">
            <h4 className="text-sm font-semibold text-gray-900">Field Task Assignment Board</h4>
            <button className="inline-flex items-center gap-1 px-3 py-1.5 bg-blue-600 text-white text-xs font-medium rounded-lg hover:bg-blue-700">
              <Plus className="h-3.5 w-3.5" /> Assign Task
            </button>
          </div>
          <div className="overflow-x-auto">
            <table className="w-full text-xs">
              <thead>
                <tr className="border-b bg-gray-50">
                  <th className="text-left px-3 py-2 font-medium text-gray-600">Task ID</th>
                  <th className="text-left px-3 py-2 font-medium text-gray-600">Task</th>
                  <th className="text-left px-3 py-2 font-medium text-gray-600">Team</th>
                  <th className="text-left px-3 py-2 font-medium text-gray-600">Priority</th>
                  <th className="text-left px-3 py-2 font-medium text-gray-600">Status</th>
                  <th className="text-left px-3 py-2 font-medium text-gray-600">Due</th>
                  <th className="text-left px-3 py-2 font-medium text-gray-600">Progress</th>
                  <th className="text-left px-3 py-2 font-medium text-gray-600"></th>
                </tr>
              </thead>
              <tbody>
                {FIELD_TASKS.map(t => (
                  <tr key={t.id} className="border-b hover:bg-gray-50">
                    <td className="px-3 py-2 font-mono">{t.id}</td>
                    <td className="px-3 py-2 font-medium text-gray-900 max-w-[200px] truncate">{t.task}</td>
                    <td className="px-3 py-2">{t.team}</td>
                    <td className="px-3 py-2">
                      <span className={`px-2 py-0.5 rounded-full text-[10px] font-medium ${
                        t.priority === "critical" ? "bg-red-100 text-red-700" :
                        t.priority === "high" ? "bg-amber-100 text-amber-700" : "bg-gray-100 text-gray-600"
                      }`}>{t.priority}</span>
                    </td>
                    <td className="px-3 py-2">
                      <span className="px-2 py-0.5 border border-gray-300 rounded text-[10px] capitalize">{t.status.replace(/_/g, " ")}</span>
                    </td>
                    <td className="px-3 py-2 text-gray-500">{t.due}</td>
                    <td className="px-3 py-2">
                      {t.contacts !== null ? (
                        <span className="text-xs">{t.traced}/{t.contacts} traced</span>
                      ) : "-"}
                    </td>
                    <td className="px-3 py-2">
                      <button className="px-2 py-1 text-gray-500 text-[10px] font-medium hover:text-gray-700">Update</button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* GPS Tracking */}
      {activeSubTab === "tracking" && (
        <div className="bg-white rounded-lg border border-gray-200">
          <div className="px-4 py-3 border-b">
            <h4 className="text-sm font-semibold text-gray-900 flex items-center gap-2">
              <Navigation className="h-4 w-4" /> GPS Check-in Log
            </h4>
            <p className="text-xs text-gray-500">Real-time field worker location and activity tracking</p>
          </div>
          <div className="overflow-x-auto">
            <table className="w-full text-xs">
              <thead>
                <tr className="border-b bg-gray-50">
                  <th className="text-left px-3 py-2 font-medium text-gray-600">Team</th>
                  <th className="text-left px-3 py-2 font-medium text-gray-600">Member</th>
                  <th className="text-left px-3 py-2 font-medium text-gray-600">Time</th>
                  <th className="text-left px-3 py-2 font-medium text-gray-600">Location</th>
                  <th className="text-left px-3 py-2 font-medium text-gray-600">Activity</th>
                </tr>
              </thead>
              <tbody>
                {GPS_CHECKINS.map((c, i) => (
                  <tr key={i} className="border-b hover:bg-gray-50">
                    <td className="px-3 py-2 font-mono">{c.team}</td>
                    <td className="px-3 py-2 font-medium text-gray-900">{c.member}</td>
                    <td className="px-3 py-2 text-gray-500">{c.time}</td>
                    <td className="px-3 py-2 font-mono text-gray-600">{c.location}</td>
                    <td className="px-3 py-2">
                      <span className="px-2 py-0.5 border border-gray-300 rounded text-[10px]">{c.activity}</span>
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

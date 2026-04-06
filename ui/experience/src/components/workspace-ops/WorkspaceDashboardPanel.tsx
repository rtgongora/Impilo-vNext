"use client";
import { Users, Clock, Star, AlertTriangle, TrendingUp, Activity } from "lucide-react";
const KPIs = [
  { label: "Patients Today", value: "47", delta: "+12%", icon: Users, color: "text-blue-600" },
  { label: "Avg Wait Time", value: "23 min", delta: "-8%", icon: Clock, color: "text-amber-600" },
  { label: "Satisfaction", value: "4.6/5", delta: "+0.3", icon: Star, color: "text-green-600" },
  { label: "Staff On Duty", value: "14", delta: "0", icon: Activity, color: "text-purple-600" },
];
const ALERTS = [
  { id: 1, severity: "high", message: "Paracetamol stock below reorder level", time: "10 min ago" },
  { id: 2, severity: "medium", message: "Dr. Moyo shift ends in 30 minutes — no replacement assigned", time: "25 min ago" },
  { id: 3, severity: "low", message: "Monthly quality report due in 3 days", time: "1 hr ago" },
];
export function WorkspaceDashboardPanel() {
  return (
    <div className="space-y-4">
      <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
        {KPIs.map(k => (
          <div key={k.label} className="bg-white rounded-lg border p-4">
            <div className="flex items-center justify-between mb-2"><k.icon className={`w-5 h-5 ${k.color}`} /><span className="text-xs text-green-600 font-medium">{k.delta}</span></div>
            <p className="text-2xl font-bold text-gray-900">{k.value}</p><p className="text-xs text-gray-500">{k.label}</p>
          </div>
        ))}
      </div>
      <div className="bg-white rounded-lg border p-4">
        <h3 className="text-sm font-semibold text-gray-900 mb-3 flex items-center gap-2"><AlertTriangle className="w-4 h-4 text-amber-500" />Active Alerts</h3>
        <div className="space-y-2">{ALERTS.map(a => (
          <div key={a.id} className={`px-3 py-2 rounded-lg flex items-center justify-between ${a.severity === "high" ? "bg-red-50 border border-red-200" : a.severity === "medium" ? "bg-amber-50 border border-amber-200" : "bg-gray-50 border border-gray-200"}`}>
            <span className="text-sm text-gray-700">{a.message}</span><span className="text-xs text-gray-500">{a.time}</span>
          </div>
        ))}</div>
      </div>
    </div>
  );
}

"use client";
import { Receipt, TrendingUp, AlertCircle, CheckCircle2, Clock } from "lucide-react";
const REVENUE = [
  { label: "Today", value: "$2,340", delta: "+15%" },
  { label: "This Month", value: "$48,720", delta: "+8%" },
  { label: "Outstanding", value: "$12,450", delta: "-3%" },
];
const CLAIMS = [
  { status: "Submitted", count: 23, color: "bg-blue-100 text-blue-700" },
  { status: "Adjudicated", count: 15, color: "bg-amber-100 text-amber-700" },
  { status: "Paid", count: 42, color: "bg-green-100 text-green-700" },
  { status: "Rejected", count: 4, color: "bg-red-100 text-red-700" },
];
const CHARGES = [
  { id: 1, patient: "J. Moyo", service: "OPD Consultation", amount: "$15.00", date: "Today 09:15", status: "Billed" },
  { id: 2, patient: "M. Ndlovu", service: "Lab — FBC", amount: "$8.50", date: "Today 10:30", status: "Billed" },
  { id: 3, patient: "T. Chirwa", service: "X-Ray Chest", amount: "$25.00", date: "Today 11:00", status: "Pending" },
  { id: 4, patient: "S. Dube", service: "Pharmacy Dispense", amount: "$12.00", date: "Today 11:45", status: "Billed" },
  { id: 5, patient: "R. Zhou", service: "Minor Procedure", amount: "$45.00", date: "Today 14:00", status: "Pending" },
];
export function BillingPanel() {
  return (
    <div className="space-y-4">
      <div className="grid grid-cols-3 gap-3">{REVENUE.map(r => (
        <div key={r.label} className="bg-white rounded-lg border p-4"><p className="text-xs text-gray-500">{r.label}</p><p className="text-xl font-bold text-gray-900 mt-1">{r.value}</p><p className="text-xs text-green-600 mt-0.5">{r.delta}</p></div>
      ))}</div>
      <div className="bg-white rounded-lg border p-4">
        <h3 className="text-sm font-semibold text-gray-900 mb-3">Claims Status</h3>
        <div className="grid grid-cols-4 gap-2">{CLAIMS.map(c => (
          <div key={c.status} className="text-center"><p className="text-lg font-bold text-gray-900">{c.count}</p><span className={`px-2 py-0.5 rounded text-xs font-medium ${c.color}`}>{c.status}</span></div>
        ))}</div>
      </div>
      <div className="bg-white rounded-lg border overflow-hidden">
        <div className="px-4 py-2 border-b bg-gray-50"><h3 className="text-sm font-semibold text-gray-900">Recent Charges</h3></div>
        <table className="w-full text-sm">
          <thead><tr><th className="px-4 py-2 text-left text-xs font-medium text-gray-500">Patient</th><th className="px-4 py-2 text-left text-xs font-medium text-gray-500">Service</th><th className="px-4 py-2 text-right text-xs font-medium text-gray-500">Amount</th><th className="px-4 py-2 text-center text-xs font-medium text-gray-500">Status</th></tr></thead>
          <tbody className="divide-y divide-gray-100">{CHARGES.map(c => (
            <tr key={c.id} className="hover:bg-gray-50"><td className="px-4 py-2 font-medium text-gray-900">{c.patient}</td><td className="px-4 py-2 text-gray-500">{c.service}</td><td className="px-4 py-2 text-right font-medium">{c.amount}</td>
              <td className="px-4 py-2 text-center"><span className={`px-2 py-0.5 rounded text-xs font-medium ${c.status === "Billed" ? "bg-green-100 text-green-700" : "bg-amber-100 text-amber-700"}`}>{c.status}</span></td></tr>
          ))}</tbody>
        </table>
      </div>
    </div>
  );
}

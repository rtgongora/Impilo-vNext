"use client";
import { Users, Clock, Calendar, CheckCircle2 } from "lucide-react";
const STAFF = [
  { id: 1, name: "Dr. T. Moyo", role: "Medical Officer", shift: "Day (07:00-19:00)", status: "on-duty" },
  { id: 2, name: "Sr. N. Ndlovu", role: "Nurse Manager", shift: "Day (07:00-19:00)", status: "on-duty" },
  { id: 3, name: "N. Chirwa", role: "Staff Nurse", shift: "Day (07:00-19:00)", status: "on-duty" },
  { id: 4, name: "Dr. S. Sibanda", role: "Registrar", shift: "Night (19:00-07:00)", status: "off-duty" },
  { id: 5, name: "M. Dube", role: "Pharmacy Tech", shift: "Day (07:00-19:00)", status: "on-duty" },
  { id: 6, name: "T. Mpofu", role: "Lab Tech", shift: "Day (07:00-19:00)", status: "on-leave" },
  { id: 7, name: "P. Ncube", role: "Staff Nurse", shift: "Night (19:00-07:00)", status: "off-duty" },
  { id: 8, name: "R. Zhou", role: "CHW", shift: "Day (07:00-15:00)", status: "on-duty" },
];
const statusStyle: Record<string, string> = { "on-duty": "bg-green-100 text-green-700", "off-duty": "bg-gray-100 text-gray-600", "on-leave": "bg-amber-100 text-amber-700" };
export function HRShiftsPanel() {
  const onDuty = STAFF.filter(s => s.status === "on-duty").length;
  return (
    <div className="space-y-4">
      <div className="grid grid-cols-3 gap-3">
        <div className="bg-white rounded-lg border p-3 text-center"><Users className="w-5 h-5 text-green-600 mx-auto mb-1" /><p className="text-xl font-bold text-gray-900">{onDuty}</p><p className="text-xs text-gray-500">On Duty</p></div>
        <div className="bg-white rounded-lg border p-3 text-center"><Clock className="w-5 h-5 text-gray-500 mx-auto mb-1" /><p className="text-xl font-bold text-gray-900">{STAFF.filter(s => s.status === "off-duty").length}</p><p className="text-xs text-gray-500">Off Duty</p></div>
        <div className="bg-white rounded-lg border p-3 text-center"><Calendar className="w-5 h-5 text-amber-500 mx-auto mb-1" /><p className="text-xl font-bold text-gray-900">{STAFF.filter(s => s.status === "on-leave").length}</p><p className="text-xs text-gray-500">On Leave</p></div>
      </div>
      <div className="bg-white rounded-lg border overflow-hidden">
        <table className="w-full text-sm">
          <thead className="bg-gray-50"><tr><th className="px-4 py-2 text-left text-xs font-medium text-gray-500">Name</th><th className="px-4 py-2 text-left text-xs font-medium text-gray-500">Role</th><th className="px-4 py-2 text-left text-xs font-medium text-gray-500">Shift</th><th className="px-4 py-2 text-center text-xs font-medium text-gray-500">Status</th></tr></thead>
          <tbody className="divide-y divide-gray-100">{STAFF.map(s => (
            <tr key={s.id} className="hover:bg-gray-50"><td className="px-4 py-2.5 font-medium text-gray-900">{s.name}</td><td className="px-4 py-2.5 text-gray-500">{s.role}</td><td className="px-4 py-2.5 text-gray-500">{s.shift}</td>
              <td className="px-4 py-2.5 text-center"><span className={`px-2 py-0.5 rounded text-xs font-medium ${statusStyle[s.status]}`}>{s.status.replace("-", " ")}</span></td></tr>
          ))}</tbody>
        </table>
      </div>
    </div>
  );
}

"use client";
import { useState } from "react";
import { Bed, Users, Activity, AlertTriangle, Building2, ArrowRightLeft, User, Clock, ChevronRight, Stethoscope, LogOut, Heart, Eye, CheckCircle2, ChevronLeft } from "lucide-react";

interface BedData { id: string; bedNumber: string; wardId: string; status: "available" | "occupied" | "maintenance"; patient?: { id: string; name: string; mrn: string; diagnosis: string; attendingPhysician: string; acuityLevel: "critical" | "high" | "medium" | "low"; admittedAt: string; }; }
interface Ward { id: string; name: string; type: string; totalBeds: number; }

const WARDS: Ward[] = [
  { id: "med", name: "Medical Ward", type: "Medical", totalBeds: 20 },
  { id: "surg", name: "Surgical Ward", type: "Surgical", totalBeds: 16 },
  { id: "mat", name: "Maternity Ward", type: "Maternity", totalBeds: 12 },
  { id: "paed", name: "Paediatric Ward", type: "Paediatric", totalBeds: 10 },
  { id: "icu", name: "ICU", type: "Critical Care", totalBeds: 8 },
];

const MOCK_BEDS: BedData[] = WARDS.flatMap(w => Array.from({ length: w.totalBeds }, (_, i) => {
  const occupied = Math.random() > 0.35;
  const acuities: BedData["patient"]!["acuityLevel"][] = ["critical", "high", "medium", "low"];
  return {
    id: `${w.id}-${i + 1}`, bedNumber: `${w.id.toUpperCase()}-${String(i + 1).padStart(2, "0")}`, wardId: w.id,
    status: occupied ? "occupied" as const : Math.random() > 0.9 ? "maintenance" as const : "available" as const,
    ...(occupied ? { patient: { id: `pt-${w.id}-${i}`, name: `Patient ${w.id.toUpperCase()}${i + 1}`, mrn: `MRN-${1000 + i}`, diagnosis: ["Pneumonia", "Fracture", "Preeclampsia", "Bronchiolitis", "Sepsis"][Math.floor(Math.random() * 5)], attendingPhysician: ["Dr. Moyo", "Dr. Ndlovu", "Dr. Chirwa", "Dr. Sibanda"][Math.floor(Math.random() * 4)], acuityLevel: acuities[Math.floor(Math.random() * 4)], admittedAt: new Date(Date.now() - Math.random() * 7 * 86400000).toISOString() } } : {}),
  };
}));

const acuityColor = { critical: "bg-red-500", high: "bg-orange-500", medium: "bg-yellow-500", low: "bg-green-500" };
const statusColor = { available: "bg-green-100 border-green-300 text-green-700", occupied: "bg-blue-100 border-blue-300 text-blue-700", maintenance: "bg-gray-100 border-gray-300 text-gray-500" };

export function WardManagementPanel() {
  const [selectedWard, setSelectedWard] = useState<string | null>(null);
  const [selectedBed, setSelectedBed] = useState<BedData | null>(null);
  const [transferOpen, setTransferOpen] = useState(false);

  const totalBeds = MOCK_BEDS.length;
  const occupied = MOCK_BEDS.filter(b => b.status === "occupied").length;
  const available = MOCK_BEDS.filter(b => b.status === "available").length;
  const critical = MOCK_BEDS.filter(b => b.patient?.acuityLevel === "critical").length;
  const occupancy = Math.round((occupied / totalBeds) * 100);

  if (selectedBed?.patient) {
    const p = selectedBed.patient;
    return (
      <div className="space-y-4">
        <button onClick={() => setSelectedBed(null)} className="flex items-center gap-1 text-sm text-blue-600 hover:underline"><ChevronLeft className="w-4 h-4" /> Back</button>
        <div className="bg-white rounded-lg border p-4">
          <div className="flex items-center justify-between mb-4">
            <div><h3 className="text-base font-semibold text-gray-900">{p.name}</h3><p className="text-xs text-gray-500">{p.mrn} &middot; Bed {selectedBed.bedNumber}</p></div>
            <span className={`w-3 h-3 rounded-full ${acuityColor[p.acuityLevel]}`} />
          </div>
          <div className="grid grid-cols-2 gap-4 text-sm">
            <div><p className="text-xs text-gray-500">Diagnosis</p><p className="font-medium">{p.diagnosis}</p></div>
            <div><p className="text-xs text-gray-500">Attending</p><p className="font-medium">{p.attendingPhysician}</p></div>
            <div><p className="text-xs text-gray-500">Admitted</p><p className="font-medium">{new Date(p.admittedAt).toLocaleDateString()}</p></div>
            <div><p className="text-xs text-gray-500">Acuity</p><p className="font-medium capitalize">{p.acuityLevel}</p></div>
          </div>
          <div className="flex gap-2 mt-4">
            <button className="px-3 py-1.5 text-xs font-medium text-blue-600 border border-blue-200 rounded-lg hover:bg-blue-50"><Eye className="w-3 h-3 inline mr-1" />View Chart</button>
            <button onClick={() => setTransferOpen(true)} className="px-3 py-1.5 text-xs font-medium text-amber-600 border border-amber-200 rounded-lg hover:bg-amber-50"><ArrowRightLeft className="w-3 h-3 inline mr-1" />Transfer</button>
            <button className="px-3 py-1.5 text-xs font-medium text-green-600 border border-green-200 rounded-lg hover:bg-green-50"><LogOut className="w-3 h-3 inline mr-1" />Discharge</button>
          </div>
        </div>
      </div>
    );
  }

  if (selectedWard) {
    const ward = WARDS.find(w => w.id === selectedWard)!;
    const wardBeds = MOCK_BEDS.filter(b => b.wardId === selectedWard);
    const wardOccupied = wardBeds.filter(b => b.status === "occupied").length;
    return (
      <div className="space-y-4">
        <button onClick={() => setSelectedWard(null)} className="flex items-center gap-1 text-sm text-blue-600 hover:underline"><ChevronLeft className="w-4 h-4" /> All Wards</button>
        <div className="flex items-center justify-between">
          <div><h3 className="text-base font-semibold text-gray-900">{ward.name}</h3><p className="text-xs text-gray-500">{wardOccupied}/{wardBeds.length} beds occupied</p></div>
        </div>
        <div className="grid grid-cols-4 sm:grid-cols-5 md:grid-cols-6 gap-2">
          {wardBeds.map(bed => (
            <button key={bed.id} onClick={() => bed.patient && setSelectedBed(bed)} className={`p-2 rounded-lg border text-center transition-colors ${statusColor[bed.status]} ${bed.patient ? "cursor-pointer hover:shadow-md" : ""}`}>
              <Bed className="w-4 h-4 mx-auto mb-1" />
              <p className="text-[10px] font-medium">{bed.bedNumber.split("-")[1]}</p>
              {bed.patient && <><p className="text-[9px] truncate">{bed.patient.name.split(" ")[1]}</p><span className={`inline-block w-2 h-2 rounded-full mt-0.5 ${acuityColor[bed.patient.acuityLevel]}`} /></>}
            </button>
          ))}
        </div>
        <div className="space-y-1 mt-4">
          <h4 className="text-xs font-semibold text-gray-500 uppercase">Patient List</h4>
          {wardBeds.filter(b => b.patient).map(b => (
            <button key={b.id} onClick={() => setSelectedBed(b)} className="w-full flex items-center justify-between px-3 py-2 rounded-lg hover:bg-gray-50 text-left">
              <div className="flex items-center gap-2"><span className={`w-2 h-2 rounded-full ${acuityColor[b.patient!.acuityLevel]}`} /><span className="text-sm font-medium text-gray-900">{b.patient!.name}</span><span className="text-xs text-gray-500">Bed {b.bedNumber}</span></div>
              <span className="text-xs text-gray-500">{b.patient!.diagnosis}</span>
            </button>
          ))}
        </div>
      </div>
    );
  }

  return (
    <div className="space-y-4">
      <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
        {[{ l: "Total Beds", v: totalBeds, c: "text-gray-900", icon: Bed }, { l: "Occupied", v: occupied, c: "text-blue-600", icon: Users }, { l: "Available", v: available, c: "text-green-600", icon: CheckCircle2 }, { l: "Critical", v: critical, c: "text-red-600", icon: AlertTriangle }].map(s => (
          <div key={s.l} className="bg-white rounded-lg border p-3"><div className="flex items-center justify-between"><s.icon className={`w-4 h-4 ${s.c}`} /><span className={`text-lg font-bold ${s.c}`}>{s.v}</span></div><p className="text-xs text-gray-500 mt-1">{s.l}</p></div>
        ))}
      </div>
      <div className="bg-white rounded-lg border p-3"><div className="flex items-center justify-between mb-2"><span className="text-xs font-medium text-gray-600">Occupancy</span><span className="text-xs font-bold text-gray-900">{occupancy}%</span></div><div className="w-full bg-gray-200 rounded-full h-2"><div className={`h-2 rounded-full ${occupancy > 90 ? "bg-red-500" : occupancy > 75 ? "bg-amber-500" : "bg-green-500"}`} style={{ width: `${occupancy}%` }} /></div></div>
      <div className="space-y-2">
        {WARDS.map(w => { const wb = MOCK_BEDS.filter(b => b.wardId === w.id); const wo = wb.filter(b => b.status === "occupied").length; const wp = Math.round((wo / wb.length) * 100); return (
          <button key={w.id} onClick={() => setSelectedWard(w.id)} className="w-full flex items-center justify-between px-4 py-3 bg-white rounded-lg border hover:shadow-sm transition-shadow text-left">
            <div className="flex items-center gap-3"><Building2 className="w-5 h-5 text-gray-400" /><div><p className="text-sm font-medium text-gray-900">{w.name}</p><p className="text-xs text-gray-500">{wo}/{wb.length} occupied</p></div></div>
            <div className="flex items-center gap-3"><div className="w-20 bg-gray-200 rounded-full h-1.5"><div className={`h-1.5 rounded-full ${wp > 90 ? "bg-red-500" : wp > 75 ? "bg-amber-500" : "bg-green-500"}`} style={{ width: `${wp}%` }} /></div><span className="text-xs font-medium text-gray-600 w-8 text-right">{wp}%</span><ChevronRight className="w-4 h-4 text-gray-400" /></div>
          </button>
        ); })}
      </div>
    </div>
  );
}

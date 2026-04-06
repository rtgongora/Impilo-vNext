"use client";
import { useState } from "react";
import { DoorOpen, Building2, ArrowRightLeft, Stethoscope, Calendar, FileText, CheckCircle2, AlertTriangle, Heart, Pill, BookOpen, ShieldAlert, Utensils, Activity } from "lucide-react";

type Disposition = "discharge" | "admit" | "transfer" | "refer" | "follow-up" | "deceased" | "lama" | "dnw";

const DISPOSITIONS: { id: Disposition; label: string; icon: React.ElementType; color: string }[] = [
  { id: "discharge", label: "Discharge Home", icon: DoorOpen, color: "bg-green-100 text-green-700 border-green-200" },
  { id: "admit", label: "Admit to Ward", icon: Building2, color: "bg-blue-100 text-blue-700 border-blue-200" },
  { id: "transfer", label: "Transfer", icon: ArrowRightLeft, color: "bg-amber-100 text-amber-700 border-amber-200" },
  { id: "refer", label: "Refer", icon: Stethoscope, color: "bg-purple-100 text-purple-700 border-purple-200" },
  { id: "follow-up", label: "Follow-up", icon: Calendar, color: "bg-cyan-100 text-cyan-700 border-cyan-200" },
  { id: "deceased", label: "Deceased", icon: Heart, color: "bg-gray-100 text-gray-700 border-gray-200" },
  { id: "lama", label: "Left Against Medical Advice", icon: AlertTriangle, color: "bg-red-100 text-red-700 border-red-200" },
  { id: "dnw", label: "Did Not Wait", icon: AlertTriangle, color: "bg-orange-100 text-orange-700 border-orange-200" },
];

const EDUCATION_ITEMS = [
  { id: "disease", label: "Disease Management", icon: BookOpen }, { id: "meds", label: "Medication Instructions", icon: Pill },
  { id: "danger", label: "Danger Signs to Watch", icon: ShieldAlert }, { id: "lifestyle", label: "Lifestyle Modifications", icon: Activity },
  { id: "wound", label: "Wound Care", icon: FileText }, { id: "diet", label: "Dietary Instructions", icon: Utensils },
  { id: "exercise", label: "Exercise Plan", icon: Activity }, { id: "followup", label: "Follow-up Instructions", icon: Calendar },
];

const RETURN_PRECAUTIONS = [
  "Fever not responding to medication", "Worsening pain or new symptoms", "Difficulty breathing",
  "Persistent vomiting or unable to keep fluids down", "Signs of infection (redness, swelling, discharge)",
  "Seizures or altered consciousness", "Heavy bleeding", "Medication side effects",
];

const WARDS = ["Medical Ward", "Surgical Ward", "Maternity Ward", "Paediatric Ward", "ICU", "HDU"];
const SPECIALTIES = ["Cardiology", "Surgery", "Orthopaedics", "Paediatrics", "Obstetrics", "ENT", "Ophthalmology", "Dermatology", "Psychiatry", "Oncology"];

interface OutcomeSectionProps { onSave?: (data: Record<string, unknown>) => void; }

export function OutcomeSection({ onSave }: OutcomeSectionProps) {
  const [disposition, setDisposition] = useState<Disposition | null>(null);
  const [dischargeSummary, setDischargeSummary] = useState("");
  const [followUpDate, setFollowUpDate] = useState("");
  const [followUpProvider, setFollowUpProvider] = useState("");
  const [followUpInstructions, setFollowUpInstructions] = useState("");
  const [admitWard, setAdmitWard] = useState("");
  const [admitDiagnosis, setAdmitDiagnosis] = useState("");
  const [admitLOS, setAdmitLOS] = useState("");
  const [transferFacility, setTransferFacility] = useState("");
  const [transferReason, setTransferReason] = useState("");
  const [transferTransport, setTransferTransport] = useState("");
  const [referFacility, setReferFacility] = useState("");
  const [referSpecialty, setReferSpecialty] = useState("");
  const [referUrgency, setReferUrgency] = useState("routine");
  const [referNotes, setReferNotes] = useState("");
  const [outcomeNotes, setOutcomeNotes] = useState("");
  const [careInstructions, setCareInstructions] = useState("");
  const [educationChecked, setEducationChecked] = useState<string[]>([]);
  const [returnPrecautions, setReturnPrecautions] = useState<string[]>([]);

  const toggleEducation = (id: string) => setEducationChecked(prev => prev.includes(id) ? prev.filter(x => x !== id) : [...prev, id]);
  const togglePrecaution = (p: string) => setReturnPrecautions(prev => prev.includes(p) ? prev.filter(x => x !== p) : [...prev, p]);

  return (
    <div className="bg-white rounded-lg border">
      <div className="px-4 py-3 border-b"><h2 className="text-sm font-semibold text-gray-900">Visit Outcome & Disposition</h2><p className="text-xs text-gray-500">Select disposition and complete required documentation</p></div>

      {/* Disposition Selector */}
      <div className="p-4 border-b">
        <p className="text-xs font-medium text-gray-500 uppercase mb-2">Disposition</p>
        <div className="grid grid-cols-2 md:grid-cols-4 gap-2">
          {DISPOSITIONS.map(d => (
            <button key={d.id} onClick={() => setDisposition(d.id)} className={`flex items-center gap-2 px-3 py-2.5 rounded-lg border text-left transition-all ${disposition === d.id ? d.color + " ring-2 ring-offset-1 ring-blue-400" : "border-gray-200 hover:bg-gray-50"}`}>
              <d.icon className="w-4 h-4 shrink-0" /><span className="text-xs font-medium">{d.label}</span>
            </button>
          ))}
        </div>
      </div>

      {/* Disposition-specific fields */}
      {disposition && (
        <div className="p-4 border-b space-y-4">
          {disposition === "discharge" && (
            <>
              <div><label className="text-[10px] font-medium text-gray-500 uppercase">Discharge Summary</label><textarea value={dischargeSummary} onChange={e => setDischargeSummary(e.target.value)} rows={3} className="w-full mt-0.5 px-3 py-2 text-sm border rounded-lg" placeholder="Summary of encounter, findings, and treatment..." /></div>
              <div><label className="text-[10px] font-medium text-gray-500 uppercase">Follow-up Date</label><input type="date" value={followUpDate} onChange={e => setFollowUpDate(e.target.value)} className="w-full mt-0.5 px-3 py-2 text-sm border rounded-lg" /></div>
            </>
          )}
          {disposition === "admit" && (
            <div className="grid grid-cols-2 gap-3">
              <div><label className="text-[10px] font-medium text-gray-500 uppercase">Target Ward</label><select value={admitWard} onChange={e => setAdmitWard(e.target.value)} className="w-full mt-0.5 px-3 py-2 text-sm border rounded-lg"><option value="">Select ward...</option>{WARDS.map(w => <option key={w} value={w}>{w}</option>)}</select></div>
              <div><label className="text-[10px] font-medium text-gray-500 uppercase">Expected LOS (days)</label><input value={admitLOS} onChange={e => setAdmitLOS(e.target.value)} type="number" className="w-full mt-0.5 px-3 py-2 text-sm border rounded-lg" /></div>
              <div className="col-span-2"><label className="text-[10px] font-medium text-gray-500 uppercase">Admitting Diagnosis</label><input value={admitDiagnosis} onChange={e => setAdmitDiagnosis(e.target.value)} className="w-full mt-0.5 px-3 py-2 text-sm border rounded-lg" placeholder="Primary admitting diagnosis..." /></div>
            </div>
          )}
          {disposition === "transfer" && (
            <div className="space-y-3">
              <div><label className="text-[10px] font-medium text-gray-500 uppercase">Target Facility</label><input value={transferFacility} onChange={e => setTransferFacility(e.target.value)} className="w-full mt-0.5 px-3 py-2 text-sm border rounded-lg" placeholder="Receiving facility name..." /></div>
              <div><label className="text-[10px] font-medium text-gray-500 uppercase">Reason for Transfer</label><textarea value={transferReason} onChange={e => setTransferReason(e.target.value)} rows={2} className="w-full mt-0.5 px-3 py-2 text-sm border rounded-lg" /></div>
              <div><label className="text-[10px] font-medium text-gray-500 uppercase">Transport Mode</label><select value={transferTransport} onChange={e => setTransferTransport(e.target.value)} className="w-full mt-0.5 px-3 py-2 text-sm border rounded-lg"><option value="">Select...</option><option>Ambulance</option><option>Private Vehicle</option><option>Air Ambulance</option></select></div>
            </div>
          )}
          {disposition === "refer" && (
            <div className="grid grid-cols-2 gap-3">
              <div><label className="text-[10px] font-medium text-gray-500 uppercase">Facility</label><input value={referFacility} onChange={e => setReferFacility(e.target.value)} className="w-full mt-0.5 px-3 py-2 text-sm border rounded-lg" /></div>
              <div><label className="text-[10px] font-medium text-gray-500 uppercase">Specialty</label><select value={referSpecialty} onChange={e => setReferSpecialty(e.target.value)} className="w-full mt-0.5 px-3 py-2 text-sm border rounded-lg"><option value="">Select...</option>{SPECIALTIES.map(s => <option key={s} value={s}>{s}</option>)}</select></div>
              <div><label className="text-[10px] font-medium text-gray-500 uppercase">Urgency</label><select value={referUrgency} onChange={e => setReferUrgency(e.target.value)} className="w-full mt-0.5 px-3 py-2 text-sm border rounded-lg"><option value="routine">Routine</option><option value="urgent">Urgent</option><option value="emergency">Emergency</option></select></div>
              <div className="col-span-2"><label className="text-[10px] font-medium text-gray-500 uppercase">Referral Notes</label><textarea value={referNotes} onChange={e => setReferNotes(e.target.value)} rows={2} className="w-full mt-0.5 px-3 py-2 text-sm border rounded-lg" /></div>
            </div>
          )}
          {disposition === "follow-up" && (
            <div className="grid grid-cols-2 gap-3">
              <div><label className="text-[10px] font-medium text-gray-500 uppercase">Follow-up Date</label><input type="date" value={followUpDate} onChange={e => setFollowUpDate(e.target.value)} className="w-full mt-0.5 px-3 py-2 text-sm border rounded-lg" /></div>
              <div><label className="text-[10px] font-medium text-gray-500 uppercase">Provider</label><input value={followUpProvider} onChange={e => setFollowUpProvider(e.target.value)} className="w-full mt-0.5 px-3 py-2 text-sm border rounded-lg" placeholder="Assigned provider..." /></div>
              <div className="col-span-2"><label className="text-[10px] font-medium text-gray-500 uppercase">Instructions</label><textarea value={followUpInstructions} onChange={e => setFollowUpInstructions(e.target.value)} rows={2} className="w-full mt-0.5 px-3 py-2 text-sm border rounded-lg" /></div>
            </div>
          )}
        </div>
      )}

      {/* Outcome Notes */}
      <div className="p-4 border-b"><label className="text-[10px] font-medium text-gray-500 uppercase">Outcome Notes</label><textarea value={outcomeNotes} onChange={e => setOutcomeNotes(e.target.value)} rows={3} className="w-full mt-0.5 px-3 py-2 text-sm border rounded-lg" placeholder="Additional outcome documentation..." /></div>

      {/* Care Instructions */}
      <div className="p-4 border-b"><label className="text-[10px] font-medium text-gray-500 uppercase">Care Instructions</label><textarea value={careInstructions} onChange={e => setCareInstructions(e.target.value)} rows={2} className="w-full mt-0.5 px-3 py-2 text-sm border rounded-lg" placeholder="Instructions for patient/caregiver..." /></div>

      {/* Patient Education */}
      <div className="p-4 border-b">
        <p className="text-[10px] font-medium text-gray-500 uppercase mb-2">Patient Education Provided</p>
        <div className="grid grid-cols-2 gap-1">{EDUCATION_ITEMS.map(e => (
          <label key={e.id} className={`flex items-center gap-2 px-3 py-2 rounded-lg cursor-pointer ${educationChecked.includes(e.id) ? "bg-green-50 border border-green-200" : "hover:bg-gray-50 border border-transparent"}`}>
            <input type="checkbox" checked={educationChecked.includes(e.id)} onChange={() => toggleEducation(e.id)} className="rounded border-gray-300 text-green-600" />
            <e.icon className="w-3.5 h-3.5 text-gray-400" /><span className="text-xs text-gray-700">{e.label}</span>
          </label>
        ))}</div>
      </div>

      {/* Return Precautions */}
      <div className="p-4 border-b">
        <p className="text-[10px] font-medium text-gray-500 uppercase mb-2">Return Precautions Discussed</p>
        <div className="space-y-1">{RETURN_PRECAUTIONS.map(p => (
          <label key={p} className={`flex items-center gap-2 px-3 py-2 rounded-lg cursor-pointer ${returnPrecautions.includes(p) ? "bg-amber-50 border border-amber-200" : "hover:bg-gray-50 border border-transparent"}`}>
            <input type="checkbox" checked={returnPrecautions.includes(p)} onChange={() => togglePrecaution(p)} className="rounded border-gray-300 text-amber-600" />
            <span className="text-xs text-gray-700">{p}</span>
          </label>
        ))}</div>
      </div>

      {/* Save */}
      <div className="px-4 py-3 bg-gray-50 flex justify-end gap-2">
        <button className="px-4 py-2 text-sm font-medium text-gray-600 border rounded-lg hover:bg-gray-100">Save Draft</button>
        <button onClick={() => onSave?.({})} className="px-4 py-2 bg-blue-600 text-white text-sm font-medium rounded-lg hover:bg-blue-700 disabled:opacity-50" disabled={!disposition}>Complete Encounter</button>
      </div>
    </div>
  );
}

"use client";

/**
 * OutcomeSection — Full visit outcome and disposition form.
 * Ported from Lovable OutcomeSection.tsx (1090 lines).
 * Supports: Discharge, Admit, Transfer, Refer, Death, LAMA with sub-forms.
 */

import { useState } from "react";
import {
  DoorOpen, Building2, ArrowRightLeft, Stethoscope, Calendar, FileText,
  CheckCircle2, AlertTriangle, Heart, Pill, BookOpen, ShieldAlert, Utensils,
  Activity, User, ClipboardList, Phone, MapPin, Plus, Clock, Send, UserX,
} from "lucide-react";

type Disposition = "discharge" | "admit" | "transfer" | "refer" | "death" | "lama" | "";

const DISPOSITIONS: { id: Disposition; label: string; icon: React.ElementType; description: string; color: string }[] = [
  { id: "discharge", label: "Discharge", icon: DoorOpen, description: "Patient ready for discharge home", color: "bg-green-100 text-green-700 border-green-300" },
  { id: "admit", label: "Admit", icon: Building2, description: "Admit to inpatient ward", color: "bg-blue-100 text-blue-700 border-blue-300" },
  { id: "transfer", label: "Transfer", icon: ArrowRightLeft, description: "Transfer to another facility", color: "bg-amber-100 text-amber-700 border-amber-300" },
  { id: "refer", label: "Refer", icon: Send, description: "Refer for specialist care", color: "bg-purple-100 text-purple-700 border-purple-300" },
  { id: "death", label: "Death", icon: Heart, description: "Record patient death", color: "bg-gray-100 text-gray-600 border-gray-300" },
  { id: "lama", label: "LAMA/Absconded", icon: UserX, description: "Left against medical advice", color: "bg-red-100 text-red-700 border-red-300" },
];

const DISCHARGE_DIAGNOSES = [
  { code: "N39.0", name: "Urinary tract infection, site not specified", type: "Primary" },
  { code: "E11.9", name: "Type 2 diabetes mellitus without complications", type: "Secondary" },
  { code: "I10", name: "Essential (primary) hypertension", type: "Secondary" },
];

const DISCHARGE_MEDICATIONS = [
  { name: "Metformin 500mg", dosage: "1 tablet BD", duration: "Ongoing", instructions: "Take with meals" },
  { name: "Amlodipine 5mg", dosage: "1 tablet OD", duration: "Ongoing", instructions: "Take in the morning" },
  { name: "Ciprofloxacin 500mg", dosage: "1 tablet BD", duration: "5 days", instructions: "Complete the course" },
];

const WARDS = ["Medical Ward 1", "Medical Ward 2", "Surgical Ward", "ICU", "HDU", "Maternity Ward", "Paediatric Ward"];
const FACILITIES = ["Harare Central Hospital", "Parirenyatwa Hospital", "United Bulawayo Hospitals", "Mpilo Central Hospital", "Chitungwiza Central Hospital"];
const SPECIALTIES = ["Cardiology", "Surgery", "Orthopaedics", "Paediatrics", "Obstetrics", "ENT", "Ophthalmology", "Dermatology", "Psychiatry", "Oncology"];

const DISCHARGE_CHECKLIST = [
  { id: "meds", label: "Medication reconciliation complete", critical: true },
  { id: "rx", label: "Discharge medications ordered/dispensed", critical: true },
  { id: "edu", label: "Patient/family education provided", critical: false },
  { id: "appt", label: "Follow-up appointments scheduled", critical: false },
  { id: "docs", label: "Discharge documents prepared", critical: false },
  { id: "sign", label: "Patient signed discharge papers", critical: true },
  { id: "transport", label: "Transport arrangements confirmed", critical: false },
  { id: "chw", label: "CHW notified for follow-up", critical: false },
];

const COUNSELING_ITEMS = ["Diabetes education", "Medication adherence", "Diet & lifestyle", "Warning signs", "Follow-up importance", "Wound care", "Activity restrictions"];

const CHW_TASKS = [
  "Home visit within 48 hours to check medication adherence",
  "Blood glucose monitoring education",
  "Verify patient has all discharge medications",
  "Assess home environment and support system",
];

const SPECIAL_REQUIREMENTS = ["Isolation", "Monitoring", "IV Access", "Oxygen", "NPO", "Fall precautions"];

interface OutcomeSectionProps { onSave?: (data: Record<string, unknown>) => void; }

export function OutcomeSection({ onSave }: OutcomeSectionProps) {
  const [disposition, setDisposition] = useState<Disposition>("");
  const [checkedItems, setCheckedItems] = useState<Record<string, boolean>>({});
  const [isCompleted, setIsCompleted] = useState(false);
  const [counselingGiven, setCounselingGiven] = useState<string[]>([]);

  const toggleCheckItem = (id: string) => setCheckedItems(prev => ({ ...prev, [id]: !prev[id] }));
  const toggleCounseling = (item: string) => setCounselingGiven(prev => prev.includes(item) ? prev.filter(x => x !== item) : [...prev, item]);

  const checkedCount = Object.values(checkedItems).filter(Boolean).length;

  return (
    <div className="space-y-6">
      {/* Disposition Selection */}
      <div className="bg-white rounded-lg border p-4">
        <h3 className="text-base font-semibold text-gray-900 flex items-center gap-2 mb-4">
          <CheckCircle2 className="w-5 h-5 text-blue-600" /> Visit Outcome
        </h3>
        <div className="grid grid-cols-3 gap-3">
          {DISPOSITIONS.map(opt => {
            const Icon = opt.icon;
            const selected = disposition === opt.id;
            return (
              <button key={opt.id} onClick={() => setDisposition(opt.id as Disposition)}
                className={`flex flex-col items-center gap-2 p-4 border-2 rounded-lg transition-all text-center ${
                  selected ? `${opt.color} ring-2 ring-blue-400 ring-offset-1` : "border-gray-200 hover:bg-gray-50"
                }`}>
                <div className={`w-12 h-12 rounded-full flex items-center justify-center ${selected ? "bg-white/60" : "bg-gray-100"}`}>
                  <Icon className="w-6 h-6" />
                </div>
                <div className="font-medium text-sm">{opt.label}</div>
                <div className="text-xs text-gray-500">{opt.description}</div>
              </button>
            );
          })}
        </div>
      </div>

      {/* Discharge Form */}
      {disposition === "discharge" && (
        <div className="space-y-4">
          {/* Tabs */}
          <DischargeTabs checkedItems={checkedItems} toggleCheckItem={toggleCheckItem} counselingGiven={counselingGiven} toggleCounseling={toggleCounseling} checkedCount={checkedCount} />
        </div>
      )}

      {/* Admit Form */}
      {disposition === "admit" && <AdmitForm />}

      {/* Transfer Form */}
      {disposition === "transfer" && <TransferForm />}

      {/* Refer Form */}
      {disposition === "refer" && <ReferForm />}

      {/* Death Form */}
      {disposition === "death" && <DeathForm />}

      {/* LAMA Form */}
      {disposition === "lama" && <LAMAForm />}

      {/* Actions */}
      {disposition && !isCompleted && (
        <div className="flex justify-end gap-3">
          <button className="px-4 py-2 text-sm font-medium text-gray-600 border rounded-lg hover:bg-gray-100">Save Draft</button>
          <button className="px-4 py-2 text-sm font-medium text-blue-600 border border-blue-200 rounded-lg hover:bg-blue-50">Preview Summary</button>
          <button onClick={() => { setIsCompleted(true); onSave?.({}); }}
            className="px-4 py-2 bg-blue-600 text-white text-sm font-medium rounded-lg hover:bg-blue-700 flex items-center gap-2">
            <CheckCircle2 className="w-4 h-4" /> Complete Encounter
          </button>
        </div>
      )}

      {isCompleted && (
        <div className="bg-green-50 border border-green-200 rounded-lg p-6 text-center">
          <CheckCircle2 className="w-10 h-10 text-green-600 mx-auto mb-2" />
          <h3 className="text-lg font-semibold text-green-800">Encounter Completed</h3>
          <p className="text-sm text-green-700 mt-1">All documentation has been saved.</p>
        </div>
      )}
    </div>
  );
}

// ── Discharge Tabs ─────────────────────────────────────────
function DischargeTabs({ checkedItems, toggleCheckItem, counselingGiven, toggleCounseling, checkedCount }: {
  checkedItems: Record<string, boolean>; toggleCheckItem: (id: string) => void;
  counselingGiven: string[]; toggleCounseling: (item: string) => void; checkedCount: number;
}) {
  const [activeTab, setActiveTab] = useState("summary");
  const tabs = [
    { id: "summary", label: "Summary" }, { id: "medications", label: "Medications" },
    { id: "followup", label: "Follow-up" }, { id: "checklist", label: "Checklist" },
  ];

  return (
    <div className="bg-white rounded-lg border overflow-hidden">
      <div className="flex border-b">
        {tabs.map(t => (
          <button key={t.id} onClick={() => setActiveTab(t.id)}
            className={`flex-1 px-4 py-2.5 text-sm font-medium border-b-2 transition-colors ${activeTab === t.id ? "border-blue-600 text-blue-600" : "border-transparent text-gray-500 hover:text-gray-700"}`}>{t.label}</button>
        ))}
      </div>

      <div className="p-4">
        {activeTab === "summary" && (
          <div className="space-y-4">
            <div>
              <label className="text-sm font-medium text-gray-700">Discharge Diagnoses</label>
              <div className="mt-2 space-y-2">
                {DISCHARGE_DIAGNOSES.map((dx, i) => (
                  <div key={i} className="flex items-center justify-between p-3 border rounded-lg">
                    <div className="flex items-center gap-3">
                      <span className={`px-2 py-0.5 rounded text-xs font-medium ${dx.type === "Primary" ? "bg-blue-100 text-blue-700" : "bg-gray-100 text-gray-600"}`}>{dx.type}</span>
                      <div><span className="text-sm font-medium">{dx.name}</span><span className="text-xs text-gray-400 ml-2">({dx.code})</span></div>
                    </div>
                  </div>
                ))}
                <button className="w-full flex items-center justify-center gap-1 px-3 py-2 text-sm text-gray-600 border border-dashed rounded-lg hover:bg-gray-50"><Plus className="w-4 h-4" /> Add Diagnosis</button>
              </div>
            </div>
            <div>
              <label className="text-sm font-medium text-gray-700">Hospital Course Summary</label>
              <textarea rows={5} className="w-full mt-2 px-3 py-2 text-sm border rounded-lg" defaultValue="65-year-old female admitted with complicated UTI (pyelonephritis). Treated with IV Ceftriaxone for 3 days with good clinical response. Blood glucose levels optimized. Blood pressure controlled. Afebrile for 48 hours. Repeat urinalysis clear." />
            </div>
            <div>
              <label className="text-sm font-medium text-gray-700">Condition at Discharge</label>
              <div className="flex gap-2 mt-2">
                {["Improved", "Stable", "Unchanged", "Deteriorated"].map(s => (
                  <button key={s} className={`px-3 py-1.5 rounded-lg text-xs font-medium border transition-colors ${s === "Improved" ? "bg-green-100 text-green-700 border-green-200" : "border-gray-200 text-gray-600 hover:bg-gray-50"}`}>{s}</button>
                ))}
              </div>
            </div>
          </div>
        )}

        {activeTab === "medications" && (
          <div className="space-y-4">
            <div className="flex items-center justify-between mb-2">
              <h4 className="text-sm font-semibold text-gray-900 flex items-center gap-2"><Pill className="w-4 h-4 text-blue-600" /> Discharge Medications</h4>
              <button className="px-3 py-1.5 bg-blue-600 text-white text-xs font-medium rounded-lg hover:bg-blue-700 flex items-center gap-1"><Plus className="w-3 h-3" /> Add</button>
            </div>
            {DISCHARGE_MEDICATIONS.map((med, i) => (
              <div key={i} className="p-4 border rounded-lg">
                <div className="flex items-start justify-between">
                  <div><h4 className="font-medium text-sm">{med.name}</h4><div className="flex items-center gap-2 mt-1 text-xs text-gray-500"><span>{med.dosage}</span><span>&middot;</span><span>{med.duration}</span></div><p className="text-xs text-gray-400 mt-1 italic">{med.instructions}</p></div>
                  <span className="px-2 py-0.5 bg-blue-50 text-blue-600 rounded text-xs font-medium">Prescribed</span>
                </div>
              </div>
            ))}
            <div className="p-4 bg-amber-50 border border-amber-200 rounded-lg flex items-start gap-2">
              <AlertTriangle className="w-4 h-4 text-amber-600 mt-0.5 shrink-0" />
              <div><p className="text-sm font-medium text-amber-800">Medication Reconciliation Required</p><p className="text-xs text-amber-700 mt-0.5">Verify all medications with the patient before discharge.</p></div>
            </div>
          </div>
        )}

        {activeTab === "followup" && (
          <div className="space-y-4">
            {/* Follow-up Scheduling */}
            <div className="border rounded-lg p-4 space-y-3">
              <h4 className="text-sm font-semibold text-gray-900 flex items-center gap-2"><Calendar className="w-4 h-4 text-blue-600" /> Follow-up Appointments</h4>
              <div className="grid grid-cols-2 gap-3">
                <div><label className="text-[10px] font-medium text-gray-500 uppercase">Follow-up Date</label><input type="date" className="w-full mt-0.5 px-3 py-2 text-sm border rounded-lg" /></div>
                <div><label className="text-[10px] font-medium text-gray-500 uppercase">Provider / Clinic</label><input className="w-full mt-0.5 px-3 py-2 text-sm border rounded-lg" placeholder="Select provider..." /></div>
              </div>
              <div className="flex gap-2">
                {["1 week", "2 weeks", "1 month", "3 months"].map(p => (
                  <button key={p} className="px-3 py-1.5 text-xs font-medium text-blue-600 border border-blue-200 rounded-lg hover:bg-blue-50">{p}</button>
                ))}
              </div>
            </div>

            {/* CHW Tasks */}
            <div className="border rounded-lg p-4 space-y-3">
              <h4 className="text-sm font-semibold text-gray-900 flex items-center gap-2"><User className="w-4 h-4 text-blue-600" /> CHW Follow-up Tasks</h4>
              {CHW_TASKS.map(task => (
                <label key={task} className="flex items-center gap-3 p-3 border rounded-lg cursor-pointer hover:bg-gray-50">
                  <input type="checkbox" className="rounded border-gray-300 text-blue-600" /><span className="text-sm text-gray-700">{task}</span>
                </label>
              ))}
              <button className="w-full flex items-center justify-center gap-1 px-3 py-2 text-sm text-gray-600 border border-dashed rounded-lg hover:bg-gray-50"><Plus className="w-4 h-4" /> Add CHW Task</button>
            </div>

            {/* Instructions & Counseling */}
            <div className="border rounded-lg p-4 space-y-4">
              <h4 className="text-sm font-semibold text-gray-900 flex items-center gap-2"><FileText className="w-4 h-4 text-blue-600" /> Discharge Instructions & Counseling</h4>
              <div><label className="text-sm font-medium text-gray-700">Instructions</label><textarea rows={4} className="w-full mt-2 px-3 py-2 text-sm border rounded-lg" defaultValue={"1. Take all medications as prescribed\n2. Complete the antibiotic course\n3. Monitor blood glucose twice daily\n4. Return immediately if fever, dysuria, or flank pain recurs\n5. Maintain adequate hydration"} /></div>
              <div>
                <label className="text-sm font-medium text-gray-700">Counseling Provided</label>
                <div className="mt-2 flex flex-wrap gap-2">
                  {COUNSELING_ITEMS.map(item => (
                    <button key={item} onClick={() => toggleCounseling(item)}
                      className={`px-3 py-1.5 rounded-lg text-xs font-medium border transition-colors flex items-center gap-1 ${counselingGiven.includes(item) ? "bg-green-100 text-green-700 border-green-200" : "border-gray-200 text-gray-600 hover:bg-gray-50"}`}>
                      {counselingGiven.includes(item) && <CheckCircle2 className="w-3 h-3" />} {item}
                    </button>
                  ))}
                </div>
              </div>
            </div>
          </div>
        )}

        {activeTab === "checklist" && (
          <div className="space-y-3">
            <h4 className="text-sm font-semibold text-gray-900 flex items-center gap-2"><ClipboardList className="w-4 h-4 text-blue-600" /> Discharge Checklist</h4>
            {DISCHARGE_CHECKLIST.map(item => (
              <button key={item.id} onClick={() => toggleCheckItem(item.id)}
                className={`w-full flex items-center gap-3 p-3 border rounded-lg text-left transition-colors ${checkedItems[item.id] ? "bg-green-50 border-green-200" : "hover:bg-gray-50"}`}>
                <input type="checkbox" checked={checkedItems[item.id] || false} readOnly className="rounded border-gray-300 text-green-600" />
                <span className={`text-sm flex-1 ${checkedItems[item.id] ? "line-through text-gray-400" : "text-gray-700"}`}>{item.label}</span>
                {item.critical && !checkedItems[item.id] && <span className="px-2 py-0.5 bg-red-100 text-red-700 rounded text-xs font-medium">Required</span>}
              </button>
            ))}
            <div className="mt-4 p-3 bg-gray-50 rounded-lg">
              <div className="flex items-center justify-between text-sm"><span className="text-gray-600">Progress</span><span className="font-medium text-gray-900">{checkedCount} / {DISCHARGE_CHECKLIST.length}</span></div>
              <div className="mt-2 h-2 bg-gray-200 rounded-full overflow-hidden"><div className="h-full bg-blue-600 transition-all" style={{ width: `${(checkedCount / DISCHARGE_CHECKLIST.length) * 100}%` }} /></div>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}

// ── Admit Form ─────────────────────────────────────────────
function AdmitForm() {
  return (
    <div className="bg-white rounded-lg border p-4 space-y-4">
      <h3 className="text-base font-semibold text-gray-900 flex items-center gap-2"><Building2 className="w-5 h-5 text-blue-600" /> Admission Details</h3>
      <div className="grid grid-cols-2 gap-4">
        <div><label className="text-sm font-medium text-gray-700">Admitting Ward</label><select className="w-full mt-1 px-3 py-2 text-sm border rounded-lg"><option value="">Select ward...</option>{WARDS.map(w => <option key={w} value={w}>{w}</option>)}</select></div>
        <div><label className="text-sm font-medium text-gray-700">Bed Assignment</label><input className="w-full mt-1 px-3 py-2 text-sm border rounded-lg" placeholder="Bed number (if known)" /></div>
      </div>
      <div><label className="text-sm font-medium text-gray-700">Admission Diagnosis</label><input className="w-full mt-1 px-3 py-2 text-sm border rounded-lg" placeholder="Primary reason for admission" /></div>
      <div><label className="text-sm font-medium text-gray-700">Admitting Team / Consultant</label><input className="w-full mt-1 px-3 py-2 text-sm border rounded-lg" placeholder="Select or type..." /></div>
      <div>
        <label className="text-sm font-medium text-gray-700">Priority Level</label>
        <div className="flex gap-2 mt-1">{["Routine", "Urgent", "Emergency"].map(l => <button key={l} className={`px-3 py-1.5 rounded-lg text-xs font-medium border ${l === "Urgent" ? "bg-amber-100 text-amber-700 border-amber-200" : "border-gray-200 text-gray-600 hover:bg-gray-50"}`}>{l}</button>)}</div>
      </div>
      <div>
        <label className="text-sm font-medium text-gray-700">Special Requirements</label>
        <div className="flex flex-wrap gap-2 mt-1">{SPECIAL_REQUIREMENTS.map(r => <button key={r} className="px-3 py-1.5 rounded-lg text-xs font-medium border border-gray-200 text-gray-600 hover:bg-gray-50">{r}</button>)}</div>
      </div>
      <div><label className="text-sm font-medium text-gray-700">Admission Notes</label><textarea rows={4} className="w-full mt-1 px-3 py-2 text-sm border rounded-lg" placeholder="Brief summary and initial orders..." /></div>
    </div>
  );
}

// ── Transfer Form ──────────────────────────────────────────
function TransferForm() {
  return (
    <div className="bg-white rounded-lg border p-4 space-y-4">
      <h3 className="text-base font-semibold text-gray-900 flex items-center gap-2"><ArrowRightLeft className="w-5 h-5 text-amber-600" /> Transfer Details</h3>
      <div className="grid grid-cols-2 gap-4">
        <div><label className="text-sm font-medium text-gray-700">Receiving Facility</label><select className="w-full mt-1 px-3 py-2 text-sm border rounded-lg"><option value="">Select facility...</option>{FACILITIES.map(f => <option key={f} value={f}>{f}</option>)}</select></div>
        <div><label className="text-sm font-medium text-gray-700">Receiving Ward</label><input className="w-full mt-1 px-3 py-2 text-sm border rounded-lg" placeholder="Ward at receiving facility" /></div>
      </div>
      <div><label className="text-sm font-medium text-gray-700">Reason for Transfer</label><textarea rows={3} className="w-full mt-1 px-3 py-2 text-sm border rounded-lg" placeholder="Clinical reason for transfer..." /></div>
      <div className="grid grid-cols-2 gap-4">
        <div><label className="text-sm font-medium text-gray-700">Transport Mode</label><select className="w-full mt-1 px-3 py-2 text-sm border rounded-lg"><option>Ambulance</option><option>Private Vehicle</option><option>Air Ambulance</option></select></div>
        <div><label className="text-sm font-medium text-gray-700">Accompanying Staff</label><input className="w-full mt-1 px-3 py-2 text-sm border rounded-lg" placeholder="Nurse / Paramedic name" /></div>
      </div>
      <div>
        <label className="text-sm font-medium text-gray-700">Clinical Summary for Receiving Facility</label>
        <textarea rows={4} className="w-full mt-1 px-3 py-2 text-sm border rounded-lg" placeholder="Current condition, interventions, pending investigations..." />
      </div>
      <div className="p-3 bg-amber-50 border border-amber-200 rounded-lg flex items-start gap-2">
        <Phone className="w-4 h-4 text-amber-600 mt-0.5 shrink-0" />
        <div><p className="text-sm font-medium text-amber-800">Confirm bed availability</p><p className="text-xs text-amber-700">Call the receiving facility to confirm acceptance before initiating transfer.</p></div>
      </div>
    </div>
  );
}

// ── Refer Form ─────────────────────────────────────────────
function ReferForm() {
  return (
    <div className="bg-white rounded-lg border p-4 space-y-4">
      <h3 className="text-base font-semibold text-gray-900 flex items-center gap-2"><Send className="w-5 h-5 text-purple-600" /> Referral Details</h3>
      <div className="grid grid-cols-2 gap-4">
        <div><label className="text-sm font-medium text-gray-700">Referring To (Facility)</label><select className="w-full mt-1 px-3 py-2 text-sm border rounded-lg"><option value="">Select facility...</option>{FACILITIES.map(f => <option key={f} value={f}>{f}</option>)}</select></div>
        <div><label className="text-sm font-medium text-gray-700">Specialty</label><select className="w-full mt-1 px-3 py-2 text-sm border rounded-lg"><option value="">Select...</option>{SPECIALTIES.map(s => <option key={s} value={s}>{s}</option>)}</select></div>
      </div>
      <div>
        <label className="text-sm font-medium text-gray-700">Urgency</label>
        <div className="flex gap-2 mt-1">{["Routine", "Urgent", "Emergency"].map(u => <button key={u} className={`px-3 py-1.5 rounded-lg text-xs font-medium border ${u === "Urgent" ? "bg-amber-100 text-amber-700 border-amber-200" : "border-gray-200 text-gray-600 hover:bg-gray-50"}`}>{u}</button>)}</div>
      </div>
      <div><label className="text-sm font-medium text-gray-700">Referral Reason / Clinical Question</label><textarea rows={3} className="w-full mt-1 px-3 py-2 text-sm border rounded-lg" placeholder="What do you want the specialist to address?" /></div>
      <div><label className="text-sm font-medium text-gray-700">Key Findings / Investigations</label><textarea rows={3} className="w-full mt-1 px-3 py-2 text-sm border rounded-lg" placeholder="Relevant exam findings, lab results, imaging..." /></div>
      <div>
        <label className="text-sm font-medium text-gray-700">Documents to Include</label>
        <div className="flex flex-wrap gap-2 mt-1">
          {["Discharge summary", "Lab results", "Imaging", "Medication list", "Referral letter"].map(d => (
            <label key={d} className="flex items-center gap-2 px-3 py-1.5 rounded-lg border border-gray-200 text-xs text-gray-600 cursor-pointer hover:bg-gray-50">
              <input type="checkbox" className="rounded border-gray-300 text-blue-600" /> {d}
            </label>
          ))}
        </div>
      </div>
    </div>
  );
}

// ── Death Form ─────────────────────────────────────────────
function DeathForm() {
  return (
    <div className="bg-white rounded-lg border p-4 space-y-4">
      <h3 className="text-base font-semibold text-gray-900 flex items-center gap-2"><Heart className="w-5 h-5 text-gray-500" /> Record of Death</h3>
      <div className="grid grid-cols-2 gap-4">
        <div><label className="text-sm font-medium text-gray-700">Date & Time of Death</label><input type="datetime-local" className="w-full mt-1 px-3 py-2 text-sm border rounded-lg" /></div>
        <div><label className="text-sm font-medium text-gray-700">Certifying Physician</label><input className="w-full mt-1 px-3 py-2 text-sm border rounded-lg" placeholder="Name of certifying doctor" /></div>
      </div>
      <div><label className="text-sm font-medium text-gray-700">Immediate Cause of Death</label><input className="w-full mt-1 px-3 py-2 text-sm border rounded-lg" placeholder="ICD-10 coded cause" /></div>
      <div><label className="text-sm font-medium text-gray-700">Underlying Cause(s)</label><textarea rows={2} className="w-full mt-1 px-3 py-2 text-sm border rounded-lg" placeholder="Chain of events leading to death..." /></div>
      <div>
        <label className="text-sm font-medium text-gray-700">Manner of Death</label>
        <div className="flex gap-2 mt-1">{["Natural", "Accident", "Homicide", "Suicide", "Pending Investigation", "Unknown"].map(m => <button key={m} className="px-3 py-1.5 rounded-lg text-xs font-medium border border-gray-200 text-gray-600 hover:bg-gray-50">{m}</button>)}</div>
      </div>
      <label className="flex items-center gap-2 px-3 py-2 border rounded-lg cursor-pointer hover:bg-gray-50">
        <input type="checkbox" className="rounded border-gray-300 text-blue-600" /><span className="text-sm text-gray-700">Post-mortem examination requested</span>
      </label>
      <div><label className="text-sm font-medium text-gray-700">Additional Notes</label><textarea rows={3} className="w-full mt-1 px-3 py-2 text-sm border rounded-lg" /></div>
    </div>
  );
}

// ── LAMA Form ──────────────────────────────────────────────
function LAMAForm() {
  return (
    <div className="bg-white rounded-lg border p-4 space-y-4">
      <h3 className="text-base font-semibold text-gray-900 flex items-center gap-2"><UserX className="w-5 h-5 text-red-600" /> Left Against Medical Advice</h3>
      <div className="p-3 bg-red-50 border border-red-200 rounded-lg">
        <p className="text-sm text-red-800 font-medium">Patient has chosen to leave against medical advice.</p>
        <p className="text-xs text-red-700 mt-1">Document that risks were explained and patient capacity was assessed.</p>
      </div>
      <div><label className="text-sm font-medium text-gray-700">Date & Time of Departure</label><input type="datetime-local" className="w-full mt-1 px-3 py-2 text-sm border rounded-lg" /></div>
      <div><label className="text-sm font-medium text-gray-700">Risks Explained to Patient</label><textarea rows={3} className="w-full mt-1 px-3 py-2 text-sm border rounded-lg" placeholder="Document specific risks discussed (infection, deterioration, death...)" /></div>
      <label className="flex items-center gap-2 px-3 py-2 border rounded-lg cursor-pointer hover:bg-gray-50">
        <input type="checkbox" className="rounded border-gray-300 text-blue-600" /><span className="text-sm text-gray-700">Patient has decision-making capacity</span>
      </label>
      <label className="flex items-center gap-2 px-3 py-2 border rounded-lg cursor-pointer hover:bg-gray-50">
        <input type="checkbox" className="rounded border-gray-300 text-blue-600" /><span className="text-sm text-gray-700">LAMA form signed by patient</span>
      </label>
      <label className="flex items-center gap-2 px-3 py-2 border rounded-lg cursor-pointer hover:bg-gray-50">
        <input type="checkbox" className="rounded border-gray-300 text-blue-600" /><span className="text-sm text-gray-700">Witnessed by second clinician</span>
      </label>
      <div>
        <label className="text-sm font-medium text-gray-700">Witness Name & Designation</label>
        <input className="w-full mt-1 px-3 py-2 text-sm border rounded-lg" placeholder="Name and role of witness" />
      </div>
    </div>
  );
}

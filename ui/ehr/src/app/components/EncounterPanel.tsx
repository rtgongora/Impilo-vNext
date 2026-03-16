"use client";

import { useState } from "react";
import { useEhrStore, type Encounter, type VitalSign, type Diagnosis, type Prescription } from "@/stores/ehrStore";

export function EncounterPanel({ encounter }: { encounter: Encounter }) {
  const { setActiveEncounter } = useEhrStore();
  const [activeTab, setActiveTab] = useState<"notes" | "vitals" | "dx" | "rx">("notes");
  const [noteText, setNoteText] = useState("");
  const [chiefComplaint, setChiefComplaint] = useState(encounter.chiefComplaint || "");

  function addNote() {
    if (!noteText.trim()) return;
    const updated = {
      ...encounter,
      notes: [...encounter.notes, noteText.trim()],
    };
    setActiveEncounter(updated);
    setNoteText("");
  }

  function updateChiefComplaint() {
    setActiveEncounter({ ...encounter, chiefComplaint });
  }

  function addVital(vital: VitalSign) {
    setActiveEncounter({
      ...encounter,
      vitals: [...encounter.vitals, vital],
    });
  }

  function addDiagnosis(dx: Diagnosis) {
    setActiveEncounter({
      ...encounter,
      diagnoses: [...encounter.diagnoses, dx],
    });
  }

  function addPrescription(rx: Prescription) {
    setActiveEncounter({
      ...encounter,
      prescriptions: [...encounter.prescriptions, rx],
    });
  }

  const tabs = [
    { key: "notes" as const, label: "Notes" },
    { key: "vitals" as const, label: "Vitals" },
    { key: "dx" as const, label: "Diagnoses" },
    { key: "rx" as const, label: "Prescriptions" },
  ];

  return (
    <div className="p-4 space-y-4">
      <div className="flex items-center justify-between">
        <h3 className="font-semibold">
          {encounter.encounterType} Encounter
        </h3>
        <span className="text-xs bg-blue-100 text-blue-700 px-2 py-0.5 rounded-full">
          {encounter.status}
        </span>
      </div>

      <div>
        <label className="text-xs text-gray-500 block mb-1">Chief Complaint</label>
        <input
          value={chiefComplaint}
          onChange={(e) => setChiefComplaint(e.target.value)}
          onBlur={updateChiefComplaint}
          className="w-full px-2 py-1 border rounded text-sm"
        />
      </div>

      <div className="flex border-b">
        {tabs.map((tab) => (
          <button
            key={tab.key}
            onClick={() => setActiveTab(tab.key)}
            className={`px-3 py-1.5 text-sm ${
              activeTab === tab.key
                ? "border-b-2 border-impilo-primary text-impilo-primary font-medium"
                : "text-gray-500"
            }`}
          >
            {tab.label}
          </button>
        ))}
      </div>

      {activeTab === "notes" && (
        <div className="space-y-2">
          <div className="flex gap-2">
            <textarea
              value={noteText}
              onChange={(e) => setNoteText(e.target.value)}
              className="flex-1 px-2 py-1 border rounded text-sm"
              rows={3}
              placeholder="Add clinical note..."
            />
          </div>
          <button
            onClick={addNote}
            className="px-3 py-1 bg-impilo-primary text-white text-sm rounded hover:bg-blue-800"
          >
            Add Note
          </button>
          {encounter.notes.map((note, i) => (
            <div key={i} className="bg-gray-50 p-2 rounded text-sm">
              {note}
            </div>
          ))}
        </div>
      )}

      {activeTab === "vitals" && (
        <VitalsForm onAdd={addVital} existing={encounter.vitals} />
      )}

      {activeTab === "dx" && (
        <DiagnosisForm onAdd={addDiagnosis} existing={encounter.diagnoses} />
      )}

      {activeTab === "rx" && (
        <PrescriptionForm onAdd={addPrescription} existing={encounter.prescriptions} />
      )}
    </div>
  );
}

function VitalsForm({ onAdd, existing }: { onAdd: (v: VitalSign) => void; existing: VitalSign[] }) {
  const [type, setType] = useState<VitalSign["type"]>("BP");
  const [value, setValue] = useState("");

  const units: Record<VitalSign["type"], string> = {
    BP: "mmHg", TEMP: "°C", HR: "bpm", RR: "/min", SPO2: "%",
    WEIGHT: "kg", HEIGHT: "cm", BMI: "kg/m²",
  };

  function submit() {
    if (!value.trim()) return;
    onAdd({ type, value, unit: units[type], recordedAt: new Date().toISOString() });
    setValue("");
  }

  return (
    <div className="space-y-2">
      <div className="flex gap-2">
        <select value={type} onChange={(e) => setType(e.target.value as VitalSign["type"])} className="border rounded px-2 py-1 text-sm">
          {Object.keys(units).map((k) => <option key={k} value={k}>{k}</option>)}
        </select>
        <input value={value} onChange={(e) => setValue(e.target.value)} className="flex-1 border rounded px-2 py-1 text-sm" placeholder="Value" />
        <button onClick={submit} className="px-3 py-1 bg-impilo-secondary text-white text-sm rounded">Add</button>
      </div>
      {existing.map((v, i) => (
        <div key={i} className="text-sm flex justify-between bg-gray-50 p-2 rounded">
          <span className="font-medium">{v.type}</span>
          <span>{v.value} {v.unit}</span>
        </div>
      ))}
    </div>
  );
}

function DiagnosisForm({ onAdd, existing }: { onAdd: (d: Diagnosis) => void; existing: Diagnosis[] }) {
  const [code, setCode] = useState("");
  const [display, setDisplay] = useState("");
  const [type, setType] = useState<Diagnosis["type"]>("PRIMARY");

  function submit() {
    if (!code.trim() || !display.trim()) return;
    onAdd({ code, display, type });
    setCode(""); setDisplay("");
  }

  return (
    <div className="space-y-2">
      <div className="flex gap-2">
        <input value={code} onChange={(e) => setCode(e.target.value)} className="w-24 border rounded px-2 py-1 text-sm" placeholder="ICD-10" />
        <input value={display} onChange={(e) => setDisplay(e.target.value)} className="flex-1 border rounded px-2 py-1 text-sm" placeholder="Diagnosis" />
        <select value={type} onChange={(e) => setType(e.target.value as Diagnosis["type"])} className="border rounded px-2 py-1 text-sm">
          <option value="PRIMARY">Primary</option>
          <option value="SECONDARY">Secondary</option>
          <option value="DIFFERENTIAL">Differential</option>
        </select>
        <button onClick={submit} className="px-3 py-1 bg-impilo-secondary text-white text-sm rounded">Add</button>
      </div>
      {existing.map((d, i) => (
        <div key={i} className="text-sm bg-gray-50 p-2 rounded">
          <span className="font-mono text-xs bg-gray-200 px-1 rounded mr-2">{d.code}</span>
          <span>{d.display}</span>
          <span className="ml-2 text-xs text-gray-400">({d.type})</span>
        </div>
      ))}
    </div>
  );
}

function PrescriptionForm({ onAdd, existing }: { onAdd: (r: Prescription) => void; existing: Prescription[] }) {
  const [medication, setMedication] = useState("");
  const [dosage, setDosage] = useState("");
  const [frequency, setFrequency] = useState("");
  const [duration, setDuration] = useState("");

  function submit() {
    if (!medication.trim()) return;
    onAdd({ medication, dosage, frequency, duration, status: "ACTIVE" });
    setMedication(""); setDosage(""); setFrequency(""); setDuration("");
  }

  return (
    <div className="space-y-2">
      <div className="grid grid-cols-2 gap-2">
        <input value={medication} onChange={(e) => setMedication(e.target.value)} className="border rounded px-2 py-1 text-sm" placeholder="Medication" />
        <input value={dosage} onChange={(e) => setDosage(e.target.value)} className="border rounded px-2 py-1 text-sm" placeholder="Dosage" />
        <input value={frequency} onChange={(e) => setFrequency(e.target.value)} className="border rounded px-2 py-1 text-sm" placeholder="Frequency" />
        <input value={duration} onChange={(e) => setDuration(e.target.value)} className="border rounded px-2 py-1 text-sm" placeholder="Duration" />
      </div>
      <button onClick={submit} className="px-3 py-1 bg-impilo-secondary text-white text-sm rounded">Add Prescription</button>
      {existing.map((r, i) => (
        <div key={i} className="text-sm bg-gray-50 p-2 rounded">
          <div className="font-medium">{r.medication}</div>
          <div className="text-gray-500">{r.dosage} — {r.frequency} for {r.duration}</div>
        </div>
      ))}
    </div>
  );
}

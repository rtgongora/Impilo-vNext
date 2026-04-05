"use client";

/**
 * Multi-step Referral Package Builder — Lovable-aligned wizard for
 * creating comprehensive referral packages.
 *
 * Steps:
 * 1. Patient Context (read-only summary)
 * 2. Clinical Summary (conditions, allergies, meds)
 * 3. Referral Details (specialty, facility, urgency)
 * 4. Review & Submit
 */

import { useState } from "react";
import { Loader2, ChevronRight, ChevronLeft, CheckCircle2, User, Stethoscope, FileText, Send } from "lucide-react";

interface ReferralPackageBuilderProps {
  patientName: string;
  patientDob?: string;
  conditions: string[];
  allergies: string[];
  medications: string[];
  referralType: string;
  onSubmit: (data: {
    specialty: string;
    referred_to: string;
    referred_to_facility: string;
    reason: string;
    urgency: string;
    clinical_summary: string;
    referral_type: string;
  }) => void;
  onCancel: () => void;
  isPending: boolean;
}

const STEPS = [
  { label: "Patient", icon: User },
  { label: "Clinical", icon: Stethoscope },
  { label: "Details", icon: FileText },
  { label: "Submit", icon: Send },
];

export function ReferralPackageBuilder({
  patientName, patientDob, conditions, allergies, medications,
  referralType, onSubmit, onCancel, isPending,
}: ReferralPackageBuilderProps) {
  const [step, setStep] = useState(0);
  const [specialty, setSpecialty] = useState("");
  const [referredTo, setReferredTo] = useState("");
  const [facility, setFacility] = useState("");
  const [reason, setReason] = useState("");
  const [urgency, setUrgency] = useState("ROUTINE");
  const [summary, setSummary] = useState("");

  // Auto-build summary from clinical data
  const autoSummary = [
    conditions.length > 0 ? `Active conditions: ${conditions.join(", ")}` : "",
    allergies.length > 0 ? `Allergies: ${allergies.join(", ")}` : "",
    medications.length > 0 ? `Current medications: ${medications.join(", ")}` : "",
  ].filter(Boolean).join(". ");

  function handleNext() {
    if (step === 1 && !summary && autoSummary) setSummary(autoSummary);
    if (step < 3) setStep(step + 1);
  }

  function handleSubmit() {
    onSubmit({
      specialty, referred_to: referredTo, referred_to_facility: facility,
      reason, urgency, clinical_summary: summary || autoSummary,
      referral_type: referralType,
    });
  }

  return (
    <div className="bg-white rounded-lg border border-blue-200 p-5">
      {/* Step indicator */}
      <div className="flex items-center gap-1 mb-5">
        {STEPS.map((s, i) => {
          const Icon = s.icon;
          return (
            <div key={i} className="flex items-center gap-1">
              <div className={`flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-medium ${
                i === step ? "bg-blue-100 text-blue-700" : i < step ? "bg-green-100 text-green-700" : "bg-gray-100 text-gray-500"
              }`}>
                {i < step ? <CheckCircle2 className="w-3 h-3" /> : <Icon className="w-3 h-3" />}
                {s.label}
              </div>
              {i < STEPS.length - 1 && <ChevronRight className="w-3 h-3 text-gray-300" />}
            </div>
          );
        })}
      </div>

      {/* Step content */}
      {step === 0 && (
        <div className="space-y-3">
          <h4 className="text-sm font-semibold text-gray-900">Patient Context</h4>
          <div className="bg-gray-50 rounded-lg p-4 space-y-2">
            <p className="text-sm"><span className="text-gray-500">Patient:</span> <span className="font-medium">{patientName}</span></p>
            {patientDob && <p className="text-sm"><span className="text-gray-500">DOB:</span> {patientDob}</p>}
            <p className="text-sm"><span className="text-gray-500">Conditions:</span> {conditions.length > 0 ? conditions.join(", ") : "None recorded"}</p>
            <p className="text-sm"><span className="text-gray-500">Allergies:</span> {allergies.length > 0 ? allergies.join(", ") : "NKDA"}</p>
          </div>
        </div>
      )}

      {step === 1 && (
        <div className="space-y-3">
          <h4 className="text-sm font-semibold text-gray-900">Clinical Summary</h4>
          <p className="text-xs text-gray-500">Review and edit the auto-generated clinical summary for the referral package.</p>
          {autoSummary && (
            <div className="bg-blue-50 rounded-lg p-3 text-xs text-blue-700">
              <p className="font-medium mb-1">Auto-generated from patient data:</p>
              <p>{autoSummary}</p>
            </div>
          )}
          <textarea value={summary} onChange={(e) => setSummary(e.target.value)} rows={4}
            placeholder="Clinical summary for the receiving provider..."
            className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm" />
        </div>
      )}

      {step === 2 && (
        <div className="space-y-3">
          <h4 className="text-sm font-semibold text-gray-900">Referral Details</h4>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-xs font-medium text-gray-600 mb-1">Specialty</label>
              <input type="text" value={specialty} onChange={(e) => setSpecialty(e.target.value)}
                placeholder="e.g. Cardiology" className="w-full px-3 py-2 text-sm border border-gray-300 rounded-lg" />
            </div>
            <div>
              <label className="block text-xs font-medium text-gray-600 mb-1">Referred To</label>
              <input type="text" value={referredTo} onChange={(e) => setReferredTo(e.target.value)}
                placeholder="Provider name" className="w-full px-3 py-2 text-sm border border-gray-300 rounded-lg" />
            </div>
            <div>
              <label className="block text-xs font-medium text-gray-600 mb-1">Facility</label>
              <input type="text" value={facility} onChange={(e) => setFacility(e.target.value)}
                placeholder="Receiving facility" className="w-full px-3 py-2 text-sm border border-gray-300 rounded-lg" />
            </div>
            <div>
              <label className="block text-xs font-medium text-gray-600 mb-1">Urgency</label>
              <select value={urgency} onChange={(e) => setUrgency(e.target.value)}
                className="w-full px-3 py-2 text-sm border border-gray-300 rounded-lg">
                <option value="ROUTINE">Routine</option>
                <option value="URGENT">Urgent</option>
                <option value="EMERGENCY">Emergency</option>
              </select>
            </div>
          </div>
          <div>
            <label className="block text-xs font-medium text-gray-600 mb-1">Reason for Referral</label>
            <textarea value={reason} onChange={(e) => setReason(e.target.value)} rows={2}
              placeholder="Why is this referral needed?"
              className="w-full px-3 py-2 text-sm border border-gray-300 rounded-lg" />
          </div>
        </div>
      )}

      {step === 3 && (
        <div className="space-y-3">
          <h4 className="text-sm font-semibold text-gray-900">Review & Submit</h4>
          <div className="bg-gray-50 rounded-lg p-4 space-y-2 text-sm">
            <p><span className="text-gray-500">Type:</span> {referralType}</p>
            <p><span className="text-gray-500">Specialty:</span> {specialty || "—"}</p>
            <p><span className="text-gray-500">To:</span> {referredTo || "—"} at {facility || "—"}</p>
            <p><span className="text-gray-500">Urgency:</span> <span className={urgency === "EMERGENCY" ? "text-red-600 font-semibold" : urgency === "URGENT" ? "text-amber-600 font-semibold" : ""}>{urgency}</span></p>
            <p><span className="text-gray-500">Reason:</span> {reason || "—"}</p>
            <p className="text-xs text-gray-400 border-t pt-2 mt-2">{summary || autoSummary || "No clinical summary"}</p>
          </div>
        </div>
      )}

      {/* Navigation buttons */}
      <div className="flex justify-between mt-4 pt-3 border-t">
        <div>
          {step > 0 && (
            <button onClick={() => setStep(step - 1)} className="inline-flex items-center gap-1 px-3 py-1.5 text-sm text-gray-600 hover:text-gray-900">
              <ChevronLeft className="w-4 h-4" /> Back
            </button>
          )}
        </div>
        <div className="flex gap-2">
          <button onClick={onCancel} className="px-3 py-1.5 text-sm text-gray-500 hover:text-gray-700">Cancel</button>
          {step < 3 ? (
            <button onClick={handleNext} className="inline-flex items-center gap-1 px-4 py-1.5 text-sm font-medium bg-blue-600 text-white rounded-lg hover:bg-blue-700">
              Next <ChevronRight className="w-4 h-4" />
            </button>
          ) : (
            <button onClick={handleSubmit} disabled={isPending || !specialty}
              className="inline-flex items-center gap-1 px-4 py-1.5 text-sm font-medium bg-green-600 text-white rounded-lg hover:bg-green-700 disabled:opacity-50">
              {isPending ? <Loader2 className="w-4 h-4 animate-spin" /> : <Send className="w-4 h-4" />} Submit Referral
            </button>
          )}
        </div>
      </div>
    </div>
  );
}

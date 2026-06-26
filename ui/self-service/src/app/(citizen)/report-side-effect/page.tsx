"use client";

/**
 * Citizen / caregiver medicine side-effect report (pharmacovigilance).
 * Route: /report-side-effect
 *
 * Plain-language guided flow that submits to the public BFF endpoint
 * (/v1/public/patient-safety/reports), which opens a regulatory safety case for MCAZ review.
 * Honest: we never claim the report was sent to WHO-UMC/VigiFlow — that link-out is separate.
 */

import { useState } from "react";
import { Card } from "shared-ui";

interface Receipt {
  referenceCode: string | null;
  caseReference: string | null;
}

export default function ReportSideEffectPage() {
  const [reporterKind, setReporterKind] = useState<"CITIZEN" | "CAREGIVER">("CITIZEN");
  const [medicineName, setMedicineName] = useState("");
  const [reaction, setReaction] = useState("");
  const [severity, setSeverity] = useState<"MILD" | "MODERATE" | "SEVERE">("MODERATE");
  const [contactPhone, setContactPhone] = useState("");
  const [subjectInitials, setSubjectInitials] = useState("");

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [receipt, setReceipt] = useState<Receipt | null>(null);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError("");
    if (!medicineName.trim()) {
      setError("Please tell us the name of the medicine or vaccine.");
      return;
    }
    setLoading(true);
    try {
      const res = await fetch("/v1/public/patient-safety/reports", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          reportType: "ADR",
          reporterKind,
          subjectIsSelf: reporterKind === "CITIZEN",
          subjectInitials: subjectInitials || null,
          reporterPhone: contactPhone || null,
          narrative: reaction || null,
          products: [{ productName: medicineName }],
          events: reaction ? [{ term: reaction, severity }] : [],
        }),
      });
      if (res.ok) {
        const json = await res.json().catch(() => null);
        setReceipt(json?.data ?? { referenceCode: null, caseReference: null });
      } else {
        const err = await res.json().catch(() => null);
        setError(err?.message ?? "Sorry, we could not submit your report. Please try again.");
      }
    } catch {
      setError("Network error. Please check your connection and try again.");
    } finally {
      setLoading(false);
    }
  }

  if (receipt) {
    return (
      <div className="mx-auto max-w-xl">
        <h1 className="mb-2 text-2xl font-bold">Thank you</h1>
        <Card className="p-6 text-center">
          <p className="text-lg font-semibold text-green-700">Your report has been received</p>
          {receipt.referenceCode && (
            <p className="mt-2 text-sm text-neutral-600">
              Reference: <span className="font-medium">{receipt.referenceCode}</span>
            </p>
          )}
          <p className="mt-3 text-sm text-neutral-500">
            A safety case has been opened for review by the Medicines Control Authority of Zimbabwe (MCAZ). They may
            contact you if more information is needed.
          </p>
          <button
            onClick={() => {
              setReceipt(null);
              setMedicineName("");
              setReaction("");
            }}
            className="mt-5 rounded-lg bg-brand-primary px-4 py-2 text-sm font-medium text-white"
          >
            Report another
          </button>
        </Card>
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-xl">
      <h1 className="mb-2 text-2xl font-bold">Report a medicine side effect</h1>
      <p className="mb-6 text-neutral-500">
        Help keep medicines and vaccines safe. Tell us what happened — it only takes a minute.
      </p>

      <Card className="p-4">
        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className="mb-1 block text-sm font-medium text-neutral-700">Who is this report about?</label>
            <div className="flex gap-2">
              {(["CITIZEN", "CAREGIVER"] as const).map((k) => (
                <button
                  type="button"
                  key={k}
                  onClick={() => setReporterKind(k)}
                  className={`rounded-lg border px-3 py-2 text-sm ${
                    reporterKind === k ? "border-brand-primary bg-brand-primary/10" : "border-neutral-300"
                  }`}
                >
                  {k === "CITIZEN" ? "Myself" : "Someone I care for"}
                </button>
              ))}
            </div>
          </div>

          <Text label="Medicine or vaccine name" value={medicineName} onChange={setMedicineName} placeholder="e.g. Amoxicillin" required />

          <div>
            <label className="mb-1 block text-sm font-medium text-neutral-700">What happened?</label>
            <textarea
              value={reaction}
              onChange={(e) => setReaction(e.target.value)}
              rows={3}
              placeholder="Describe the side effect…"
              className="w-full rounded-lg border border-neutral-300 px-3 py-2 text-sm"
            />
          </div>

          <div>
            <label className="mb-1 block text-sm font-medium text-neutral-700">How bad was it?</label>
            <select
              value={severity}
              onChange={(e) => setSeverity(e.target.value as typeof severity)}
              className="w-full rounded-lg border border-neutral-300 px-3 py-2 text-sm"
            >
              <option value="MILD">Mild — uncomfortable but manageable</option>
              <option value="MODERATE">Moderate — affected daily activities</option>
              <option value="SEVERE">Severe — could not work or function</option>
            </select>
          </div>

          {reporterKind === "CAREGIVER" && (
            <Text label="Patient initials (optional)" value={subjectInitials} onChange={setSubjectInitials} placeholder="A.B." />
          )}
          <Text label="Your phone (optional, so MCAZ can follow up)" value={contactPhone} onChange={setContactPhone} placeholder="+263…" />

          {error && <div className="rounded-lg bg-red-50 p-3 text-sm text-red-800">{error}</div>}

          <button
            type="submit"
            disabled={loading}
            className="w-full rounded-lg bg-brand-primary px-4 py-2 text-sm font-medium text-white disabled:opacity-50"
          >
            {loading ? "Submitting…" : "Submit report"}
          </button>

          <p className="text-center text-xs text-neutral-400">
            This sends your report to MCAZ through Impilo. It is not the same as the external WHO-UMC VigiFlow form.
          </p>
        </form>
      </Card>
    </div>
  );
}

function Text({
  label,
  value,
  onChange,
  placeholder,
  required,
}: {
  label: string;
  value: string;
  onChange: (v: string) => void;
  placeholder?: string;
  required?: boolean;
}) {
  return (
    <div>
      <label className="mb-1 block text-sm font-medium text-neutral-700">{label}</label>
      <input
        type="text"
        value={value}
        onChange={(e) => onChange(e.target.value)}
        placeholder={placeholder}
        required={required}
        className="w-full rounded-lg border border-neutral-300 px-3 py-2 text-sm"
      />
    </div>
  );
}

"use client";

/**
 * New safety report — provider ADR/AEFI capture.
 * Route: /work/patient-safety/new
 *
 * Captures report type, subject, the implicated product (drug/vaccine), the reaction and
 * seriousness, then creates + submits the report and shows the receipt (reference + opened case).
 */

import { useState } from "react";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { ArrowLeft, CheckCircle2, Loader2 } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { apiClient } from "@/lib/api-client";

type ReportType = "ADR" | "AEFI";

const SERIOUSNESS_REASONS = [
  "DEATH",
  "LIFE_THREATENING",
  "HOSPITALISATION",
  "DISABILITY",
  "CONGENITAL_ANOMALY",
  "OTHER_MEDICALLY_IMPORTANT",
];

const OUTCOMES = ["RECOVERED", "RECOVERING", "NOT_RECOVERED", "RECOVERED_WITH_SEQUELAE", "FATAL", "UNKNOWN"];

interface Receipt {
  referenceCode: string | null;
  caseReference: string | null;
  status: string;
}

export default function NewSafetyReportPage() {
  const router = useRouter();
  const searchParams = useSearchParams();

  const [reportType, setReportType] = useState<ReportType>((searchParams.get("type") as ReportType) || "ADR");
  const [subjectCpid, setSubjectCpid] = useState(searchParams.get("cpid") ?? "");
  const [subjectInitials, setSubjectInitials] = useState("");
  const [subjectSex, setSubjectSex] = useState("");
  const [encounterId, setEncounterId] = useState(searchParams.get("encounterId") ?? "");
  const [narrative, setNarrative] = useState("");

  const [productName, setProductName] = useState("");
  const [batchNumber, setBatchNumber] = useState("");
  const [route, setRoute] = useState("");
  const [doseNumber, setDoseNumber] = useState("");

  const [reactionTerm, setReactionTerm] = useState("");
  const [severity, setSeverity] = useState("MODERATE");
  const [outcome, setOutcome] = useState("RECOVERING");

  const [serious, setSerious] = useState(false);
  const [reasons, setReasons] = useState<string[]>([]);

  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [receipt, setReceipt] = useState<Receipt | null>(null);

  const isVaccine = reportType === "AEFI";

  function toggleReason(r: string) {
    setReasons((prev) => (prev.includes(r) ? prev.filter((x) => x !== r) : [...prev, r]));
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    if (!productName.trim()) {
      setError("Name the implicated medicine or vaccine.");
      return;
    }
    setSubmitting(true);
    try {
      const created = await apiClient.post<{ data: { id: string } }>(`/internal/v1/patient-safety/reports`, {
        reportType,
        reporterKind: "PROVIDER",
        sourceContext: isVaccine ? "VACCINATION" : "CLINICAL_ENCOUNTER",
        subjectCpid: subjectCpid || null,
        subjectInitials: subjectInitials || null,
        subjectSex: subjectSex || null,
        encounterId: encounterId || null,
        narrative: narrative || null,
        serious,
        seriousnessReasons: serious ? reasons : [],
        products: [
          {
            productName,
            productKind: isVaccine ? "VACCINE" : "DRUG",
            batchNumber: batchNumber || null,
            route: route || null,
            doseNumber: isVaccine ? doseNumber || null : null,
          },
        ],
        events: reactionTerm ? [{ term: reactionTerm, severity, outcome }] : [],
      });
      const reportId = created.data.id;
      const submitted = await apiClient.post<{ data: Receipt }>(
        `/internal/v1/patient-safety/reports/${reportId}/submit`,
      );
      setReceipt(submitted.data);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Submission failed. Please review and try again.");
    } finally {
      setSubmitting(false);
    }
  }

  if (receipt) {
    return (
      <AppLayout>
        <PageShell title="Report submitted" subtitle="Your safety report has been recorded">
          <div className="mx-auto max-w-xl rounded-lg border border-green-200 bg-green-50 p-6 text-center">
            <CheckCircle2 className="mx-auto mb-3 h-12 w-12 text-green-600" />
            <p className="text-lg font-semibold">Reference {receipt.referenceCode}</p>
            <p className="mt-1 text-sm text-muted-foreground">
              A regulatory safety case ({receipt.caseReference}) has been opened for MCAZ review. Status:{" "}
              {receipt.status}.
            </p>
            <div className="mt-5 flex justify-center gap-3">
              <Link
                href="/work/patient-safety"
                className="rounded-lg border border-border px-4 py-2 text-sm font-medium hover:bg-muted"
              >
                Back to safety
              </Link>
              <button
                onClick={() => router.refresh()}
                className="rounded-lg bg-primary px-4 py-2 text-sm font-medium text-white hover:bg-primary-hover"
              >
                Report another
              </button>
            </div>
          </div>
        </PageShell>
      </AppLayout>
    );
  }

  return (
    <AppLayout>
      <PageShell title="New safety report" subtitle="Adverse drug reaction (ADR) or adverse event following immunisation (AEFI)">
        <Link
          href="/work/patient-safety"
          className="mb-4 inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground"
        >
          <ArrowLeft className="h-4 w-4" /> Back
        </Link>

        <form onSubmit={handleSubmit} className="max-w-2xl space-y-6">
          <div>
            <label className="mb-1 block text-sm font-medium">Report type</label>
            <div className="flex gap-2">
              {(["ADR", "AEFI"] as ReportType[]).map((t) => (
                <button
                  type="button"
                  key={t}
                  onClick={() => setReportType(t)}
                  className={`rounded-lg border px-4 py-2 text-sm font-medium ${
                    reportType === t ? "border-primary bg-primary/10 text-primary" : "border-border hover:bg-muted"
                  }`}
                >
                  {t === "ADR" ? "ADR — medicine reaction" : "AEFI — vaccine event"}
                </button>
              ))}
            </div>
          </div>

          <fieldset className="space-y-3 rounded-lg border border-border p-4">
            <legend className="px-1 text-sm font-semibold">Patient</legend>
            <div className="grid gap-3 sm:grid-cols-2">
              <Field label="Patient ID (CPID)" value={subjectCpid} onChange={setSubjectCpid} placeholder="CPID-…" />
              <Field label="Initials" value={subjectInitials} onChange={setSubjectInitials} placeholder="A.B." />
              <Field label="Sex" value={subjectSex} onChange={setSubjectSex} placeholder="FEMALE / MALE" />
              <Field label="Encounter ID" value={encounterId} onChange={setEncounterId} placeholder="optional" />
            </div>
          </fieldset>

          <fieldset className="space-y-3 rounded-lg border border-border p-4">
            <legend className="px-1 text-sm font-semibold">{isVaccine ? "Vaccine" : "Suspect medicine"}</legend>
            <Field
              label={isVaccine ? "Vaccine name" : "Medicine name"}
              value={productName}
              onChange={setProductName}
              placeholder={isVaccine ? "e.g. Measles vaccine" : "e.g. Amoxicillin 500mg"}
              required
            />
            <div className="grid gap-3 sm:grid-cols-2">
              <Field label="Batch / lot number" value={batchNumber} onChange={setBatchNumber} placeholder="optional" />
              {isVaccine ? (
                <Field label="Dose number" value={doseNumber} onChange={setDoseNumber} placeholder="1 / 2 / booster" />
              ) : (
                <Field label="Route" value={route} onChange={setRoute} placeholder="Oral / IV" />
              )}
            </div>
          </fieldset>

          <fieldset className="space-y-3 rounded-lg border border-border p-4">
            <legend className="px-1 text-sm font-semibold">Reaction</legend>
            <Field label="Reaction / event" value={reactionTerm} onChange={setReactionTerm} placeholder="e.g. Generalised rash" />
            <div className="grid gap-3 sm:grid-cols-2">
              <Select label="Severity" value={severity} onChange={setSeverity} options={["MILD", "MODERATE", "SEVERE"]} />
              <Select label="Outcome" value={outcome} onChange={setOutcome} options={OUTCOMES} />
            </div>
            <label className="mt-1 block">
              <span className="mb-1 block text-sm font-medium">Narrative</span>
              <textarea
                value={narrative}
                onChange={(e) => setNarrative(e.target.value)}
                rows={3}
                className="w-full rounded-lg border border-border px-3 py-2 text-sm"
                placeholder="Describe the event, timing and clinical course…"
              />
            </label>
          </fieldset>

          <fieldset className="space-y-3 rounded-lg border border-border p-4">
            <legend className="px-1 text-sm font-semibold">Seriousness</legend>
            <label className="flex items-center gap-2 text-sm">
              <input type="checkbox" checked={serious} onChange={(e) => setSerious(e.target.checked)} />
              This is a serious reaction
            </label>
            {serious && (
              <div className="flex flex-wrap gap-2">
                {SERIOUSNESS_REASONS.map((r) => (
                  <button
                    type="button"
                    key={r}
                    onClick={() => toggleReason(r)}
                    className={`rounded-full border px-3 py-1 text-xs ${
                      reasons.includes(r) ? "border-red-300 bg-red-50 text-red-800" : "border-border hover:bg-muted"
                    }`}
                  >
                    {r.replace(/_/g, " ")}
                  </button>
                ))}
              </div>
            )}
          </fieldset>

          {error && <div className="rounded-lg bg-red-50 p-3 text-sm text-red-800">{error}</div>}

          <button
            type="submit"
            disabled={submitting}
            className="inline-flex items-center gap-2 rounded-lg bg-primary px-5 py-2.5 text-sm font-medium text-white hover:bg-primary-hover disabled:opacity-50"
          >
            {submitting && <Loader2 className="h-4 w-4 animate-spin" />}
            {submitting ? "Submitting…" : "Submit safety report"}
          </button>
        </form>
      </PageShell>
    </AppLayout>
  );
}

function Field({
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
    <label className="block">
      <span className="mb-1 block text-sm font-medium">{label}</span>
      <input
        type="text"
        value={value}
        onChange={(e) => onChange(e.target.value)}
        placeholder={placeholder}
        required={required}
        className="w-full rounded-lg border border-border px-3 py-2 text-sm"
      />
    </label>
  );
}

function Select({
  label,
  value,
  onChange,
  options,
}: {
  label: string;
  value: string;
  onChange: (v: string) => void;
  options: string[];
}) {
  return (
    <label className="block">
      <span className="mb-1 block text-sm font-medium">{label}</span>
      <select
        value={value}
        onChange={(e) => onChange(e.target.value)}
        className="w-full rounded-lg border border-border px-3 py-2 text-sm"
      >
        {options.map((o) => (
          <option key={o} value={o}>
            {o.replace(/_/g, " ")}
          </option>
        ))}
      </select>
    </label>
  );
}

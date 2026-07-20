"use client";

import { useState } from "react";
import Link from "next/link";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import {
  useExtractTemplate,
  useEnrollTemplate,
  fileToBase64,
  type Modality,
  type EnrollResult,
} from "@/hooks/queries/useAbisBiometric";
import { UserPlus, ShieldCheck, AlertTriangle, Loader2 } from "lucide-react";

/**
 * Biometric ENROLMENT onboarding — the governed capture → extract → dedup → enrol flow.
 * Enrol-time deduplication runs server-side (1:N against the tenant gallery): if a probable
 * duplicate is found, a duplicate-investigation case is OPENED for human adjudication. The
 * enrolment still lands (care-first) and is NEVER auto-merged — a reviewer decides, and any
 * merge is a separate dual-control action in the adjudication console.
 * (Browser capture = image upload; a scanner/device is an EXTERNAL_INTEGRATION follow-on.)
 */
export default function BiometricOnboardWorkspace() {
  const [modality, setModality] = useState<Modality>("FINGERPRINT");
  const [subjectRef, setSubjectRef] = useState("");
  const [template, setTemplate] = useState<string | null>(null);
  const [quality, setQuality] = useState<number | null>(null);
  const [result, setResult] = useState<EnrollResult | null>(null);
  const [error, setError] = useState<string | null>(null);

  const extract = useExtractTemplate();
  const enroll = useEnrollTemplate();

  async function onCaptureFile(file: File) {
    setError(null);
    setResult(null);
    setTemplate(null);
    try {
      const sampleBase64 = await fileToBase64(file);
      const ex = await extract.mutateAsync({ modality, sampleBase64 });
      if (!ex.ok || !ex.templateBase64) {
        setError(`Extraction failed: ${ex.detail ?? "no template"}`);
        return;
      }
      setTemplate(ex.templateBase64);
      setQuality(typeof ex.quality === "number" ? ex.quality : null);
    } catch (e) {
      setError(String((e as Error).message ?? e));
    }
  }

  async function doEnrol() {
    if (!subjectRef.trim() || !template) return;
    setError(null);
    try {
      const r = await enroll.mutateAsync({ subjectRef: subjectRef.trim(), modality, templateBase64: template });
      setResult(r);
    } catch (e) {
      setError(String((e as Error).message ?? e));
    }
  }

  const busy = extract.isPending || enroll.isPending;
  const dup = result?.duplicateReview;

  return (
    <AppLayout>
      <PageShell
        title="Biometric enrolment onboarding"
        subtitle="Capture → extract → dedup → enrol. Duplicates open an adjudication case; enrolment is never auto-merged."
        icon={<UserPlus className="h-5 w-5" />}
      >
        <div className="max-w-2xl space-y-6">
          <div className="flex gap-3 items-end">
            <label className="flex-1">
              <span className="block text-sm font-medium mb-1">Subject reference (opaque)</span>
              <input
                data-testid="subject-ref"
                className="w-full rounded-md border px-3 py-2 bg-background"
                placeholder="e.g. subject-0421"
                value={subjectRef}
                onChange={(e) => setSubjectRef(e.target.value)}
              />
            </label>
            <label>
              <span className="block text-sm font-medium mb-1">Modality</span>
              <select
                data-testid="modality"
                className="rounded-md border px-3 py-2 bg-background"
                value={modality}
                onChange={(e) => setModality(e.target.value as Modality)}
              >
                <option value="FINGERPRINT">Fingerprint</option>
                <option value="FACE">Face</option>
                <option value="IRIS">Iris</option>
              </select>
            </label>
          </div>

          <section className="rounded-lg border p-4 space-y-3">
            <h3 className="font-semibold">1 · Capture &amp; extract</h3>
            <input
              data-testid="capture-file"
              type="file"
              accept="image/*"
              onChange={(e) => e.target.files?.[0] && onCaptureFile(e.target.files[0])}
            />
            {extract.isPending && (
              <p className="text-sm flex items-center gap-1"><Loader2 className="h-4 w-4 animate-spin" /> Extracting…</p>
            )}
            {template && (
              <p data-testid="extracted-ok" className="text-sm text-green-600">
                Template extracted{quality != null ? ` (quality ${quality})` : ""}.
              </p>
            )}
          </section>

          <section className="rounded-lg border p-4 space-y-3">
            <h3 className="font-semibold">2 · Dedup &amp; enrol</h3>
            <button
              data-testid="enrol-btn"
              className="rounded-md bg-primary text-primary-foreground px-4 py-2 disabled:opacity-50"
              onClick={doEnrol}
              disabled={!subjectRef.trim() || !template || busy}
            >
              {enroll.isPending ? "Enrolling…" : "Run dedup & enrol"}
            </button>

            {result && !dup && (
              <p data-testid="enrolled-clean" className="text-sm text-green-600 flex items-center gap-1">
                <ShieldCheck className="h-4 w-4" /> Enrolled under {result.subjectRef} (version {result.version ?? 1}) — no duplicates found.
              </p>
            )}

            {dup && (
              <div data-testid="duplicate-review" className="rounded-md border border-amber-400 bg-amber-50 dark:bg-amber-950/30 p-3 text-sm space-y-1">
                <p className="text-amber-700 dark:text-amber-400 flex items-center gap-1 font-semibold">
                  <AlertTriangle className="h-4 w-4" /> Possible duplicate — case opened for adjudication
                </p>
                <p>
                  {dup.candidateCount} candidate{dup.candidateCount === 1 ? "" : "s"}
                  {dup.topScore != null ? ` · top score ${(dup.topScore * 100).toFixed(1)}%` : ""}.
                  The enrolment landed (care-first); a reviewer must adjudicate — merges are never automatic.
                </p>
                <Link
                  data-testid="review-link"
                  href={`/operations/vito/adjudication/${dup.caseId}`}
                  className="text-primary underline"
                >
                  Open case #{dup.caseId} in the adjudication console →
                </Link>
              </div>
            )}
          </section>

          {error && <p data-testid="error" className="text-sm text-red-600">{error}</p>}
        </div>
      </PageShell>
    </AppLayout>
  );
}

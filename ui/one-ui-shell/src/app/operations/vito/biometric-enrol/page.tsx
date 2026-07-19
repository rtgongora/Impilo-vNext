"use client";

import { useState } from "react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import {
  useExtractTemplate,
  useEnrollTemplate,
  useVerifyTemplate,
  fileToBase64,
  type Modality,
  type VerifyResult,
} from "@/hooks/queries/useAbisBiometric";
import { Fingerprint, ShieldCheck, ShieldX, Loader2 } from "lucide-react";

/**
 * Biometric enrol → verify workspace — the end-to-end vertical slice you can
 * navigate in a browser: capture a fingerprint image, enrol it under a subject,
 * then verify a fresh capture and see a REAL match decision from the engine.
 * (A browser has no scanner, so capture = image upload; a scanner/device is an
 * EXTERNAL_INTEGRATION follow-on.)
 */
export default function BiometricEnrolWorkspace() {
  const [modality, setModality] = useState<Modality>("FINGERPRINT");
  const [subjectRef, setSubjectRef] = useState("");
  const [enrolTemplate, setEnrolTemplate] = useState<string | null>(null);
  const [enrolled, setEnrolled] = useState(false);
  const [verifyResult, setVerifyResult] = useState<VerifyResult | null>(null);
  const [error, setError] = useState<string | null>(null);

  const extract = useExtractTemplate();
  const enroll = useEnrollTemplate();
  const verify = useVerifyTemplate();

  async function onEnrolFile(file: File) {
    setError(null);
    setEnrolled(false);
    try {
      const sampleBase64 = await fileToBase64(file);
      const ex = await extract.mutateAsync({ modality, sampleBase64 });
      if (!ex.ok || !ex.templateBase64) {
        setError(`Extraction failed: ${ex.detail ?? "no template"}`);
        return;
      }
      setEnrolTemplate(ex.templateBase64);
    } catch (e) {
      setError(String((e as Error).message ?? e));
    }
  }

  async function doEnrol() {
    if (!subjectRef.trim() || !enrolTemplate) return;
    setError(null);
    try {
      await enroll.mutateAsync({ subjectRef: subjectRef.trim(), modality, templateBase64: enrolTemplate });
      setEnrolled(true);
    } catch (e) {
      setError(String((e as Error).message ?? e));
    }
  }

  async function onVerifyFile(file: File) {
    if (!subjectRef.trim()) {
      setError("Enrol a subject first.");
      return;
    }
    setError(null);
    setVerifyResult(null);
    try {
      const sampleBase64 = await fileToBase64(file);
      const ex = await extract.mutateAsync({ modality, sampleBase64 });
      if (!ex.ok || !ex.templateBase64) {
        setError(`Extraction failed: ${ex.detail ?? "no template"}`);
        return;
      }
      const result = await verify.mutateAsync({
        subjectRef: subjectRef.trim(),
        modality,
        probeBase64: ex.templateBase64,
      });
      setVerifyResult(result);
    } catch (e) {
      setError(String((e as Error).message ?? e));
    }
  }

  const busy = extract.isPending || enroll.isPending || verify.isPending;

  return (
    <AppLayout>
      <PageShell
        title="Biometric enrolment & verification"
        subtitle="Capture → enrol → verify a fingerprint end-to-end against the real matching engine."
        icon={<Fingerprint className="h-5 w-5" />}
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

          {/* Step 1 — enrol */}
          <section className="rounded-lg border p-4 space-y-3">
            <h3 className="font-semibold">1 · Enrol a capture</h3>
            <input
              data-testid="enrol-file"
              type="file"
              accept="image/*"
              onChange={(e) => e.target.files?.[0] && onEnrolFile(e.target.files[0])}
            />
            {enrolTemplate && !enrolled && (
              <button
                data-testid="enrol-btn"
                className="rounded-md bg-primary text-primary-foreground px-4 py-2 disabled:opacity-50"
                onClick={doEnrol}
                disabled={!subjectRef.trim() || busy}
              >
                {enroll.isPending ? "Enrolling…" : "Enrol this template"}
              </button>
            )}
            {enrolled && (
              <p data-testid="enrolled-ok" className="text-sm text-green-600 flex items-center gap-1">
                <ShieldCheck className="h-4 w-4" /> Enrolled under {subjectRef}
              </p>
            )}
          </section>

          {/* Step 2 — verify */}
          <section className="rounded-lg border p-4 space-y-3">
            <h3 className="font-semibold">2 · Verify a fresh capture</h3>
            <input
              data-testid="verify-file"
              type="file"
              accept="image/*"
              disabled={!enrolled}
              onChange={(e) => e.target.files?.[0] && onVerifyFile(e.target.files[0])}
            />
            {verify.isPending && (
              <p className="text-sm flex items-center gap-1"><Loader2 className="h-4 w-4 animate-spin" /> Matching…</p>
            )}
            {verifyResult && (
              <div data-testid="verify-result" className="text-sm">
                {verifyResult.result === "MATCH" ? (
                  <p className="text-green-600 flex items-center gap-1 font-semibold">
                    <ShieldCheck className="h-5 w-5" /> MATCH — confidence {(verifyResult.confidence * 100).toFixed(1)}%
                  </p>
                ) : (
                  <p className="text-red-600 flex items-center gap-1 font-semibold">
                    <ShieldX className="h-5 w-5" /> {verifyResult.result} — confidence{" "}
                    {(verifyResult.confidence * 100).toFixed(1)}%
                  </p>
                )}
                {verifyResult.summary && <p className="text-muted-foreground mt-1">{verifyResult.summary}</p>}
              </div>
            )}
          </section>

          {error && <p data-testid="error" className="text-sm text-red-600">{error}</p>}
          <p className="text-xs text-muted-foreground">
            Capture is image upload (a browser has no scanner). Matching is real: the engine extracts
            minutiae/embeddings and scores similarity — a matching capture MATCHes, a different one does not.
          </p>
        </div>
      </PageShell>
    </AppLayout>
  );
}

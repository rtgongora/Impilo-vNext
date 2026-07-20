"use client";

import { useState } from "react";
import Link from "next/link";
import { Fingerprint, ShieldCheck, AlertTriangle, Loader2 } from "lucide-react";
import {
  useExtractTemplate,
  useEnrollTemplate,
  fileToBase64,
  type Modality,
  type EnrollResult,
} from "@/hooks/queries/useAbisBiometric";

/**
 * Inline biometric ENROLMENT step — "register this patient's biometric".
 *
 * Distinct from {@link BiometricVerifyField} (which VERIFIES a returning patient
 * at check-in): this ENROLS a subject the first time, running the governed
 * capture → extract → dedup → enrol pipeline. The `subjectRef` is the patient's
 * CPID (never their Health ID).
 *
 * Care-first: a brand-new patient with no prior enrolment normally enrols
 * cleanly. Server-side quality gating rejects a sub-quality capture (surfaced
 * honestly as an error, never a fake success). Server-side dedup (1:N against
 * the tenant gallery) may OPEN a duplicate-investigation case — the enrolment
 * still lands and is NEVER auto-merged; a reviewer adjudicates the case.
 * (A browser has no scanner, so capture = image upload; a device scanner is an
 * EXTERNAL_INTEGRATION follow-on.)
 */
export function EnrolBiometricStep({
  subjectRef,
  subjectLabel,
  defaultModality = "FINGERPRINT",
  disabled,
  onEnrolled,
}: {
  /** Opaque subject reference to enrol under — a patient's CPID. */
  subjectRef: string;
  /** Optional human label for the subject (shown in the heading). */
  subjectLabel?: string;
  defaultModality?: Modality;
  disabled?: boolean;
  /** Called when an enrolment lands (with or without a duplicate case). */
  onEnrolled?: (result: EnrollResult) => void;
}) {
  const [modality, setModality] = useState<Modality>(defaultModality);
  const [position, setPosition] = useState("");
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
    setQuality(null);
    try {
      const sampleBase64 = await fileToBase64(file);
      const ex = await extract.mutateAsync({ modality, sampleBase64 });
      if (!ex.ok || !ex.templateBase64) {
        setError(
          `Could not read a ${modality.toLowerCase()} from that capture${ex.detail ? ` — ${ex.detail}` : ""}. Retry with a clearer capture.`,
        );
        return;
      }
      setTemplate(ex.templateBase64);
      setQuality(typeof ex.quality === "number" ? ex.quality : null);
    } catch (e) {
      setError(messageOf(e));
    }
  }

  async function doEnrol() {
    if (!subjectRef.trim() || !template) return;
    setError(null);
    try {
      const r = await enroll.mutateAsync({
        subjectRef: subjectRef.trim(),
        modality,
        templateBase64: template,
        position: modality === "FINGERPRINT" && position.trim() ? position.trim() : undefined,
      });
      setResult(r);
      onEnrolled?.(r);
    } catch (e) {
      // Server-side quality gating (422) and other failures surface honestly —
      // never a fabricated success.
      setError(messageOf(e));
    }
  }

  const busy = extract.isPending || enroll.isPending;
  const dup = result?.duplicateReview;

  return (
    <div className="rounded-lg border p-4 space-y-3" data-testid="enrol-biometric-step">
      <div className="flex items-center justify-between gap-3">
        <span className="text-sm font-medium flex items-center gap-1.5">
          <Fingerprint className="h-4 w-4" /> Register this patient&apos;s biometric
          {subjectLabel ? ` — ${subjectLabel}` : ""}
        </span>
        <select
          data-testid="enrol-modality"
          className="rounded-md border px-2 py-1 text-xs bg-background"
          value={modality}
          onChange={(e) => setModality(e.target.value as Modality)}
          disabled={disabled || busy}
          aria-label="Biometric modality"
        >
          <option value="FINGERPRINT">Fingerprint</option>
          <option value="FACE">Face</option>
          <option value="IRIS">Iris</option>
        </select>
      </div>

      {modality === "FINGERPRINT" && (
        <label className="block">
          <span className="block text-xs text-muted-foreground mb-1">Finger position (optional)</span>
          <input
            data-testid="enrol-position"
            className="w-full rounded-md border px-3 py-1.5 text-sm bg-background"
            placeholder="e.g. RIGHT_THUMB"
            value={position}
            onChange={(e) => setPosition(e.target.value)}
            disabled={disabled || busy}
          />
        </label>
      )}

      <input
        data-testid="enrol-capture-file"
        type="file"
        accept="image/*"
        disabled={disabled || busy}
        onChange={(e) => e.target.files?.[0] && onCaptureFile(e.target.files[0])}
        className="text-sm"
      />

      {extract.isPending && (
        <p className="text-xs flex items-center gap-1 text-muted-foreground">
          <Loader2 className="h-3.5 w-3.5 animate-spin" /> Reading capture…
        </p>
      )}

      {template && !result && (
        <p data-testid="enrol-extracted-ok" className="text-sm text-green-600">
          Template extracted{quality != null ? ` (quality ${quality})` : ""} — ready to enrol.
        </p>
      )}

      <button
        type="button"
        data-testid="enrol-btn"
        className="rounded-md bg-primary text-primary-foreground px-4 py-2 text-sm disabled:opacity-50"
        onClick={() => void doEnrol()}
        disabled={!subjectRef.trim() || !template || disabled || busy || !!result}
      >
        {enroll.isPending ? "Enrolling…" : "Register biometric"}
      </button>

      {result && !dup && (
        <p data-testid="enrol-clean" className="text-sm text-green-600 flex items-center gap-1.5">
          <ShieldCheck className="h-4 w-4" /> Enrolled under {result.subjectRef ?? subjectRef} (version{" "}
          {result.version ?? 1}) — no duplicates found.
        </p>
      )}

      {dup && (
        <div
          data-testid="enrol-duplicate-review"
          className="rounded-md border border-amber-400 bg-amber-50 dark:bg-amber-950/30 p-3 text-sm space-y-1"
        >
          <p className="text-amber-700 dark:text-amber-400 flex items-center gap-1 font-semibold">
            <AlertTriangle className="h-4 w-4" /> Possible duplicate — case opened for adjudication
          </p>
          <p>
            {dup.candidateCount} candidate{dup.candidateCount === 1 ? "" : "s"}
            {dup.topScore != null ? ` · top score ${(dup.topScore * 100).toFixed(1)}%` : ""}. The enrolment landed
            (care-first); a reviewer must adjudicate — merges are never automatic.
          </p>
          <Link
            data-testid="enrol-review-link"
            href={`/operations/vito/adjudication/${dup.caseId}`}
            className="text-primary underline"
          >
            Open case #{dup.caseId} in the adjudication console →
          </Link>
        </div>
      )}

      {error && (
        <p className="text-sm text-red-600" data-testid="enrol-error">
          {error}
        </p>
      )}

      <p className="text-[11px] text-muted-foreground">
        First-time enrolment for this patient. Capture is an image upload (no scanner in the browser). Quality and
        duplicate checks run server-side; a duplicate opens a review and is never auto-merged.
      </p>
    </div>
  );
}

function messageOf(e: unknown): string {
  const err = e as { status?: number; error?: { message?: string } } | undefined;
  if (err?.error?.message) {
    return err.status === 422 ? `Capture rejected: ${err.error.message}` : err.error.message;
  }
  return String((e as Error)?.message ?? e);
}

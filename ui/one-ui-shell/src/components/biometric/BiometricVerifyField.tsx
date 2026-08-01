"use client";

import { useId, useState } from "react";
import { Fingerprint, ImagePlus, ShieldCheck, Loader2, X } from "lucide-react";
import {
  useExtractTemplate,
  fileToBase64,
  type Modality,
} from "@/hooks/queries/useAbisBiometric";

/**
 * Reusable biometric-capture field for any action that can be biometrically
 * verified (check-in, dispense pickup, handover, pre-transfusion, high-value
 * release). It captures an image (a browser has no scanner, so capture = upload;
 * a device scanner is an EXTERNAL_INTEGRATION follow-on), extracts a probe
 * template via the real engine, and hands {modality, probeBase64} back to the
 * host form to send with its action.
 *
 * Care-first: this is OPTIONAL. When the operator captures nothing, the host
 * action proceeds on its existing factor — the field never blocks the workflow.
 * The server makes the MATCH/NO_MATCH/UNAVAILABLE decision; this only captures.
 */
export function BiometricVerifyField({
  label = "Verify by biometric (optional)",
  defaultModality = "FINGERPRINT",
  onProbe,
  disabled,
}: {
  label?: string;
  defaultModality?: Modality;
  /** Called with the extracted probe (or null when cleared). */
  onProbe: (probe: { modality: Modality; probeBase64: string } | null) => void;
  disabled?: boolean;
}) {
  const [modality, setModality] = useState<Modality>(defaultModality);
  const [captured, setCaptured] = useState(false);
  const [fileName, setFileName] = useState("");
  const [error, setError] = useState<string | null>(null);
  const inputId = useId();
  const extract = useExtractTemplate();

  async function onFile(file: File) {
    setError(null);
    setCaptured(false);
    setFileName("");
    onProbe(null);
    if (!file.type.startsWith("image/")) {
      setError("Choose an image captured from the selected biometric device.");
      return;
    }
    if (file.size > 10 * 1024 * 1024) {
      setError("The capture is larger than 10 MB. Choose a smaller image and try again.");
      return;
    }
    try {
      const sampleBase64 = await fileToBase64(file);
      const ex = await extract.mutateAsync({ modality, sampleBase64 });
      if (!ex.ok || !ex.templateBase64) {
        setError(`Could not read a ${modality.toLowerCase()} from that capture — proceed without, or retry.`);
        return;
      }
      setCaptured(true);
      setFileName(file.name);
      onProbe({ modality, probeBase64: ex.templateBase64 });
    } catch (e) {
      setError(String((e as Error).message ?? e));
    }
  }

  function clear() {
    setCaptured(false);
    setFileName("");
    setError(null);
    onProbe(null);
  }

  return (
    <div className="space-y-3 rounded-2xl border border-emerald-200 bg-emerald-50/45 p-4" data-testid="biometric-verify-field">
      <div className="flex items-center justify-between">
        <span className="text-sm font-medium flex items-center gap-1.5">
          <Fingerprint className="h-4 w-4" /> {label}
        </span>
        <select
          className="rounded-md border px-2 py-1 text-xs bg-background"
          value={modality}
          onChange={(e) => setModality(e.target.value as Modality)}
          disabled={disabled || extract.isPending}
          aria-label="Biometric modality"
        >
          <option value="FINGERPRINT">Fingerprint</option>
          <option value="FACE">Face</option>
          <option value="IRIS">Iris</option>
        </select>
      </div>

      {!captured ? (
        <div>
          <input
            id={inputId}
            data-testid="biometric-probe-file"
            type="file"
            accept="image/*"
            disabled={disabled || extract.isPending}
            onChange={(e) => e.target.files?.[0] && onFile(e.target.files[0])}
            className="sr-only"
          />
          <label
            htmlFor={inputId}
            aria-disabled={disabled || extract.isPending}
            className="inline-flex min-h-11 cursor-pointer items-center gap-2 rounded-xl border border-emerald-300 bg-white px-4 py-2.5 text-sm font-semibold text-emerald-900 shadow-sm hover:bg-emerald-50 aria-disabled:pointer-events-none aria-disabled:opacity-50"
          >
            <ImagePlus className="h-4 w-4" aria-hidden />
            Choose biometric capture
          </label>
          <p className="mt-2 text-xs text-muted-foreground">Image file, up to 10 MB. The image is converted to a protected probe before check-in.</p>
        </div>
      ) : (
        <p className="text-sm text-green-600 flex items-center gap-1.5" data-testid="biometric-captured">
          <ShieldCheck className="h-4 w-4" /> {modality.toLowerCase()} captured from {fileName} — verification will occur on submit
          <button type="button" onClick={clear} className="ml-1 text-muted-foreground hover:text-foreground"
            aria-label="Clear captured biometric">
            <X className="h-3.5 w-3.5" />
          </button>
        </p>
      )}

      {extract.isPending && (
        <p className="text-xs flex items-center gap-1 text-muted-foreground">
          <Loader2 className="h-3.5 w-3.5 animate-spin" /> Reading capture…
        </p>
      )}
      {error && <p className="text-xs text-red-600" data-testid="biometric-error">{error}</p>}
      <p className="text-[11px] text-muted-foreground">
        Optional. A match confirms identity; a non-match blocks the step; if biometrics are unavailable the
        action proceeds on the existing factor. Capture is image upload (no scanner in the browser).
      </p>
    </div>
  );
}

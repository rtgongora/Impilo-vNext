"use client";

import { useEffect, useState } from "react";
import { Fingerprint, ScanBarcode, Loader2, CheckCircle2 } from "lucide-react";
import { useBiometricProfile, useBiometricVerify } from "@/hooks/queries/useVitoBiometric";
import { usePreVerifyTransfusion } from "@/hooks/queries/useMadi";
import { BiometricVerifyField } from "@/components/biometric/BiometricVerifyField";
import type { Modality } from "@/hooks/queries/useAbisBiometric";

const PATIENT_METHODS = [
  { value: "BIOMETRIC", label: "Biometric (VITO)" },
  { value: "BARCODE_SCAN", label: "Patient wristband barcode" },
  { value: "MANUAL_GOVERNED", label: "Manual ID check (two staff)" },
  { value: "EMERGENCY_OVERRIDE", label: "Emergency override (documented)" },
] as const;

const UNIT_METHODS = [
  { value: "BARCODE_SCAN", label: "Unit bag barcode" },
  { value: "MANUAL_GOVERNED", label: "Manual unit label check" },
] as const;

export function MadiBedsideVerifyPanel({
  episodeId,
  defaultPatientCpid,
  verifiedBy,
  onVerified,
}: {
  episodeId: string;
  defaultPatientCpid?: string;
  verifiedBy?: string;
  onVerified?: () => void;
}) {
  const preVerify = usePreVerifyTransfusion(episodeId);
  const [patientCpid, setPatientCpid] = useState(defaultPatientCpid ?? "");

  useEffect(() => {
    if (defaultPatientCpid && !patientCpid) {
      setPatientCpid(defaultPatientCpid);
    }
  }, [defaultPatientCpid, patientCpid]);
  const [bloodUnitId, setBloodUnitId] = useState("");
  const [patientMethod, setPatientMethod] = useState<string>("BIOMETRIC");
  const [unitMethod, setUnitMethod] = useState<string>("BARCODE_SCAN");
  const [patientBiometricRef, setPatientBiometricRef] = useState("");
  const [unitScanRef, setUnitScanRef] = useState("");
  const [biometricOk, setBiometricOk] = useState(false);
  const [liveProbe, setLiveProbe] = useState<{ modality: Modality; probeBase64: string } | null>(null);
  const [error, setError] = useState<string | null>(null);

  const { data: bioProfile } = useBiometricProfile(patientCpid || undefined);
  const biometricVerify = useBiometricVerify();

  async function handleBiometricCapture() {
    if (!patientCpid.trim()) {
      setError("Enter patient CPID before biometric verification.");
      return;
    }
    const template = bioProfile?.data?.templates?.[0];
    if (!template) {
      setError("No enrolled biometric template for this patient. Use barcode or manual verification.");
      return;
    }
    setError(null);
    biometricVerify.mutate(
      { healthId: patientCpid.trim(), template },
      {
        onSuccess: (res) => {
          const payload =
            (res as { data?: { verified?: boolean; score?: number } })?.data
            ?? (res as { verified?: boolean; score?: number });
          const verified = payload?.verified;
          const score = payload?.score;
          if (verified) {
            const ref = `vito-verify:${patientCpid}:${score ?? "ok"}:${Date.now()}`;
            setPatientBiometricRef(ref);
            setBiometricOk(true);
          } else {
            setBiometricOk(false);
            setError("Biometric verification did not match. Try again or use another method.");
          }
        },
        onError: () => {
          setBiometricOk(false);
          setError("Biometric service unavailable. Use barcode or manual verification.");
        },
      },
    );
  }

  function handlePreVerify() {
    if (!patientCpid.trim() || !bloodUnitId.trim()) return;
    if (patientMethod === "BIOMETRIC" && !patientBiometricRef.trim() && !liveProbe) {
      setError("Capture a biometric (VITO match or a live probe) before recording verification, or choose another method.");
      return;
    }
    preVerify.mutate(
      {
        patient_cpid: patientCpid.trim(),
        blood_unit_id: bloodUnitId.trim(),
        patient_method: patientMethod,
        patient_biometric_ref: patientBiometricRef.trim() || undefined,
        unit_method: unitMethod,
        unit_scan_ref: unitScanRef.trim() || undefined,
        verified_by: verifiedBy,
        // Live ABIS probe: the server matches it against the patient's enrolled
        // template. MATCH → verification proceeds; NO_MATCH → the service rejects
        // with a 4xx (surfaced below); UNAVAILABLE → falls back on the chosen method.
        biometric_subject_ref: liveProbe ? patientCpid.trim() : undefined,
        biometric_modality: liveProbe?.modality,
        biometric_probe_base64: liveProbe?.probeBase64,
      },
      {
        onSuccess: () => {
          setError(null);
          onVerified?.();
        },
        onError: (err) => {
          const e = err as { status?: number; error?: { message?: string } };
          if (e?.status && e.status >= 400 && e.status < 500) {
            setError(
              e.error?.message
                ?? "Biometric did not match — bedside verification is blocked. Confirm the patient's identity another way.",
            );
          } else {
            setError("Bedside verification failed. Check patient, unit, and method.");
          }
        },
      },
    );
  }

  const patientVerified = preVerify.isSuccess || biometricOk;

  return (
    <section className="rounded-2xl border border-danger/28 bg-danger-soft/50 p-5 space-y-4">
      <div className="flex items-center gap-2 text-sm font-semibold text-danger">
        <Fingerprint className="h-4 w-4" />
        Bedside verification (patient + unit)
      </div>

      <div className="flex flex-wrap gap-2 text-xs">
        <span className={`inline-flex items-center gap-1 rounded-full px-2 py-1 ${patientVerified ? "bg-green-100 text-green-800" : "bg-neutral-100 text-muted-foreground"}`}>
          <CheckCircle2 className="h-3 w-3" /> Patient {patientVerified ? "verified" : "pending"}
        </span>
        <span className={`inline-flex items-center gap-1 rounded-full px-2 py-1 ${preVerify.isSuccess ? "bg-green-100 text-green-800" : "bg-neutral-100 text-muted-foreground"}`}>
          <ScanBarcode className="h-3 w-3" /> Unit {preVerify.isSuccess ? "verified" : "pending"}
        </span>
      </div>

      <input
        value={patientCpid}
        onChange={(e) => { setPatientCpid(e.target.value); setBiometricOk(false); }}
        placeholder="Patient CPID / Health ID"
        className="w-full rounded-xl border border-border px-3 py-2 text-sm font-mono"
      />
      <input
        value={bloodUnitId}
        onChange={(e) => setBloodUnitId(e.target.value)}
        placeholder="Blood unit UUID"
        className="w-full rounded-xl border border-border px-3 py-2 text-sm font-mono"
      />

      <select value={patientMethod} onChange={(e) => setPatientMethod(e.target.value)} className="w-full rounded-xl border border-border px-3 py-2 text-sm bg-card">
        {PATIENT_METHODS.map((m) => (
          <option key={m.value} value={m.value}>{m.label}</option>
        ))}
      </select>

      {patientMethod === "BIOMETRIC" && (
        <div className="flex flex-wrap items-center gap-2">
          <button
            type="button"
            onClick={handleBiometricCapture}
            disabled={biometricVerify.isPending || !patientCpid}
            className="rounded-lg bg-primary-hover px-3 py-1.5 text-xs text-white disabled:opacity-50"
          >
            {biometricVerify.isPending ? <Loader2 className="h-3 w-3 animate-spin inline" /> : null}
            Verify with VITO biometric
          </button>
          {bioProfile?.data?.status === "NOT_ENROLLED" && (
            <span className="text-xs text-warning-foreground">Patient not enrolled for biometrics</span>
          )}
          {biometricOk && <span className="text-xs text-green-700">Biometric match recorded</span>}
        </div>
      )}

      {patientMethod === "BIOMETRIC" && (
        <BiometricVerifyField
          label="Or capture a live probe (verified on submit)"
          onProbe={setLiveProbe}
          disabled={preVerify.isPending}
        />
      )}

      {(patientMethod === "EMERGENCY_OVERRIDE" || patientMethod === "BARCODE_SCAN") && (
        <input
          value={patientBiometricRef}
          onChange={(e) => setPatientBiometricRef(e.target.value)}
          placeholder={patientMethod === "BARCODE_SCAN" ? "Wristband scan value" : "Override documentation ref"}
          className="w-full rounded-xl border border-border px-3 py-2 text-sm"
        />
      )}

      <select value={unitMethod} onChange={(e) => setUnitMethod(e.target.value)} className="w-full rounded-xl border border-border px-3 py-2 text-sm bg-card">
        {UNIT_METHODS.map((m) => (
          <option key={m.value} value={m.value}>{m.label}</option>
        ))}
      </select>
      <input
        value={unitScanRef}
        onChange={(e) => setUnitScanRef(e.target.value)}
        placeholder="Unit barcode / scan reference"
        className="w-full rounded-xl border border-border px-3 py-2 text-sm"
      />

      {error && <p className="text-xs text-danger">{error}</p>}

      <button
        type="button"
        onClick={handlePreVerify}
        disabled={preVerify.isPending || !patientCpid || !bloodUnitId}
        className="rounded-xl bg-rose-600 px-4 py-2 text-sm font-medium text-white disabled:opacity-50"
      >
        {preVerify.isPending ? <Loader2 className="h-4 w-4 animate-spin inline" /> : null}
        Record bedside verification
      </button>
    </section>
  );
}

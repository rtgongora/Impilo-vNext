"use client";

import { useEffect, useState } from "react";
import { Fingerprint, ScanBarcode, Loader2, CheckCircle2 } from "lucide-react";
import { useBiometricProfile, useBiometricVerify } from "@/hooks/queries/useVitoBiometric";
import { usePreVerifyTransfusion } from "@/hooks/queries/useMadi";

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
    if (patientMethod === "BIOMETRIC" && !patientBiometricRef.trim()) {
      setError("Complete biometric capture before recording verification.");
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
      },
      {
        onSuccess: () => {
          setError(null);
          onVerified?.();
        },
        onError: () => setError("Bedside verification failed. Check patient, unit, and method."),
      },
    );
  }

  const patientVerified = preVerify.isSuccess || biometricOk;

  return (
    <section className="rounded-2xl border border-rose-200 bg-rose-50/50 p-5 space-y-4">
      <div className="flex items-center gap-2 text-sm font-semibold text-rose-900">
        <Fingerprint className="h-4 w-4" />
        Bedside verification (patient + unit)
      </div>

      <div className="flex flex-wrap gap-2 text-xs">
        <span className={`inline-flex items-center gap-1 rounded-full px-2 py-1 ${patientVerified ? "bg-green-100 text-green-800" : "bg-gray-100 text-gray-600"}`}>
          <CheckCircle2 className="h-3 w-3" /> Patient {patientVerified ? "verified" : "pending"}
        </span>
        <span className={`inline-flex items-center gap-1 rounded-full px-2 py-1 ${preVerify.isSuccess ? "bg-green-100 text-green-800" : "bg-gray-100 text-gray-600"}`}>
          <ScanBarcode className="h-3 w-3" /> Unit {preVerify.isSuccess ? "verified" : "pending"}
        </span>
      </div>

      <input
        value={patientCpid}
        onChange={(e) => { setPatientCpid(e.target.value); setBiometricOk(false); }}
        placeholder="Patient CPID / Health ID"
        className="w-full rounded-xl border border-gray-300 px-3 py-2 text-sm font-mono"
      />
      <input
        value={bloodUnitId}
        onChange={(e) => setBloodUnitId(e.target.value)}
        placeholder="Blood unit UUID"
        className="w-full rounded-xl border border-gray-300 px-3 py-2 text-sm font-mono"
      />

      <select value={patientMethod} onChange={(e) => setPatientMethod(e.target.value)} className="w-full rounded-xl border border-gray-300 px-3 py-2 text-sm bg-white">
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
            className="rounded-lg bg-gray-800 px-3 py-1.5 text-xs text-white disabled:opacity-50"
          >
            {biometricVerify.isPending ? <Loader2 className="h-3 w-3 animate-spin inline" /> : null}
            Verify with VITO biometric
          </button>
          {bioProfile?.data?.status === "NOT_ENROLLED" && (
            <span className="text-xs text-amber-700">Patient not enrolled for biometrics</span>
          )}
          {biometricOk && <span className="text-xs text-green-700">Biometric match recorded</span>}
        </div>
      )}

      {(patientMethod === "EMERGENCY_OVERRIDE" || patientMethod === "BARCODE_SCAN") && (
        <input
          value={patientBiometricRef}
          onChange={(e) => setPatientBiometricRef(e.target.value)}
          placeholder={patientMethod === "BARCODE_SCAN" ? "Wristband scan value" : "Override documentation ref"}
          className="w-full rounded-xl border border-gray-300 px-3 py-2 text-sm"
        />
      )}

      <select value={unitMethod} onChange={(e) => setUnitMethod(e.target.value)} className="w-full rounded-xl border border-gray-300 px-3 py-2 text-sm bg-white">
        {UNIT_METHODS.map((m) => (
          <option key={m.value} value={m.value}>{m.label}</option>
        ))}
      </select>
      <input
        value={unitScanRef}
        onChange={(e) => setUnitScanRef(e.target.value)}
        placeholder="Unit barcode / scan reference"
        className="w-full rounded-xl border border-gray-300 px-3 py-2 text-sm"
      />

      {error && <p className="text-xs text-red-700">{error}</p>}

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

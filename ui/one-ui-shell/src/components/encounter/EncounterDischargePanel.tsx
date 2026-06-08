"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { CheckCircle2, Loader2, XCircle } from "lucide-react";
import { useDischargeEncounter } from "@/hooks/queries/useDischarge";
import { useAuthStore } from "@/hooks/useAuthStore";

interface EncounterDischargePanelProps {
  patientId: string;
  encounterId: string;
  disabled?: boolean;
}

export function EncounterDischargePanel({
  patientId,
  encounterId,
  disabled = false,
}: EncounterDischargePanelProps) {
  const router = useRouter();
  const { user } = useAuthStore();
  const dischargeMutation = useDischargeEncounter();

  const [dischargeType, setDischargeType] = useState("DISCHARGE");
  const [diagnosis, setDiagnosis] = useState("");
  const [followUp, setFollowUp] = useState("");
  const [instructions, setInstructions] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault();
    if (disabled) return;

    setError(null);
    setSuccess(null);

    if (!diagnosis.trim()) {
      setError("Discharge diagnosis is required.");
      return;
    }

    try {
      const response = await dischargeMutation.mutateAsync({
        encounterId,
        discharge_type: dischargeType,
        discharge_diagnosis: diagnosis.trim(),
        follow_up_instructions: followUp.trim() || undefined,
        patient_instructions: instructions.trim() || undefined,
        discharged_by: user?.id ?? "current-user",
      });
      setSuccess("Discharge instructions captured and journey discharge started.");
      if (dischargeType === "ADMIT") {
        const transactionId = (response.meta as { core_transaction_id?: string } | undefined)?.core_transaction_id;
        const params = new URLSearchParams({
          encounterId,
          source: "outpatient-discharge",
        });
        if (transactionId) {
          params.set("transaction_id", transactionId);
        }
        router.push(`/clinical/inpatient/admissions?patientId=${encodeURIComponent(patientId)}&${params.toString()}`);
      }
    } catch {
      setError("Discharge failed — verify encounter is active and BFF/PCT is available.");
    }
  }

  return (
    <section
      className="rounded-xl border border-emerald-200 bg-white p-4 shadow-sm"
      data-testid="encounter-discharge-panel"
    >
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div className="flex items-center gap-2">
          <CheckCircle2 className="h-4 w-4 text-emerald-600" />
          <h3 className="text-sm font-semibold text-slate-900">Discharge instructions</h3>
        </div>
        <Link
          href={`/ehr/${patientId}/discharge?encounterId=${encodeURIComponent(encounterId)}`}
          className="text-xs font-medium text-impilo-600 hover:underline"
        >
          Full visit outcome
        </Link>
      </div>

      <p className="mt-1 text-xs text-slate-600">
        Inline orchestration posts to{" "}
        <code className="rounded bg-slate-100 px-1">POST /internal/v1/encounters/{encounterId}/discharge</code> with
        instruction fields forwarded to PCT.
      </p>

      {!disabled ? (
        <form onSubmit={(e) => void handleSubmit(e)} className="mt-4 space-y-3">
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600" htmlFor="encounter-discharge-type">
              Outcome type
            </label>
            <select
              id="encounter-discharge-type"
              value={dischargeType}
              onChange={(e) => setDischargeType(e.target.value)}
              className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm"
            >
              <option value="DISCHARGE">Discharge home</option>
              <option value="ADMIT">Admit inpatient</option>
              <option value="TRANSFER">Transfer</option>
              <option value="REFER">Refer out</option>
              <option value="LAMA">Leave against medical advice</option>
            </select>
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600" htmlFor="encounter-discharge-diagnosis">
              Discharge diagnosis
            </label>
            <input
              id="encounter-discharge-diagnosis"
              value={diagnosis}
              onChange={(e) => setDiagnosis(e.target.value)}
              className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm"
              placeholder="Primary diagnosis at closure"
            />
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600" htmlFor="encounter-discharge-followup">
              Follow-up instructions
            </label>
            <textarea
              id="encounter-discharge-followup"
              value={followUp}
              onChange={(e) => setFollowUp(e.target.value)}
              rows={2}
              className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm"
              placeholder="Clinic review, labs, referrals…"
            />
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600" htmlFor="encounter-discharge-instructions">
              Patient instructions
            </label>
            <textarea
              id="encounter-discharge-instructions"
              value={instructions}
              onChange={(e) => setInstructions(e.target.value)}
              rows={2}
              className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm"
              placeholder="Medication, activity, warning signs…"
            />
          </div>

          {error ? (
            <p className="flex items-center gap-1 text-xs text-red-600">
              <XCircle className="h-3.5 w-3.5" />
              {error}
            </p>
          ) : null}
          {success ? <p className="text-xs text-emerald-700">{success}</p> : null}

          <button
            type="submit"
            disabled={dischargeMutation.isPending}
            className="inline-flex items-center gap-1.5 rounded-lg bg-emerald-600 px-3 py-2 text-xs font-medium text-white hover:bg-emerald-700 disabled:opacity-50"
          >
            {dischargeMutation.isPending ? (
              <Loader2 className="h-3.5 w-3.5 animate-spin" />
            ) : (
              <CheckCircle2 className="h-3.5 w-3.5" />
            )}
            Capture discharge & start closure
          </button>
        </form>
      ) : (
        <p className="mt-3 text-xs text-slate-500">Encounter is closed — discharge panel read-only.</p>
      )}
    </section>
  );
}
